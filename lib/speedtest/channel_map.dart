import 'dart:io';

import 'package:path/path.dart' as p;

import '../utils/app_paths.dart';
import 'config.dart';

/// 频道名清洗 / 分组 / 标准名映射 / 排序键。
/// 1:1 移植自 `src-tauri/src/speedtest/channel.rs`。

// ── 分组 ──────────────────────────────────────────────────────────

String baseGroup(String name) {
  final upper = name.toUpperCase();
  if (upper.contains('CCTV')) return '央视频道';
  if (name.contains('卫视')) return '卫视频道';
  return '其他频道';
}

String fullGroup(String name, double speed) => baseGroup(name);

String buildLogoUrl(String name) {
  final encoded = Uri.encodeQueryComponent(name).replaceAll('+', '%20');
  return '${SpeedtestConfig.logoBaseUrl}$encoded.png';
}

String buildM3u8Entry(String name, String streamUrl, double speed) {
  final grp = fullGroup(name, speed);
  return '#EXTINF:-1 tvg-name="$name" tvg-logo="${buildLogoUrl(name)}" '
      'group-title="$grp",$name\n$streamUrl';
}

// ── 频道名清洗 ────────────────────────────────────────────────────

final _reCctvExtract = RegExp(r'CCTV(\d{1,2})(\+)?');
final _reWeixiExtract = RegExp(r'([\u4e00-\u9fff]+卫视)');
final _reCctvNum = RegExp(r'CCTV(\d+)台');

String cleanChannelName(String name) {
  var s = name;

  // 别名映射 (清洗前先替换原始名)
  s = _normalizeAlias(s);

  s = s.replaceAll('cctv', 'CCTV');
  s = s.replaceAll('中央', 'CCTV');
  s = s.replaceAll('央视', 'CCTV');
  for (final rep in ['高清', '超高', 'HD', '标清', '频道', '-', ' ', '(', ')']) {
    s = s.replaceAll(rep, '');
  }
  s = s.replaceAll('PLUS', '+');
  s = s.replaceAll('＋', '+');
  s = s.replaceAllMapped(_reCctvNum, (m) => 'CCTV${m.group(1)}');

  // CCTV+数字: 提取数字, 校验 1~17 (含 5+)
  final caps = _reCctvExtract.firstMatch(s);
  if (caps != null) {
    final num = int.tryParse(caps.group(1)!) ?? 0;
    final isPlus = caps.group(2) != null;
    if (isPlus && num == 5) return 'CCTV5+';
    if (num >= 1 && num <= 17) return 'CCTV$num';
    return s.replaceAll('CCTV', ''); // 超范围 -> 去掉 CCTV 前缀
  }

  // XX卫视 (汉字) -> 提取为标准名
  final wei = _reWeixiExtract.firstMatch(s);
  if (wei != null) return wei.group(1)!;

  // 含 CCTV 但无合法编号 -> 去掉 CCTV
  if (s.toUpperCase().contains('CCTV')) {
    return s.replaceAll('CCTV', '').replaceAll('cctv', '');
  }

  return s;
}

String _normalizeAlias(String name) {
  switch (name) {
    case '上海卫视':
      return '东方卫视';
    case '内蒙卫视':
      return '内蒙古卫视';
    case '福建卫视':
      return '东南卫视';
    case '上海':
      return '东方卫视';
    case '内蒙':
      return '内蒙古卫视';
    case '福建':
      return '东南卫视';
    default:
      return name;
  }
}

// ── 标准名映射 ────────────────────────────────────────────────────

/// 读取 channel_list.txt 构建 {normalKey: 标准名} 映射。
/// 对应 Rust `get_standard_channel_map`, 文件位于 AppPaths.dataDir。
Future<Map<String, String>> getStandardChannelMap() async {
  final dir = await AppPaths.dataDir();
  final f = File(p.join(dir.path, SpeedtestConfig.channelListFile));
  if (!f.existsSync()) return {};
  final m = <String, String>{};
  for (final raw in f.readAsStringSync().split('\n')) {
    final std = raw.trim();
    if (std.isEmpty) continue;
    m[normalKey(std)] = std;
  }
  return m;
}

String normalKey(String s) => s.toUpperCase().replaceAll('-', '').replaceAll(' ', '');

String mapToStandardName(String name, Map<String, String> m) {
  return m[normalKey(name)] ?? name;
}

// ── 卫视排序 ──────────────────────────────────────────────────────

const _weixiOrder = <String>[
  '湖南卫视', '东方卫视', '浙江卫视', '江苏卫视', '北京卫视', '山东卫视', '河南卫视',
  '广东卫视', '安徽卫视', '深圳卫视', '天津卫视', '江西卫视', '四川卫视', '湖北卫视',
  '重庆卫视', '黑龙江卫视', '辽宁卫视', '河北卫视', '吉林卫视', '山西卫视', '广西卫视',
  '云南卫视', '东南卫视', '贵州卫视', '陕西卫视', '甘肃卫视', '内蒙古卫视', '新疆卫视',
  '宁夏卫视', '青海卫视', '西藏卫视', '海南卫视', '兵团卫视',
];

int? weixiSortIndex(String name) {
  for (var i = 0; i < _weixiOrder.length; i++) {
    if (name.contains(_weixiOrder[i])) return i;
  }
  return null;
}

/// 返回 (category, subOrder, name) 用于排序。对应 Rust `channel_sort_key`。
List<dynamic> channelSortKey(String name) {
  final upper = name.toUpperCase();
  if (upper.contains('CCTV')) {
    final re = RegExp(r'CCTV(\d+)');
    final m = re.firstMatch(upper);
    if (m != null) {
      final num = double.tryParse(m.group(1)!) ?? 999.0;
      return [0, num, ''];
    }
    if (upper.contains('5+')) return [0, 5.5, ''];
    return [0, 999.0, ''];
  }
  if (name.contains('卫视')) {
    final idx = weixiSortIndex(name);
    if (idx != null) return [1, idx.toDouble(), name];
    return [1, _weixiOrder.length.toDouble(), name];
  }
  return [2, 0.0, name];
}

int compareSortKey(List<dynamic> a, List<dynamic> b) {
  final c = (a[0] as int).compareTo(b[0] as int);
  if (c != 0) return c;
  final c2 = (a[1] as double).compareTo(b[1] as double);
  if (c2 != 0) return c2;
  return (a[2] as String).compareTo(b[2] as String);
}
