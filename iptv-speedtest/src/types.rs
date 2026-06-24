/// 单个 IPTV 频道（名称 + 地址）
#[derive(Debug, Clone)]
pub struct Channel {
    pub name: String,
    pub url: String,
}

/// 一个经过测速的 API 源
#[derive(Debug, Clone)]
pub struct SourceResult {
    pub host: String,
    pub match_type: String,
    pub source: String,
    pub speed: f64,
    pub channels: Vec<Channel>,
}

/// 可输出的完整播放列表条目
#[derive(Debug, Clone)]
pub struct Entry {
    pub name: String,
    pub url: String,
    pub content: String, // 完整的 #EXTINF + URL 块
    pub index: usize,    // 越小优先级越高
    pub speed: f64,      // MB/s
}
