import 'dart:io';

import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

/// 本地播放列表 / 缓存文件路径。对应 Rust `m3u::m3u_path` + `speedtest::config::data_dir`。
///
/// Windows: %APPDATA%\mytv-desktop\iptv_sources.m3u8
/// (getApplicationSupportDirectory 在 Windows 上返回 %APPDATA%\<org>.<app>)
class AppPaths {
  static const _appName = 'mytv-desktop';
  static const m3u8FileName = 'iptv_sources.m3u8';
  static const channelListFileName = 'channel_list.txt';
  static const hsmdAddressListFileName = 'hsmd_address_list.txt';

  static Future<Directory> dataDir() async {
    final base = await getApplicationSupportDirectory();
    final dir = Directory(p.join(base.path, _appName));
    if (!dir.existsSync()) {
      await dir.create(recursive: true);
    }
    return dir;
  }

  /// 本地播放列表路径 (测速结果写入这里、播放时从这里读取)。
  static Future<File> m3uPath() async {
    final dir = await dataDir();
    return File(p.join(dir.path, m3u8FileName));
  }

  static Future<bool> hasLocalSource() async {
    final f = await m3uPath();
    return f.existsSync() && f.lengthSync() > 0;
  }

  /// 测速过程中的临时缓存目录 (订阅文件缓存 / hsmd 地址表)。
  static Future<Directory> cacheDir() async {
    final dir = await dataDir();
    final cache = Directory(p.join(dir.path, 'cache'));
    if (!cache.existsSync()) {
      await cache.create(recursive: true);
    }
    return cache;
  }
}
