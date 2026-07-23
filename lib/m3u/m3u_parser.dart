import 'channel.dart';

/// 解析本地 m3u8 播放列表。1:1 移植自 `src-tauri/src/m3u.rs::parse_m3u`。
///
/// 行格式:
///   #EXTINF:-1 tvg-name="..." tvg-logo="..." group-title="...",频道名
///   http://stream/url
List<Channel> parseM3u(String content) {
  final channels = <Channel>[];
  var name = '';
  var group = '';
  var logo = '';

  for (final raw in content.split('\n')) {
    final line = raw.trim();
    if (line.startsWith('#EXTINF')) {
      name = _extractAttr(line, 'tvg-name') ??
          (() {
            final i = line.lastIndexOf(',');
            return i >= 0 ? line.substring(i + 1).trim() : '';
          })();
      if (name.isEmpty) {
        final i = line.lastIndexOf(',');
        name = i >= 0 ? line.substring(i + 1).trim() : '';
      }
      group = _extractAttr(line, 'group-title')?.isNotEmpty == true
          ? _extractAttr(line, 'group-title')!
          : '其他频道';
      logo = _extractAttr(line, 'tvg-logo') ?? '';
    } else if (line.isNotEmpty &&
        !line.startsWith('#') &&
        name.isNotEmpty) {
      channels.add(Channel(
        name: name,
        url: line,
        group: group,
        logo: logo,
      ));
      name = '';
    }
  }
  return channels;
}

String? _extractAttr(String line, String attr) {
  final key = '$attr="';
  final start = line.indexOf(key);
  if (start < 0) return null;
  final valueStart = start + key.length;
  final end = line.indexOf('"', valueStart);
  if (end < 0) return null;
  return line.substring(valueStart, end);
}
