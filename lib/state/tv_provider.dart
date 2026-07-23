import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../m3u/channel.dart';
import '../m3u/m3u_parser.dart';
import '../utils/app_paths.dart';
import 'player_provider.dart';

/// TV 主状态。对应 `src/stores/tv.js` (Pinia store)。
@immutable
class TvState {
  const TvState({
    this.channels = const [],
    this.groups = const [],
    this.activeGroup = '',
    this.currentChannel,
    this.isPlaying = false,
    this.playerError,
  });

  final List<Channel> channels;
  final List<String> groups;
  final String activeGroup;
  final Channel? currentChannel;
  final bool isPlaying;
  final String? playerError;

  /// 当前分组过滤后的频道 (对应 Pinia getter filteredChannels)。
  List<Channel> get filteredChannels {
    if (activeGroup.isEmpty) return channels;
    return channels.where((c) => c.group == activeGroup).toList();
  }

  /// 当前正在播放的频道所属分组 (用于分组列表的"播放中"高亮，
  /// 与 activeGroup 浏览态解耦——用户浏览别的分组时，播放中的
  /// 分组仍应保留一个区别于普通态的标记)。
  String get playingGroup => currentChannel?.group ?? '';

  TvState copyWith({
    List<Channel>? channels,
    List<String>? groups,
    String? activeGroup,
    Channel? currentChannel,
    bool? isPlaying,
    String? playerError,
    bool clearCurrent = false,
    bool clearError = false,
  }) =>
      TvState(
        channels: channels ?? this.channels,
        groups: groups ?? this.groups,
        activeGroup: activeGroup ?? this.activeGroup,
        currentChannel:
            clearCurrent ? null : (currentChannel ?? this.currentChannel),
        isPlaying: isPlaying ?? this.isPlaying,
        playerError: clearError ? null : (playerError ?? this.playerError),
      );
}

class TvNotifier extends Notifier<TvState> {
  @override
  TvState build() {
    // 启动时检查本地播放列表是否存在 (对应 onMounted 里的 has_local_source)。
    _init();
    return const TvState();
  }

  Future<void> _init() async {
    if (await AppPaths.hasLocalSource()) {
      await loadChannels();
    }
  }

  /// 加载本地频道 (对应 Pinia action loadChannels -> invoke('load_channels'))。
  Future<void> loadChannels() async {
    try {
      final file = await AppPaths.m3uPath();
      if (!file.existsSync()) return;
      final list = parseM3u(file.readAsStringSync());
      final seen = <String>{};
      final groups = <String>[];
      for (final ch in list) {
        if (seen.add(ch.group)) groups.add(ch.group);
      }
      var active = state.activeGroup;
      if (active.isEmpty && groups.isNotEmpty) active = groups.first;
      state = state.copyWith(
        channels: list,
        groups: groups,
        activeGroup: active,
        clearError: true,
      );
    } catch (e) {
      debugPrint('loadChannels: $e');
      // 之前这里只 debugPrint 静默吞掉异常，UI 上完全看不出加载失败，
      // 表现为"测速完成后频道列表一直不出现"且无任何提示。改为把错误
      // 写进 state，右侧面板会显示出来，方便排查。
      state = state.copyWith(playerError: '加载频道列表失败: $e');
    }
  }

  void setActiveGroup(String group) {
    state = state.copyWith(activeGroup: group);
  }

  /// 播放频道 (对应 Pinia playChannel -> invoke('play_url'))。
  Future<void> playChannel(Channel channel) async {
    state = state.copyWith(
      currentChannel: channel,
      isPlaying: true,
      clearError: true,
    );
    final player = ref.read(playerServiceProvider);
    await player.play(channel.url);
    if (player.error != null) {
      state = state.copyWith(playerError: player.error, isPlaying: false);
    }
  }

  /// 停止播放 (对应 stopPlayer -> invoke('stop_player'))。
  Future<void> stopPlayer() async {
    await ref.read(playerServiceProvider).stop();
    state = state.copyWith(clearCurrent: true, isPlaying: false);
  }
}

final tvProvider = NotifierProvider<TvNotifier, TvState>(TvNotifier.new);
