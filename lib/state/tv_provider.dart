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
    this.currentStreamUrls = const [],
    this.currentStreamIndex = 0,
    this.isPlaying = false,
    this.playerError,
  });

  final List<Channel> channels;
  final List<String> groups;
  final String activeGroup;
  final Channel? currentChannel;

  /// 当前播放频道的全部源地址 (同名频道去重后的 url 集合)。
  /// 对应 Nexus IptvPlayerController.streamUrls：多个 m3u 条目如果
  /// 频道名相同 (如多个来源都叫 "CCTV1")，视为同一个频道的不同源，
  /// 而不是重复的频道条目。
  final List<String> currentStreamUrls;

  /// 当前选中的源下标，对应右侧"源"列的高亮项。
  final int currentStreamIndex;
  final bool isPlaying;
  final String? playerError;

  /// 当前分组过滤后的频道 (对应 Pinia getter filteredChannels)。
  ///
  /// 按频道名去重——m3u 里经常出现多条同名频道 (同一频道的不同源/
  /// 不同清晰度)，这些不应该在频道列表里重复显示成多行，而是合并为
  /// 一个频道条目，选中后在右侧"源"列展示全部可选源。
  /// 对应 Nexus `browsedChannels` 里 `seen.add(c.name)` 的去重逻辑。
  List<Channel> get filteredChannels {
    final scoped =
        activeGroup.isEmpty ? channels : channels.where((c) => c.group == activeGroup);
    final seen = <String>{};
    return scoped.where((c) => seen.add(c.name)).toList();
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
    List<String>? currentStreamUrls,
    int? currentStreamIndex,
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
        currentStreamUrls: clearCurrent
            ? const []
            : (currentStreamUrls ?? this.currentStreamUrls),
        currentStreamIndex:
            clearCurrent ? 0 : (currentStreamIndex ?? this.currentStreamIndex),
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
  ///
  /// 会先按频道名从全量 channels 里收集所有同名条目的 url 作为
  /// "源" 列表 (对应 Nexus selectChannel 里的去重收集逻辑)，默认播放
  /// 第一个源。
  Future<void> playChannel(Channel channel) async {
    final urls = state.channels
        .where((c) => c.name == channel.name)
        .map((c) => c.url)
        .toSet()
        .toList();
    final streamUrls = urls.isNotEmpty ? urls : [channel.url];

    state = state.copyWith(
      currentChannel: channel,
      currentStreamUrls: streamUrls,
      currentStreamIndex: 0,
      isPlaying: true,
      clearError: true,
    );
    final player = ref.read(playerServiceProvider);
    await player.play(streamUrls.first);
    if (player.error != null) {
      state = state.copyWith(playerError: player.error, isPlaying: false);
    }
  }

  /// 切换到当前频道的某个源 (对应右侧"源"列点击)。
  /// 不改变 currentChannel/分组/频道列表的选中状态，只切换实际播放的 url。
  Future<void> selectStream(int index) async {
    if (index < 0 || index >= state.currentStreamUrls.length) return;
    if (index == state.currentStreamIndex) return;

    state = state.copyWith(currentStreamIndex: index, clearError: true);
    final player = ref.read(playerServiceProvider);
    await player.play(state.currentStreamUrls[index]);
    if (player.error != null) {
      state = state.copyWith(playerError: player.error);
    }
  }

  /// 停止播放 (对应 stopPlayer -> invoke('stop_player'))。
  Future<void> stopPlayer() async {
    await ref.read(playerServiceProvider).stop();
    state = state.copyWith(clearCurrent: true, isPlaying: false);
  }
}

final tvProvider = NotifierProvider<TvNotifier, TvState>(TvNotifier.new);
