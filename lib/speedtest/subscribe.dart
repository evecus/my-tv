import 'dart:io';

import 'package:http/http.dart' as http;
import 'package:path/path.dart' as p;

import '../utils/app_paths.dart';
import 'config.dart';
import 'types.dart';

/// 订阅文件下载与解析。1:1 移植自 `src-tauri/src/speedtest/subscribe.rs`。

/// 并发下载所有订阅 URL, 缓存到本地文件。返回 {url: 本地路径}。
Future<Map<String, String>> downloadSubscribes(List<String> urls) async {
  final cacheDir = await AppPaths.cacheDir();
  final futures = <Future<({String url, String path})?>>[];
  for (var i = 0; i < urls.length; i++) {
    final rawUrl = urls[i].trim();
    if (rawUrl.isEmpty) continue;
    final idx = i;
    futures.add(_downloadOne(rawUrl, idx, cacheDir));
  }
  final results = await Future.wait(futures);
  final out = <String, String>{};
  for (final r in results) {
    if (r != null) out[r.url] = r.path;
  }
  return out;
}

Future<({String url, String path})?> _downloadOne(
  String rawUrl,
  int idx,
  Directory cacheDir,
) async {
  final cachePath = p.join(cacheDir.path, 'sub_cache_$idx.txt');
  try {
    final resp = await http.get(Uri.parse(rawUrl)).timeout(SpeedtestConfig.subTimeout);
    if (resp.statusCode != 200) {
      print('[subscribe] skip $rawUrl: HTTP ${resp.statusCode}');
      return null;
    }
    await File(cachePath).writeAsBytes(resp.bodyBytes);
    print('[subscribe] downloaded (${resp.bodyBytes.length} bytes): $rawUrl');
    return (url: rawUrl, path: cachePath);
  } catch (e) {
    print('[subscribe] skip $rawUrl: $e');
    return null;
  }
}

/// 解析订阅缓存文件, 自动识别 m3u / txt 格式。
List<TestChannel> parseSubscribeFile(String path) {
  final f = File(path);
  if (!f.existsSync()) return [];
  final content = f.readAsStringSync().trimLeft();
  if (content.startsWith('#EXTM3U')) return _parseM3u(content);
  return _parseTxtChannels(content);
}

List<TestChannel> _parseM3u(String content) {
  final channels = <TestChannel>[];
  var pending = '';
  for (final raw in content.split('\n')) {
    final line = raw.trim();
    if (line.startsWith('#EXTINF')) {
      final i = line.lastIndexOf(',');
      pending = i >= 0 ? line.substring(i + 1).trim() : '';
    } else if (line.isNotEmpty && !line.startsWith('#') && pending.isNotEmpty) {
      channels.add(TestChannel(name: pending, url: line));
      pending = '';
    }
  }
  return channels;
}

List<TestChannel> _parseTxtChannels(String content) {
  final channels = <TestChannel>[];
  for (final raw in content.split('\n')) {
    final line = raw.trim();
    if (line.isEmpty || line.startsWith('#')) continue;
    final parts = line.split(',');
    if (parts.length < 2) continue;
    final name = parts[0].trim();
    final url = parts[1].trim();
    if (name.isEmpty || url.isEmpty || url.contains('#genre#')) continue;
    channels.add(TestChannel(name: name, url: url));
  }
  return channels;
}

/// 提取 url 的 scheme://host 作为主机 key。
String hostKey(String rawUrl) {
  final u = Uri.tryParse(rawUrl);
  if (u != null && u.host.isNotEmpty) {
    final scheme = u.scheme.isEmpty ? 'http' : u.scheme;
    return '$scheme://${u.host}';
  }
  return rawUrl;
}
