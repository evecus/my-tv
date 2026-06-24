use crate::config::SUB_TIMEOUT;
use crate::types::Channel;
use reqwest::Client;
use std::collections::HashMap;
use std::fs;
use url::Url;

// ── 下载 ──────────────────────────────────────────────────────────

/// 并发下载所有订阅 URL，缓存到本地文件。返回 map[url → 本地路径]
pub async fn download_subscribes(urls: &[String]) -> HashMap<String, String> {
    let mut handles = vec![];
    for (i, raw_url) in urls.iter().enumerate() {
        let raw_url = raw_url.trim().to_string();
        if raw_url.is_empty() {
            continue;
        }
        let i = i;
        let handle = tokio::spawn(async move {
            let cache_path = format!("sub_cache_{}.txt", i);
            match fetch_raw(&raw_url).await {
                Ok(body) => {
                    if let Err(e) = fs::write(&cache_path, &body) {
                        println!("[subscribe] cache write {}: {}", raw_url, e);
                        return None;
                    }
                    println!("[subscribe] downloaded ({} bytes): {}", body.len(), raw_url);
                    Some((raw_url, cache_path))
                }
                Err(e) => {
                    println!("[subscribe] skip {}: {}", raw_url, e);
                    None
                }
            }
        });
        handles.push(handle);
    }

    let mut result = HashMap::new();
    for h in handles {
        if let Ok(Some((url, path))) = h.await {
            result.insert(url, path);
        }
    }
    result
}

async fn fetch_raw(raw_url: &str) -> anyhow::Result<Vec<u8>> {
    let client = Client::builder().timeout(SUB_TIMEOUT).build()?;
    let resp = client.get(raw_url).send().await?;
    if resp.status() != 200 {
        anyhow::bail!("HTTP {}", resp.status());
    }
    Ok(resp.bytes().await?.to_vec())
}

// ── 解析 ──────────────────────────────────────────────────────────

pub fn parse_subscribe_file(path: &str) -> Vec<Channel> {
    let Ok(data) = fs::read_to_string(path) else {
        return vec![];
    };
    let content = data.trim_start();
    if content.starts_with("#EXTM3U") {
        parse_m3u(content)
    } else {
        parse_txt_channels(content)
    }
}

fn parse_m3u(content: &str) -> Vec<Channel> {
    let mut channels = vec![];
    let mut pending = String::new();
    for line in content.lines() {
        let line = line.trim();
        if line.starts_with("#EXTINF") {
            if let Some(idx) = line.rfind(',') {
                pending = line[idx + 1..].trim().to_string();
            }
        } else if !line.is_empty() && !line.starts_with('#') && !pending.is_empty() {
            channels.push(Channel {
                name: pending.clone(),
                url: line.to_string(),
            });
            pending.clear();
        }
    }
    channels
}

fn parse_txt_channels(content: &str) -> Vec<Channel> {
    let mut channels = vec![];
    for line in content.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let mut parts = line.splitn(2, ',');
        if let (Some(name), Some(url)) = (parts.next(), parts.next()) {
            let name = name.trim().to_string();
            let url = url.trim().to_string();
            if !name.is_empty() && !url.is_empty() && !url.contains("#genre#") {
                channels.push(Channel { name, url });
            }
        }
    }
    channels
}

// ── 主机 key ─────────────────────────────────────────────────────

pub fn host_key(raw_url: &str) -> String {
    if let Ok(u) = Url::parse(raw_url) {
        if !u.host_str().unwrap_or("").is_empty() {
            let scheme = if u.scheme().is_empty() {
                "http"
            } else {
                u.scheme()
            };
            return format!("{}://{}", scheme, u.host_str().unwrap_or(""));
        }
    }
    raw_url.to_string()
}
