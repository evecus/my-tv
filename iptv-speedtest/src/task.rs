use crate::channel::{
    build_m3u8_entry, clean_channel_name, get_standard_channel_map, map_to_standard_name,
};
use crate::config::{API_URL, HSMD_ADDRESS_LIST_FILE, SPEED_LOW};
use crate::output::build_and_write;
use crate::speedtest::{fetch_channels_for_source, run_api_speed_tests, test_subscribe_hosts};
use crate::subscribe::{download_subscribes, host_key, parse_subscribe_file};
use crate::types::{Entry, SourceResult};
use once_cell::sync::Lazy;
use regex::Regex;
use reqwest::Client;
use serde_json::Value;
use std::collections::{HashMap, HashSet};
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;
use url::Url;

static IS_RUNNING: Lazy<AtomicBool> = Lazy::new(|| AtomicBool::new(false));

pub fn is_running() -> bool {
    IS_RUNNING.load(Ordering::Relaxed)
}

// ── 内部辅助 ──────────────────────────────────────────────────────

async fn fetch_api_data() -> Vec<serde_json::Map<String, Value>> {
    let client = Client::builder()
        .timeout(Duration::from_secs(10))
        .build()
        .unwrap();
    for attempt in 1..=3 {
        eprintln!("[api] fetch attempt {}: {}", attempt, API_URL);
        if let Ok(resp) = client.get(API_URL).send().await {
            if resp.status() == 200 {
                if let Ok(data) = resp.json::<Value>().await {
                    if let Some(results) = data["results"].as_array() {
                        let out: Vec<_> = results
                            .iter()
                            .filter_map(|r| r.as_object().cloned())
                            .collect();
                        eprintln!("[api] received {} hosts", out.len());
                        return out;
                    }
                }
            }
        }
        tokio::time::sleep(Duration::from_secs(5)).await;
    }
    eprintln!("[api] fetch failed after 3 retries");
    vec![]
}

fn select_top_sources(mut results: Vec<SourceResult>, top_n: usize) -> Vec<SourceResult> {
    results.sort_by(|a, b| {
        b.speed
            .partial_cmp(&a.speed)
            .unwrap_or(std::cmp::Ordering::Equal)
    });
    let mut selected_hosts = HashSet::new();
    let mut final_results: Vec<SourceResult> = vec![];

    for mt in &["txiptv", "hsmdtv", "zhgxtv", "jsmpeg"] {
        if let Some(r) = results
            .iter()
            .find(|r| r.match_type == *mt && !selected_hosts.contains(&r.host))
        {
            selected_hosts.insert(r.host.clone());
            final_results.push(r.clone());
        }
    }
    for r in &results {
        if final_results.len() >= top_n {
            break;
        }
        if !selected_hosts.contains(&r.host) {
            selected_hosts.insert(r.host.clone());
            final_results.push(r.clone());
        }
    }
    final_results.sort_by(|a, b| {
        b.speed
            .partial_cmp(&a.speed)
            .unwrap_or(std::cmp::Ordering::Equal)
    });
    final_results
}

fn build_entries(
    channels: &[crate::types::Channel],
    idx: usize,
    speed: f64,
    std_map: &HashMap<String, String>,
) -> Vec<Entry> {
    channels
        .iter()
        .map(|ch| {
            let name = map_to_standard_name(&clean_channel_name(&ch.name), std_map).to_string();
            Entry {
                content: build_m3u8_entry(&name, &ch.url, speed),
                name,
                url: ch.url.clone(),
                index: idx,
                speed,
            }
        })
        .collect()
}

static RE_URL: Lazy<Regex> = Lazy::new(|| Regex::new(r"(http://[^\s]+)").unwrap());
static RE_ID: Lazy<Regex> = Lazy::new(|| Regex::new(r"^\s*\d+\s+").unwrap());

fn process_hsmdtv_channels(
    host: &str,
    source_index: usize,
    speed: f64,
    std_map: &HashMap<String, String>,
) -> Vec<Entry> {
    let Ok(data) = std::fs::read_to_string(HSMD_ADDRESS_LIST_FILE) else {
        eprintln!("[hsmd] {} not found, skipping", HSMD_ADDRESS_LIST_FILE);
        return vec![];
    };
    let mut entries = vec![];
    for line in data.lines() {
        let line = line.trim();
        if line.is_empty() {
            continue;
        }
        let Some(loc) = RE_URL.find(line) else {
            continue;
        };
        let url_in_file = loc.as_str();
        let before = &line[..loc.start()];
        let name_raw = RE_ID
            .replace(before, "")
            .replace("（默认频道）", "")
            .trim()
            .to_string();
        let name = map_to_standard_name(&clean_channel_name(&name_raw), std_map).to_string();
        let Ok(p) = Url::parse(url_in_file) else {
            continue;
        };
        let new_url = format!("http://{}{}", host, p.path());
        entries.push(Entry {
            content: build_m3u8_entry(&name, &new_url, speed),
            name,
            url: new_url,
            index: source_index,
            speed,
        });
    }
    entries
}

// ── 主入口 ────────────────────────────────────────────────────────

/// 测速 → 整理去重排序 → 直接写 m3u8 到 output_path。
/// 返回写入的频道名数量；0 表示无可用频道（调用方应 exit(1)）。
pub async fn run_task_android(
    workers: usize,
    top_n: usize,
    urls: Vec<String>,
    output_path: &std::path::Path,
) -> usize {
    if IS_RUNNING
        .compare_exchange(false, true, Ordering::Acquire, Ordering::Relaxed)
        .is_err()
    {
        eprintln!("[android] already running, skipping");
        return 0;
    }

    let start = std::time::Instant::now();
    eprintln!("[android] ── speedtest start ──");

    let std_map = get_standard_channel_map();
    let mut all_entries: Vec<Entry> = vec![];
    let mut source_idx = 0usize;

    // Step 1 & 2 并行：下载订阅文件 + 获取 API 网关列表
    eprintln!("[android] fetching api list & subscribe files in parallel...");
    let (api_items, sub_cache) = tokio::join!(
        fetch_api_data(),
        download_subscribes(&urls),
    );

    // Step 3: 并发测速 API 网关
    if !api_items.is_empty() {
        eprintln!("[android] speed-testing {} api hosts...", api_items.len());
        let raw_results = run_api_speed_tests(api_items, workers).await;
        let mut top_sources = select_top_sources(raw_results, top_n);
        eprintln!("[android] selected {} api sources", top_sources.len());

        for src in top_sources.iter_mut() {
            fetch_channels_for_source(src).await;
            let entries = match src.match_type.as_str() {
                "txiptv" | "zhgxtv" | "jsmpeg" => {
                    build_entries(&src.channels, source_idx, src.speed, &std_map)
                }
                "hsmdtv" => process_hsmdtv_channels(&src.host, source_idx, src.speed, &std_map),
                _ => vec![],
            };
            all_entries.extend(entries);
            source_idx += 1;
        }
    }

    // Step 4: 测速订阅源
    for (raw_url, cache_path) in &sub_cache {
        let channels = parse_subscribe_file(cache_path);
        if channels.is_empty() {
            eprintln!("[android] no channels from {}", raw_url);
            continue;
        }
        eprintln!(
            "[android] {} channels from {} — testing hosts...",
            channels.len(),
            raw_url
        );
        let host_speeds = test_subscribe_hosts(&channels, workers).await;

        let mut added = 0usize;
        for ch in &channels {
            let hk = host_key(&ch.url);
            let spd = match host_speeds.get(&hk) {
                Some(&s) if s >= SPEED_LOW => s,
                _ => continue,
            };
            let name = map_to_standard_name(
                &clean_channel_name(&ch.name),
                &std_map,
            )
            .to_string();
            all_entries.push(Entry {
                content: build_m3u8_entry(&name, &ch.url, spd),
                name,
                url: ch.url.clone(),
                index: source_idx,
                speed: spd,
            });
            added += 1;
        }
        eprintln!("[android] kept {} / {} channels", added, channels.len());
        source_idx += 1;
    }

    eprintln!(
        "[android] collected {} raw entries in {}s, building m3u8...",
        all_entries.len(),
        start.elapsed().as_secs()
    );

    if all_entries.is_empty() {
        eprintln!("[android] no entries, abort");
        IS_RUNNING.store(false, Ordering::Release);
        return 0;
    }

    // Step 5: 整理 → 去重 → 排序 → 写 m3u8
    let update_time = chrono::Local::now();
    let (m3u8, _txt) = build_and_write(all_entries, update_time);

    if let Err(e) = std::fs::write(output_path, &m3u8) {
        eprintln!("[android] failed to write output: {}", e);
        IS_RUNNING.store(false, Ordering::Release);
        return 0;
    }

    // 统计去重后的频道名数
    let channel_count = {
        let mut names = HashSet::new();
        for line in m3u8.lines() {
            if let Some(rest) = line.strip_prefix("#EXTINF") {
                if let Some(idx) = rest.rfind(',') {
                    let name = rest[idx + 1..].trim().to_string();
                    if !name.is_empty() {
                        names.insert(name);
                    }
                }
            }
        }
        names.len()
    };

    IS_RUNNING.store(false, Ordering::Release);
    eprintln!(
        "[android] done — {} channels → {} ({}s total)",
        channel_count,
        output_path.display(),
        start.elapsed().as_secs()
    );
    channel_count
}
