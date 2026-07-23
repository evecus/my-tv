import 'package:flutter/foundation.dart';

/// 单个 IPTV 频道 (从本地 m3u8 解析出来的播放条目)。
/// 对应 Rust `m3u::Channel`。
@immutable
class Channel {
  const Channel({
    required this.name,
    required this.url,
    required this.group,
    required this.logo,
  });

  final String name;
  final String url;
  final String group;
  final String logo;

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      (other is Channel && other.url == url && other.name == name);

  @override
  int get hashCode => Object.hash(url, name);

  @override
  String toString() => 'Channel($name, $group)';
}
