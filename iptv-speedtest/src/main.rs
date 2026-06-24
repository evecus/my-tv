mod channel;
mod config;
mod output;
mod speedtest;
mod subscribe;
mod task;
mod types;

// server 模块仅在非 android feature 下编译
#[cfg(not(feature = "android"))]
mod server;

use crate::config::{init_data_dir, DEFAULT_SUB_URL, VERSION};
use clap::{Parser, Subcommand};
use std::path::PathBuf;

// ── 共享状态（server 模式专用）────────────────────────────────────

#[cfg(not(feature = "android"))]
use tokio::sync::RwLock;

#[cfg(not(feature = "android"))]
#[derive(Default)]
pub struct SharedData {
    pub m3u8: String,
    pub txt: String,
    pub last_run: String,
}

#[cfg(not(feature = "android"))]
pub struct AppState {
    pub data: RwLock<SharedData>,
    pub workers: usize,
    pub top_n: usize,
    pub urls: Vec<String>,
}

// ── CLI 定义 ──────────────────────────────────────────────────────

#[derive(Parser, Debug)]
#[command(version = VERSION, about = "IPTV Speed Tester")]
struct Cli {
    #[command(subcommand)]
    command: Commands,
}

#[derive(Subcommand, Debug)]
enum Commands {
    /// 启动 HTTP 服务器模式（原有功能）
    #[cfg(not(feature = "android"))]
    Serve(ServeArgs),

    /// Android CLI 模式：测速后输出 JSON 到 stdout
    Android(AndroidArgs),
}

// ── Server 模式参数（保持原有完整功能）───────────────────────────

#[cfg(not(feature = "android"))]
#[derive(clap::Args, Debug)]
struct ServeArgs {
    #[arg(long, env = "PORT", default_value_t = 3030)]
    port: u16,
    #[arg(long, env = "WORKERS", default_value_t = 20)]
    workers: usize,
    #[arg(long = "top", env = "TOP", default_value_t = 5)]
    top_n: usize,
    #[arg(long, env = "CRON", default_value = "23 3 * * *")]
    cron: String,
    #[arg(long, env = "TZ", default_value = "Asia/Shanghai")]
    timezone: String,
    #[arg(long, env = "DATA_DIR")]
    dir: Option<PathBuf>,
    #[arg(long = "url1", env = "URL1")] url1: Option<String>,
    #[arg(long = "url2", env = "URL2")] url2: Option<String>,
    #[arg(long = "url3", env = "URL3")] url3: Option<String>,
    #[arg(long = "url4", env = "URL4")] url4: Option<String>,
    #[arg(long = "url5", env = "URL5")] url5: Option<String>,
}

// ── Android 模式参数 ──────────────────────────────────────────────

#[derive(clap::Args, Debug)]
struct AndroidArgs {
    /// 并发测速数（默认 60）
    #[arg(long, default_value_t = 60)]
    workers: usize,

    /// 每种类型保留前 N 个源（默认 10）
    #[arg(long, default_value_t = 10)]
    top: usize,

    /// 额外订阅 URL（可多次指定）
    #[arg(long = "url")]
    urls: Vec<String>,

    /// 结果写入文件路径（不指定则输出到 stdout）
    #[arg(long)]
    output: Option<PathBuf>,
}

// ── 程序入口 ──────────────────────────────────────────────────────

#[tokio::main]
async fn main() {
    let cli = Cli::parse();

    match cli.command {
        #[cfg(not(feature = "android"))]
        Commands::Serve(args) => run_server(args).await,
        Commands::Android(args) => run_android(args).await,
    }
}

// ── Android CLI 模式 ──────────────────────────────────────────────

async fn run_android(args: AndroidArgs) {
    // 优先用 --output 文件的父目录作为数据目录。
    // 这样在 Android 沙箱里始终落在 app 有写权限的 cacheDir，
    // 而不是无权限的 /data/local/tmp（temp_dir() 在 Android 上的返回值）。
    let data_dir = if let Some(out) = &args.output {
        out.parent()
            .filter(|p| !p.as_os_str().is_empty())
            .map(|p| p.to_path_buf())
            .unwrap_or_else(std::env::temp_dir)
    } else {
        std::env::temp_dir()
    };
    std::fs::create_dir_all(&data_dir).ok();
    init_data_dir(Some(&data_dir));

    // 组合订阅 URL：用户传入的 + 默认内置
    let mut urls = args.urls.clone();
    urls.push(DEFAULT_SUB_URL.to_string());

    eprintln!(
        "[android] workers={} top={} urls={}",
        args.workers,
        args.top,
        urls.len()
    );

    // 复用原有 task 逻辑，但结果通过 JSON 返回
    let result = task::run_task_android(args.workers, args.top, urls).await;

    let json = serde_json::to_string(&result).unwrap_or_else(|_| "[]".to_string());

    match args.output {
        Some(path) => {
            std::fs::write(&path, &json).expect("failed to write output file");
            eprintln!("[android] result written to {}", path.display());
        }
        None => {
            println!("{}", json);
        }
    }
}

// ── Server 模式（原有逻辑，完整保留）────────────────────────────

#[cfg(not(feature = "android"))]
async fn run_server(args: ServeArgs) {
    use crate::config::{data_path, CACHE_M3U8, CACHE_TXT};
    use crate::output::read_cache;
    use axum::{routing::get, Router};
    use chrono_tz::Tz;
    use cron::Schedule;
    use std::str::FromStr;
    use std::sync::Arc;

    init_data_dir(args.dir.as_deref());
    eprintln!("[main] data dir: {}", config::data_dir().display());

    let mut urls: Vec<String> = [
        &args.url1, &args.url2, &args.url3, &args.url4, &args.url5,
    ]
    .iter()
    .filter_map(|o| o.as_deref().map(str::to_string))
    .collect();
    urls.push(DEFAULT_SUB_URL.to_string());

    let tz: Tz = args.timezone.parse().unwrap_or_else(|_| {
        eprintln!("[warn] unknown timezone, falling back to Asia/Shanghai");
        "Asia/Shanghai".parse().unwrap()
    });

    let cron_expr = format!("0 {}", args.cron.trim());
    let schedule = Schedule::from_str(&cron_expr).unwrap_or_else(|e| {
        eprintln!("[error] invalid cron '{}': {}", args.cron, e);
        std::process::exit(1);
    });

    eprintln!(
        "IPTV Aggregator v{}  port={}  workers={}  top={}  cron=\"{}\"  tz={}",
        VERSION, args.port, args.workers, args.top_n, args.cron, tz
    );

    let cache_m3u8 = data_path(CACHE_M3U8);
    let cache_txt = data_path(CACHE_TXT);
    let cache_exists = cache_m3u8.exists() && cache_txt.exists();
    let (m3u8, txt) = read_cache();

    let last_run_init = if cache_exists {
        get_file_mtime(&cache_m3u8.to_string_lossy())
            .unwrap_or_else(|| "cached (unknown time)".to_string())
    } else {
        "Never".to_string()
    };

    let state = Arc::new(AppState {
        data: RwLock::new(SharedData { m3u8, txt, last_run: last_run_init }),
        workers: args.workers,
        top_n: args.top_n,
        urls: urls.clone(),
    });

    if !cache_exists {
        let st = state.clone();
        let us = urls.clone();
        let (w, t) = (args.workers, args.top_n);
        tokio::spawn(async move { task::run_task(st, w, t, us).await });
    }

    {
        let st = state.clone();
        let us = urls.clone();
        let (w, t) = (args.workers, args.top_n);
        tokio::spawn(async move {
            loop {
                let now_utc = chrono::Utc::now();
                let now_tz = now_utc.with_timezone(&tz);
                let Some(next_time) = schedule.after(&now_tz).next() else { break };
                let next_utc = next_time.with_timezone(&chrono::Utc);
                let wait_secs = (next_utc - now_utc).num_seconds().max(0) as u64;
                eprintln!("[cron] next run in {}s", wait_secs);
                tokio::time::sleep(tokio::time::Duration::from_secs(wait_secs)).await;
                tokio::spawn(task::run_task(st.clone(), w, t, us.clone()));
            }
        });
    }

    let app = Router::new()
        .route("/", get(server::handle_m3u8))
        .route("/iptv", get(server::handle_m3u8))
        .route("/txt", get(server::handle_txt))
        .route("/status", get(server::handle_status))
        .route("/retest", get(server::handle_force_retest))
        .with_state(state);

    let addr = format!("0.0.0.0:{}", args.port);
    eprintln!("[main] listening on http://{}", addr);
    let listener = tokio::net::TcpListener::bind(&addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}

#[cfg(not(feature = "android"))]
fn get_file_mtime(path: &str) -> Option<String> {
    use std::time::UNIX_EPOCH;
    let meta = std::fs::metadata(path).ok()?;
    let mtime = meta.modified().ok()?;
    let secs = mtime.duration_since(UNIX_EPOCH).ok()?.as_secs() as i64;
    let dt = chrono::DateTime::<chrono::Utc>::from_timestamp(secs, 0)?;
    let local = dt.with_timezone(&chrono::Local);
    Some(local.format("%Y-%m-%d %H:%M:%S").to_string())
}
