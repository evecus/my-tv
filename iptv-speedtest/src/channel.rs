use crate::config::LOGO_BASE_URL;
use once_cell::sync::Lazy;
use regex::Regex;
use std::collections::HashMap;
use std::fs;

// ── 速度分级（保留供兼容，但输出不再使用分级分组）────────────────

pub fn speed_tier(_speed: f64) -> &'static str {
    ""
}

pub fn tier_order(_tier: &str) -> i32 {
    0
}

// ── 分组 ──────────────────────────────────────────────────────────

pub fn base_group(name: &str) -> &'static str {
    let upper = name.to_uppercase();
    if upper.contains("CCTV") {
        "央视频道"
    } else if name.contains("卫视") {
        "卫视频道"
    } else {
        "其他频道"
    }
}

pub fn full_group(name: &str, _speed: f64) -> String {
    base_group(name).to_string()
}

pub fn build_logo_url(name: &str) -> String {
    let encoded = url::form_urlencoded::byte_serialize(name.as_bytes())
        .collect::<String>()
        .replace('+', "%20");
    format!("{}{}.png", LOGO_BASE_URL, encoded)
}

pub fn build_m3u8_entry(name: &str, stream_url: &str, speed: f64) -> String {
    let grp = full_group(name, speed);
    format!(
        "#EXTINF:-1 tvg-name=\"{}\" tvg-logo=\"{}\" group-title=\"{}\",{}\n{}",
        name,
        build_logo_url(name),
        grp,
        name,
        stream_url
    )
}

// ── 频道名清洗 ────────────────────────────────────────────────────

// 匹配 CCTV + 1~2位数字（含可选的+号），数字后紧跟的多余字符会被忽略
static RE_CCTV_EXTRACT: Lazy<Regex> =
    Lazy::new(|| Regex::new(r"(?i)CCTV(\d{1,2})(\+)?").unwrap());

// 匹配 XX卫视，XX必须是汉字（至少1个），前后内容全部丢弃
static RE_WEIXI_EXTRACT: Lazy<Regex> =
    Lazy::new(|| Regex::new(r"([\u4e00-\u9fff]+卫视)").unwrap());

static RE_CCTV_NUM: Lazy<Regex> = Lazy::new(|| Regex::new(r"CCTV(\d+)台").unwrap());

pub fn clean_channel_name(name: &str) -> String {
    let mut s = name.to_string();
    s = s.replace("cctv", "CCTV");
    s = s.replace("中央", "CCTV");
    s = s.replace("央视", "CCTV");
    for rep in &["高清", "超高", "HD", "标清", "频道", "-", " ", "(", ")"] {
        s = s.replace(rep, "");
    }
    s = s.replace("PLUS", "+");
    s = s.replace('＋', "+");
    s = RE_CCTV_NUM
        .replace_all(&s, |caps: &regex::Captures| format!("CCTV{}", &caps[1]))
        .into_owned();

    // 若包含 CCTV+数字：提取数字，校验范围 1~17（含5+），范围外归其他频道
    if let Some(caps) = RE_CCTV_EXTRACT.captures(&s) {
        let num: u32 = caps[1].parse().unwrap_or(0);
        let is_plus = caps.get(2).is_some();
        if is_plus && num == 5 {
            return "CCTV5+".to_string();
        }
        if num >= 1 && num <= 17 {
            return format!("CCTV{}", num);
        }
        // 数字超出范围（CCTV18等）→ 去掉CCTV前缀，归其他频道
        return s.replace("CCTV", "");
    }

    // 若包含 XX卫视（XX为汉字），提取为标准名，前后数字/符号全部丢弃
    if let Some(caps) = RE_WEIXI_EXTRACT.captures(&s) {
        return caps[1].to_string();
    }

    // 若还含有 CCTV 但没有合法编号（如CCTV气象），去掉CCTV归其他频道
    if s.to_uppercase().contains("CCTV") {
        return s.replace("CCTV", "").replace("cctv", "");
    }

    s
}

// ── 标准名映射 ────────────────────────────────────────────────────

pub fn get_standard_channel_map() -> HashMap<String, String> {
    let mut m = HashMap::new();
    let Ok(data) = fs::read_to_string(crate::config::CHANNEL_LIST_FILE) else {
        return m;
    };
    for line in data.lines() {
        let std = line.trim();
        if std.is_empty() {
            continue;
        }
        m.insert(normal_key(std), std.to_string());
    }
    m
}

pub fn normal_key(s: &str) -> String {
    s.to_uppercase().replace('-', "").replace(' ', "")
}

pub fn map_to_standard_name<'a>(name: &'a str, m: &'a HashMap<String, String>) -> &'a str {
    m.get(&normal_key(name)).map(|s| s.as_str()).unwrap_or(name)
}

// ── 卫视排序 ──────────────────────────────────────────────────────

static WEIXI_ORDER: &[&str] = &[
    "湖南卫视",
    "东方卫视",
    "浙江卫视",
    "江苏卫视",
    "北京卫视",
    "山东卫视",
    "河南卫视",
    "广东卫视",
    "安徽卫视",
    "深圳卫视",
    "天津卫视",
    "江西卫视",
    "四川卫视",
    "湖北卫视",
    "重庆卫视",
    "黑龙江卫视",
    "辽宁卫视",
    "河北卫视",
    "吉林卫视",
    "山西卫视",
    "广西卫视",
    "云南卫视",
    "福建东南卫视",
    "贵州卫视",
    "陕西卫视",
    "甘肃卫视",
    "内蒙古卫视",
    "新疆卫视",
    "宁夏卫视",
    "青海卫视",
    "西藏卫视",
    "海南卫视",
    "兵团卫视",
];

pub fn weixi_sort_index(name: &str) -> Option<usize> {
    WEIXI_ORDER.iter().position(|&kw| name.contains(kw))
}

// 返回 (category, sub_order, name) 用于排序
pub fn channel_sort_key(name: &str) -> (i32, f64, String) {
    let upper = name.to_uppercase();
    if upper.contains("CCTV") {
        static RE: Lazy<Regex> = Lazy::new(|| Regex::new(r"CCTV(\d+)").unwrap());
        if let Some(caps) = RE.captures(&upper) {
            let num: f64 = caps[1].parse().unwrap_or(999.0);
            return (0, num, String::new());
        }
        if upper.contains("5+") {
            return (0, 5.5, String::new());
        }
        return (0, 999.0, String::new());
    }
    if name.contains("卫视") {
        if let Some(idx) = weixi_sort_index(name) {
            return (1, idx as f64, name.to_string());
        }
        return (1, WEIXI_ORDER.len() as f64, name.to_string());
    }
    (2, 0.0, name.to_string())
}
