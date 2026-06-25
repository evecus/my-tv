use crate::channel::{base_group, channel_sort_key};
use crate::config::EPG_URL;
use crate::types::Entry;
use std::collections::HashMap;

static GROUPS: &[&str] = &["央视频道", "卫视频道", "其他频道"];

/// 聚合所有条目、去重、排序，返回 m3u8 字符串。
pub fn build_and_write(
    all_entries: Vec<Entry>,
    update_time: chrono::DateTime<chrono::Local>,
) -> (String, String) {
    // 按频道名分组
    let mut by_name: HashMap<String, Vec<Entry>> = HashMap::new();
    for e in all_entries {
        by_name.entry(e.name.clone()).or_default().push(e);
    }

    // 频道名排序
    let mut all_names: Vec<String> = by_name.keys().cloned().collect();
    all_names.sort_by(|a, b| {
        let (a0, a1, a2) = channel_sort_key(a);
        let (b0, b1, b2) = channel_sort_key(b);
        a0.cmp(&b0)
            .then(a1.partial_cmp(&b1).unwrap_or(std::cmp::Ordering::Equal))
            .then(a2.cmp(&b2))
    });

    // 每个频道去重并按速度降序排序
    for entries in by_name.values_mut() {
        let mut seen = std::collections::HashSet::new();
        entries.retain(|e| seen.insert(e.url.clone()));
        entries.sort_by(|a, b| {
            b.speed
                .partial_cmp(&a.speed)
                .unwrap_or(std::cmp::Ordering::Equal)
                .then(a.index.cmp(&b.index))
        });
    }

    let ts = update_time.format("%Y-%m-%d %H:%M:%S").to_string();

    // 构建 m3u8
    let mut lines: Vec<String> = vec![
        format!("#EXTM3U x-tvg-url=\"{}\"", EPG_URL),
        format!("#EXT-X-UPDATED: {}", ts),
    ];
    for grp in GROUPS {
        for name in &all_names {
            if base_group(name) != *grp {
                continue;
            }
            if let Some(entries) = by_name.get(name) {
                for e in entries {
                    lines.push(e.content.clone());
                }
            }
        }
    }
    // 末尾写入更新时间占位条目（播放器可忽略）
    lines.push(format!(
        "#EXTINF:-1 group-title=\"更新时间\",更新时间: {}\nhttp://127.0.0.1/",
        ts
    ));

    let m3u8 = lines.join("\n");
    eprintln!(
        "[output] m3u8 {} bytes  channels {}",
        m3u8.len(),
        all_names.len()
    );

    // 第二个返回值保留签名兼容性，为空字符串
    (m3u8, String::new())
}
