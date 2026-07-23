import 'dart:async';
import 'dart:convert';

import 'package:collection/collection.dart';
import 'package:http/http.dart' as http;
import 'package:pool/pool.dart';

import 'config.dart';
import 'subscribe.dart';
import 'types.dart';

/// HTTP 测速核心。1:1 移植自 `src-tauri/src/speedtest/speedtest.rs`。

http.Client _makeClient(Duration timeout) {
  return http.Client();
}

/// 从 m3u8 播放列表获取第一个 TS 分片 URL。
Future<String?> getTsUrl(String m3u8Url, Duration timeout) async {
  http.Response resp;
  try {
    resp = await http.get(Uri.parse(m3u8Url)).timeout(timeout);
  } catch (_) {
    return null;
  }
  if (resp.statusCode != 200) return null;
  final body = resp.body;
  final parsed = Uri.tryParse(m3u8Url);
  if (parsed == null) return null;
  final origin = '${parsed.scheme}://${parsed.host}';
  final slash = m3u8Url.lastIndexOf('/');
  if (slash < 0) return null;
  final base = m3u8Url.substring(0, slash + 1);

  for (final raw in body.split('\n')) {
    final line = raw.trim();
    if (line.isEmpty || line.startsWith('#')) continue;
    if (line.startsWith('http')) return line;
    if (line.startsWith('/')) return '$origin$line';
    return '$base$line';
  }
  return null;
}

/// 下载 stream_url 最多 SPEED_TEST_SECS 秒并返回 MB/s。
/// 必须**流式**读取: IPTV 直播 TS 流是无限流, 一次性 bodyBytes 读取会永久阻塞。
/// 对应 Rust `measure_speed` (用 bytes_stream + 500ms 超时窗口)。
Future<double> measureSpeed(String streamUrl, DateTime deadline) async {
  final now = DateTime.now();
  if (!now.isBefore(deadline)) return -1.0;
  final remaining = deadline.difference(now);
  final client = _makeClient(remaining);
  final start = DateTime.now();

  http.StreamedResponse resp;
  try {
    final request = http.Request('GET', Uri.parse(streamUrl));
    resp = await client.send(request).timeout(
      remaining < const Duration(seconds: 10) ? remaining : const Duration(seconds: 10),
    );
  } catch (_) {
    client.close();
    return -1.0;
  }
  if (resp.statusCode >= 400) {
    client.close();
    return -1.0;
  }

  var size = 0;
  try {
    // 每 500ms 没有新分片即视为超时退出 (对应 tokio::timeout(500ms, stream.next()))。
    await for (final chunk in resp.stream.timeout(const Duration(milliseconds: 500))) {
      size += chunk.length;
      if (size > 10 * 1024 * 1024) break;
      if (DateTime.now().isAfter(deadline)) break;
      if (DateTime.now().difference(start) > SpeedtestConfig.speedTestSecs) break;
    }
  } on TimeoutException {
    // 500ms 无新数据 -> 退出 (与 Rust 的 _ => break 等价)
  } catch (_) {
    // 流中途断开
  } finally {
    client.close();
  }

  final dur = DateTime.now().difference(start).inMilliseconds / 1000.0;
  final secs = dur < 0.001 ? 0.001 : dur;
  return size / 1024.0 / 1024.0 / secs;
}

/// 解析 m3u8 -> 找到第一个分片 -> 测速。
Future<double> testStreamUrl(String streamUrl, DateTime deadline) async {
  if (!DateTime.now().isBefore(deadline)) return -1.0;
  final remaining = deadline.difference(DateTime.now());
  final ts = await getTsUrl(
    streamUrl,
    remaining < const Duration(seconds: 5) ? remaining : const Duration(seconds: 5),
  );
  if (ts == null) return -1.0;
  if (!DateTime.now().isBefore(deadline)) return -1.0;
  return measureSpeed(ts, deadline);
}

// ── 各类型测速 ────────────────────────────────────────────────────

Future<(double, List<TestChannel>)> _testTxiptv(
  String host,
  DateTime deadline, {
  required bool fetchCh,
}) async {
  if (!DateTime.now().isBefore(deadline)) return (-1.0, const <TestChannel>[]);
  final remaining = deadline.difference(DateTime.now());
  final url = 'http://$host/iptv/live/1000.json?key=txiptv';
  http.Response resp;
  try {
    resp = await http
        .get(Uri.parse(url))
        .timeout(remaining < const Duration(seconds: 2) ? remaining : const Duration(seconds: 2));
  } catch (_) {
    return (-1.0, const <TestChannel>[]);
  }
  if (resp.statusCode != 200) return (-1.0, const <TestChannel>[]);
  final data = jsonDecode(resp.body) as Map<String, dynamic>;

  final channels = <TestChannel>[];
  var firstUrl = '';
  final arr = data['data'];
  if (arr is List) {
    for (final d in arr) {
      if (d is! Map) continue;
      final name = (d['name'] ?? '').toString();
      final u = (d['url'] ?? '').toString();
      if (name.isEmpty || u.isEmpty || u.contains(',')) continue;
      String full;
      if (u.contains('http')) {
        full = u;
      } else if (u.startsWith('/')) {
        full = 'http://$host$u';
      } else {
        full = 'http://$host/$u';
      }
      if (fetchCh) channels.add(TestChannel(name: name, url: full));
      if (firstUrl.isEmpty) firstUrl = full;
    }
  }
  if (firstUrl.isEmpty) return (-1.0, channels);
  final speed = await testStreamUrl(firstUrl, deadline);
  return (speed, channels);
}

Future<double> _testHsmdtv(String host, DateTime deadline) async {
  if (!DateTime.now().isBefore(deadline)) return -1.0;
  final url = 'http://$host${SpeedtestConfig.hsmdtvTestUri}';
  return testStreamUrl(url, deadline);
}

Future<(double, List<TestChannel>)> _testJsmpeg(
  String host,
  DateTime deadline, {
  required bool fetchCh,
}) async {
  if (!DateTime.now().isBefore(deadline)) return (-1.0, const <TestChannel>[]);
  final remaining = deadline.difference(DateTime.now());
  final url = 'http://$host/streamer/list';
  http.Response resp;
  try {
    resp = await http
        .get(Uri.parse(url))
        .timeout(remaining < const Duration(seconds: 2) ? remaining : const Duration(seconds: 2));
  } catch (_) {
    return (-1.0, const <TestChannel>[]);
  }
  if (resp.statusCode != 200) return (-1.0, const <TestChannel>[]);
  final list = jsonDecode(resp.body);
  if (list is! List) return (-1.0, const <TestChannel>[]);

  final channels = <TestChannel>[];
  var firstUrl = '';
  for (final d in list) {
    if (d is! Map) continue;
    final name = (d['name'] ?? '').toString().trim();
    final key = (d['key'] ?? '').toString().trim();
    if (name.isEmpty || key.isEmpty) continue;
    final full = 'http://$host/hls/$key/index.m3u8';
    if (fetchCh) channels.add(TestChannel(name: name, url: full));
    if (firstUrl.isEmpty) firstUrl = full;
  }
  if (firstUrl.isEmpty) return (-1.0, channels);
  final speed = await testStreamUrl(firstUrl, deadline);
  return (speed, channels);
}

Future<(double, List<TestChannel>)> _testZhgxtv(
  String host,
  DateTime deadline, {
  required bool fetchCh,
}) async {
  if (!DateTime.now().isBefore(deadline)) return (-1.0, const <TestChannel>[]);
  final remaining = deadline.difference(DateTime.now());
  final url = 'http://$host${SpeedtestConfig.zhgxtvInterface}';
  http.Response resp;
  try {
    resp = await http
        .get(Uri.parse(url))
        .timeout(remaining < const Duration(seconds: 5) ? remaining : const Duration(seconds: 5));
  } catch (_) {
    return (-1.0, const <TestChannel>[]);
  }
  if (resp.statusCode != 200) return (-1.0, const <TestChannel>[]);
  final body = resp.body;

  final channels = <TestChannel>[];
  var firstUrl = '';
  for (final raw in body.split('\n')) {
    final line = raw.trim();
    if (!line.contains(',')) continue;
    final parts = line.split(',');
    if (parts.length < 2) continue;
    final name = parts[0].trim();
    final urlPart = parts.sublist(1).join(',').trim();
    String full;
    if (urlPart.startsWith('http')) {
      final p = Uri.tryParse(urlPart);
      if (p == null) continue;
      full = '${p.scheme}://$host${p.path}';
      if (p.query.isNotEmpty) full += '?${p.query}';
    } else if (urlPart.startsWith('/')) {
      full = 'http://$host$urlPart';
    } else {
      full = 'http://$host/$urlPart';
    }
    if (fetchCh) channels.add(TestChannel(name: name, url: full));
    if (firstUrl.isEmpty) firstUrl = full;
  }
  if (firstUrl.isEmpty) return (-1.0, channels);
  final speed = await testStreamUrl(firstUrl, deadline);
  return (speed, channels);
}

// ── 公开接口 ──────────────────────────────────────────────────────

/// 测试单个 API 主机。返回 (speed, channels)。
Future<(double, List<TestChannel>)> testApiHostSpeed(
  String host,
  String matchType, {
  required bool fetchChannels,
}) async {
  final deadline = DateTime.now().add(SpeedtestConfig.hostTimeout);
  switch (matchType) {
    case 'txiptv':
      return _testTxiptv(host, deadline, fetchCh: fetchChannels);
    case 'hsmdtv':
      final spd = await _testHsmdtv(host, deadline);
      return (spd, const <TestChannel>[]);
    case 'jsmpeg':
      return _testJsmpeg(host, deadline, fetchCh: fetchChannels);
    case 'zhgxtv':
      return _testZhgxtv(host, deadline, fetchCh: fetchChannels);
    default:
      return (-1.0, const <TestChannel>[]);
  }
}

/// 为已选定的源补抓频道列表, 返回新频道列表 (调用方用 copyWith 替换)。
/// 对应 Rust `fetch_channels_for_source` (原地处 src.channels = chs)。
Future<List<TestChannel>> fetchChannelsForSource(SourceResult src) async {
  switch (src.matchType) {
    case 'txiptv':
    case 'jsmpeg':
    case 'zhgxtv':
      final (_, chs) = await testApiHostSpeed(src.host, src.matchType, fetchChannels: true);
      return chs;
  }
  return const <TestChannel>[];
}

/// 并发批量测速所有 API 主机, 返回速度 >= SPEED_LOW 的结果。
Future<List<SourceResult>> runApiSpeedTests(
  List<Map<String, dynamic>> items,
  int workers,
) async {
  final total = items.length;
  if (total == 0) return [];
  final pool = Pool(workers);
  final futures = <Future<SourceResult?>>[];

  for (final item in items) {
    futures.add(pool.withResource(() async {
      final host = (item['host'] ?? '').toString();
      final mt = (item['matchType'] ?? '').toString();
      final source = (item['source'] ?? '').toString();
      if (host.isEmpty) return null;
      final (speed, _) = await testApiHostSpeed(host, mt, fetchChannels: false);
      if (speed >= SpeedtestConfig.speedLow) {
        return SourceResult(
          host: host,
          matchType: mt,
          source: source,
          speed: speed,
          channels: const <TestChannel>[],
        );
      }
      return null;
    }));
  }

  final results = await Future.wait(futures);
  await pool.close();
  return results.whereNotNull().toList();
}

/// 为订阅源测速 (每个主机测一个样本 URL)。
Future<Map<String, double>> testSubscribeHosts(
  List<TestChannel> channels,
  int workers,
) async {
  final hostChannels = <String, TestChannel>{};
  for (final ch in channels) {
    hostChannels.putIfAbsent(hostKey(ch.url), () => ch);
  }

  final total = hostChannels.length;
  if (total == 0) return {};
  final pool = Pool(workers);
  final futures = <Future<({String hk, double spd})?>>[];

  for (final entry in hostChannels.entries) {
    final hk = entry.key;
    final url = entry.value.url;
    futures.add(pool.withResource(() async {
      final spd = await _testOneSubscribeUrl(url);
      return (hk: hk, spd: spd < SpeedtestConfig.speedLow ? -1.0 : spd);
    }));
  }

  final results = await Future.wait(futures);
  await pool.close();
  final out = <String, double>{};
  for (final r in results) {
    if (r != null) out[r.hk] = r.spd;
  }
  return out;
}

Future<double> _testOneSubscribeUrl(String rawUrl) async {
  final deadline = DateTime.now().add(SpeedtestConfig.hostTimeout);
  final lower = rawUrl.toLowerCase();
  if (lower.contains('.m3u8') || lower.contains('/hls/') || lower.contains('/live/')) {
    return testStreamUrl(rawUrl, deadline);
  }
  return measureSpeed(rawUrl, deadline);
}
