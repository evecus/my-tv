import 'dart:collection';

import 'package:intl/intl.dart';

import 'channel_map.dart';
import 'config.dart';
import 'types.dart';

/// 聚合所有条目、去重、排序，返回 m3u8 字符串。
/// 1:1 移植自 `src-tauri/src/speedtest/output.rs::build_and_write`。
String buildM3u8(List<Entry> allEntries, DateTime updateTime) {
  // 按频道名分组
  final byName = <String, List<Entry>>{};
  for (final e in allEntries) {
    byName.putIfAbsent(e.name, () => []).add(e);
  }

  // 频道名排序
  final allNames = byName.keys.toList()
    ..sort((a, b) => compareSortKey(channelSortKey(a), channelSortKey(b)));

  // 每个频道去重并按速度降序排序
  for (final entries in byName.values) {
    final seen = HashSet<String>();
    entries.retainWhere((e) => seen.add(e.url));
    entries.sort((a, b) {
      final c = b.speed.compareTo(a.speed);
      if (c != 0) return c;
      return a.index.compareTo(b.index);
    });
  }

  final ts = DateFormat('yyyy-MM-dd HH:mm:ss').format(updateTime);

  final lines = <String>[
    '#EXTM3U x-tvg-url="${SpeedtestConfig.epgUrl}"',
    '#EXT-X-UPDATED: $ts',
  ];

  for (final grp in SpeedtestConfig.groups) {
    for (final name in allNames) {
      if (baseGroup(name) != grp) continue;
      final entries = byName[name];
      if (entries == null) continue;
      for (final e in entries) {
        lines.add(e.content);
      }
    }
  }

  return lines.join('\n');
}
