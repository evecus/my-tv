import 'package:flutter/foundation.dart';

/// 测速用频道 (仅 name + url, 比 m3u/Channel 更轻)。
/// 对应 Rust `speedtest::types::Channel`。
@immutable
class TestChannel {
  const TestChannel({required this.name, required this.url});
  final String name;
  final String url;
}

/// 经过测速的 API 源。对应 Rust `speedtest::types::SourceResult`。
@immutable
class SourceResult {
  const SourceResult({
    required this.host,
    required this.matchType,
    required this.source,
    required this.speed,
    required this.channels,
  });
  final String host;
  final String matchType; // txiptv | hsmdtv | zhgxtv | jsmpeg
  final String source;
  final double speed; // MB/s
  final List<TestChannel> channels;

  SourceResult copyWith({
    String? host,
    String? matchType,
    String? source,
    double? speed,
    List<TestChannel>? channels,
  }) =>
      SourceResult(
        host: host ?? this.host,
        matchType: matchType ?? this.matchType,
        source: source ?? this.source,
        speed: speed ?? this.speed,
        channels: channels ?? this.channels,
      );
}

/// 可输出的播放列表条目。对应 Rust `speedtest::types::Entry`。
@immutable
class Entry {
  const Entry({
    required this.name,
    required this.url,
    required this.content, // 完整的 #EXTINF + URL 块
    required this.index, // 越小优先级越高
    required this.speed, // MB/s
  });
  final String name;
  final String url;
  final String content;
  final int index;
  final double speed;
}

/// 测速进度事件 (对应 Tauri 的 speedtest://progress 事件 payload)。
@immutable
class SpeedtestProgress {
  const SpeedtestProgress({this.phase = '', this.percent = -1});
  final String phase;
  final int percent; // -1 表示不更新百分比
}
