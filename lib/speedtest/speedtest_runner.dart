import 'dart:convert';
import 'dart:io';

import 'package:collection/collection.dart';
import 'package:http/http.dart' as http;
import 'package:path/path.dart' as p;

import '../utils/app_paths.dart';
import 'channel_map.dart';
import 'config.dart';
import 'host_tester.dart';
import 'output_builder.dart';
import 'subscribe.dart';
import 'types.dart';

/// 测速主流程。1:1 移植自 `src-tauri/src/speedtest/task.rs::run_task_with_progress`。
///
/// 通过 [onProgress] 回报阶段文本与百分比, 返回写入的频道名数量 (0 表示无可用频道)。
Future<int> runSpeedtest({
  required int workers,
  required int topN,
  required List<String> urls,
  required File outputPath,
  required void Function(SpeedtestProgress) onProgress,
}) async {
  final start = DateTime.now();
  onProgress(const SpeedtestProgress(phase: '── 测速开始 ──'));

  final stdMap = await getStandardChannelMap();
  final dataDir = await AppPaths.dataDir();
  final allEntries = <Entry>[];
  var sourceIdx = 0;

  // Step 1 & 2 并行: 下载订阅文件 + 获取 API 网关列表
  onProgress(const SpeedtestProgress(phase: '正在获取 API 列表和订阅文件…', percent: 10));
  final results = await Future.wait([
    _fetchApiData(),
    downloadSubscribes(urls),
  ]);
  final apiItems = results[0] as List<Map<String, dynamic>>;
  final subCache = results[1] as Map<String, String>;

  // Step 3: 并发测速 API 网关
  if (apiItems.isNotEmpty) {
    onProgress(SpeedtestProgress(phase: '正在测速 ${apiItems.length} 个 API 源…', percent: 30));
    final rawResults = await runApiSpeedTests(apiItems, workers);
    var topSources = _selectTopSources(rawResults, topN);
    onProgress(SpeedtestProgress(
      phase: '筛选出 ${topSources.length} 个优质 API 源，获取频道中…',
      percent: 50,
    ));

    for (var i = 0; i < topSources.length; i++) {
      final chs = await fetchChannelsForSource(topSources[i]);
      topSources[i] = topSources[i].copyWith(channels: chs);
      final entries = switch (topSources[i].matchType) {
        'txiptv' || 'zhgxtv' || 'jsmpeg' => _buildEntries(
            topSources[i].channels, sourceIdx, topSources[i].speed, stdMap),
        'hsmdtv' => _processHsmdtvChannels(
            dataDir, topSources[i].host, sourceIdx, topSources[i].speed, stdMap),
        _ => const <Entry>[],
      };
      allEntries.addAll(entries);
      sourceIdx++;
    }
  }

  // Step 4: 测速订阅源
  for (final entry in subCache.entries) {
    final rawUrl = entry.key;
    final cachePath = entry.value;
    final channels = parseSubscribeFile(cachePath);
    if (channels.isEmpty) continue;
    onProgress(SpeedtestProgress(phase: '正在测速订阅源频道 (${channels.length} 个)…', percent: 60));
    final hostSpeeds = await testSubscribeHosts(channels, workers);

    var added = 0;
    for (final ch in channels) {
      final hk = hostKey(ch.url);
      final spd = hostSpeeds[hk];
      if (spd == null || spd < SpeedtestConfig.speedLow) continue;
      final name = mapToStandardName(cleanChannelName(ch.name), stdMap);
      allEntries.add(Entry(
        name: name,
        url: ch.url,
        content: buildM3u8Entry(name, ch.url, spd),
        index: sourceIdx,
        speed: spd,
      ));
      added++;
    }
    onProgress(SpeedtestProgress(
      phase: '订阅源筛选完成，保留 $added/${channels.length} 个频道',
      percent: 75,
    ));
    sourceIdx++;
  }

  onProgress(SpeedtestProgress(
    phase: '收集到 ${allEntries.length} 个候选频道，正在整理写入…',
    percent: 85,
  ));

  if (allEntries.isEmpty) {
    onProgress(const SpeedtestProgress(phase: '未找到可用频道'));
    return 0;
  }

  // Step 5: 整理 -> 去重 -> 排序 -> 写 m3u8
  final m3u8 = buildM3u8(allEntries, DateTime.now());
  try {
    await outputPath.writeAsString(m3u8);
  } catch (e) {
    onProgress(SpeedtestProgress(phase: '写入失败: $e'));
    return 0;
  }

  final channelCount = _countChannelNames(m3u8);
  onProgress(SpeedtestProgress(
    phase: '测速完成！共 $channelCount 个频道（耗时 ${DateTime.now().difference(start).inSeconds}s）',
    percent: 100,
  ));
  return channelCount;
}

// ── 内部辅助 ──────────────────────────────────────────────────────

Future<List<Map<String, dynamic>>> _fetchApiData() async {
  final client = http.Client();
  for (var attempt = 1; attempt <= 3; attempt++) {
    print('[api] fetch attempt $attempt: ${SpeedtestConfig.apiUrl}');
    try {
      final resp = await client
          .get(Uri.parse(SpeedtestConfig.apiUrl))
          .timeout(const Duration(seconds: 10));
      if (resp.statusCode == 200) {
        final data = jsonDecode(resp.body) as Map<String, dynamic>;
        final results = data['results'];
        if (results is List) {
          final out = <Map<String, dynamic>>[];
          for (final r in results) {
            if (r is Map<String, dynamic>) out.add(r);
          }
          print('[api] received ${out.length} hosts');
          return out;
        }
      }
    } catch (_) {}
    await Future.delayed(const Duration(seconds: 5));
  }
  print('[api] fetch failed after 3 retries');
  return [];
}

List<SourceResult> _selectTopSources(List<SourceResult> results, int topN) {
  results.sort((a, b) => b.speed.compareTo(a.speed));
  final selectedHosts = <String>{};
  final finalResults = <SourceResult>[];

  for (final mt in ['txiptv', 'hsmdtv', 'zhgxtv', 'jsmpeg']) {
    final r = results.firstWhereOrNull(
      (r) => r.matchType == mt && !selectedHosts.contains(r.host),
    );
    if (r != null) {
      selectedHosts.add(r.host);
      finalResults.add(r);
    }
  }
  for (final r in results) {
    if (finalResults.length >= topN) break;
    if (!selectedHosts.contains(r.host)) {
      selectedHosts.add(r.host);
      finalResults.add(r);
    }
  }
  finalResults.sort((a, b) => b.speed.compareTo(a.speed));
  return finalResults;
}

List<Entry> _buildEntries(
  List<TestChannel> channels,
  int idx,
  double speed,
  Map<String, String> stdMap,
) {
  return channels.map((ch) {
    final name = mapToStandardName(cleanChannelName(ch.name), stdMap);
    return Entry(
      content: buildM3u8Entry(name, ch.url, speed),
      name: name,
      url: ch.url,
      index: idx,
      speed: speed,
    );
  }).toList();
}

final _reUrl = RegExp(r'(http://[^\s]+)');
final _reId = RegExp(r'^\s*\d+\s+');

List<Entry> _processHsmdtvChannels(
  Directory dataDir,
  String host,
  int sourceIndex,
  double speed,
  Map<String, String> stdMap,
) {
  // 注: 原版读 HSMD_ADDRESS_LIST_FILE (本地文本)。这里读 AppPaths.dataDir 下的同名文件。
  final f = File(p.join(dataDir.path, SpeedtestConfig.hsmdAddressListFile));
  if (!f.existsSync()) {
    print('[hsmd] ${SpeedtestConfig.hsmdAddressListFile} not found, skipping');
    return [];
  }
  final entries = <Entry>[];
  for (final raw in f.readAsStringSync().split('\n')) {
    final line = raw.trim();
    if (line.isEmpty) continue;
    final m = _reUrl.firstMatch(line);
    if (m == null) continue;
    final urlInFile = m.group(0)!;
    final before = line.substring(0, m.start);
    // 去掉开头编号 + "（默认频道）" 标记
    final nameRaw = before.replaceAll(_reId, '').replaceAll('（默认频道）', '').trim();
    final name = mapToStandardName(cleanChannelName(nameRaw), stdMap);
    final p = Uri.tryParse(urlInFile);
    if (p == null) continue;
    final newUrl = 'http://$host${p.path}';
    entries.add(Entry(
      content: buildM3u8Entry(name, newUrl, speed),
      name: name,
      url: newUrl,
      index: sourceIndex,
      speed: speed,
    ));
  }
  return entries;
}

int _countChannelNames(String m3u8) {
  final names = <String>{};
  for (final line in m3u8.split('\n')) {
    if (line.startsWith('#EXTINF')) {
      final i = line.lastIndexOf(',');
      if (i >= 0) {
        final name = line.substring(i + 1).trim();
        if (name.isNotEmpty) names.add(name);
      }
    }
  }
  return names.length;
}
