mod channel;
mod config;
mod output;
mod speedtest;
mod subscribe;
mod task;
mod types;

use crate::config::{init_data_dir, DEFAULT_SUB_URL, VERSION};
use clap::Parser;
use std::path::PathBuf;

#[derive(Parser, Debug)]
#[command(version = VERSION, about = "IPTV Speed Tester — Android CLI")]
struct Args {
    /// 并发测速数（默认 60）
    #[arg(long, default_value_t = 60)]
    workers: usize,

    /// 每种类型保留前 N 个源（默认 10）
    #[arg(long, default_value_t = 10)]
    top: usize,

    /// 额外订阅 URL（可多次指定）
    #[arg(long = "url")]
    urls: Vec<String>,

    /// m3u8 结果写入路径（必填）
    #[arg(long)]
    output: PathBuf,
}

#[tokio::main]
async fn main() {
    let args = Args::parse();

    // 用 --output 的父目录作为数据目录（Android 沙箱权限安全）
    let data_dir = args
        .output
        .parent()
        .filter(|p| !p.as_os_str().is_empty())
        .map(|p| p.to_path_buf())
        .unwrap_or_else(std::env::temp_dir);

    std::fs::create_dir_all(&data_dir).ok();
    init_data_dir(Some(&data_dir));

    let mut urls = args.urls.clone();
    urls.push(DEFAULT_SUB_URL.to_string());

    eprintln!(
        "[android] workers={} top={} urls={} output={}",
        args.workers,
        args.top,
        urls.len(),
        args.output.display()
    );

    let count = task::run_task_android(args.workers, args.top, urls, &args.output).await;

    if count == 0 {
        eprintln!("[android] no channels found");
        std::process::exit(1);
    }
    eprintln!("[android] finished, {} channels", count);
}
