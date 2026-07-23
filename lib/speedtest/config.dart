/// 测速引擎常量。1:1 移植自 `src-tauri/src/speedtest/config.rs`。
class SpeedtestConfig {
  static const version = '3.0.0';

  static const outputM3u8 = 'iptv_sources.m3u8';

  // 远程端点
  static const apiUrl = 'https://iptvs.pes.im';
  static const epgUrl = 'https://epg.zsdc.eu.org/t.xml';
  static const logoBaseUrl =
      'https://ghfast.top/https://raw.githubusercontent.com/Jarrey/iptv_logo/main/tv/';
  static const defaultSubUrl =
      'http://gh-proxy.com/raw.githubusercontent.com/suxuang/myIPTV/main/ipv4.m3u';

  // 文件名 (放在 AppPaths.dataDir 下)
  static const channelListFile = 'channel_list.txt';
  static const hsmdAddressListFile = 'hsmd_address_list.txt';

  // IPTV 类型路径
  static const zhgxtvInterface = '/ZHGXTV/Public/json/live_interface.txt';
  static const hsmdtvTestUri = '/newlive/live/hls/1/live.m3u8';

  // 速度阈值 (MB/s)
  static const speedLow = 0.5;

  // 超时
  static const hostTimeout = Duration(seconds: 15);
  static const subTimeout = Duration(seconds: 10);
  static const speedTestSecs = Duration(seconds: 8);

  // 并发数 (对应 Vue 端 invoke('start_speedtest', { workers: 60, top: 10 }))
  static const defaultWorkers = 60;
  static const defaultTop = 10;

  // m3u8 输出分组顺序 (对应 output.rs GROUPS)
  static const groups = ['央视频道', '卫视频道', '其他频道'];
}
