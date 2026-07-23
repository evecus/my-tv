import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:vlc_player/vlc_player.dart';

import '../m3u/channel.dart';
import '../player/vlc_controller.dart';
import '../state/player_provider.dart';
import '../state/speedtest_provider.dart';
import '../state/tv_provider.dart';
import '../theme/app_theme.dart';
import 'widgets/speedtest_overlay.dart';

/// 主页面。整体布局参照 Nexus (nexus_windows) 的 IPTV 播放页：
/// 左侧大视频区（常驻，非弹窗）+ 右侧固定宽度三栏面板（分组 / 频道 / 源）。
///
/// 与 Nexus 原实现的差异（有意为之，详见对话记录）：
/// - Nexus 用 GetX + Hive + 多播放后端（VLC/MPV 可切换）+ 多源(sources)
///   管理一整套架构；本项目保持 Riverpod + 单一 VlcPlayerService，只在
///   视觉和交互层面对齐，不引入新依赖。
/// - Nexus 是"点进播放页"的独立路由，本项目是单页应用，此页面即主页面。
/// - Nexus 每个频道可能有多个源(streamUrls)，本项目的 m3u 频道模型
///   (Channel) 每个频道只有一个 url，因此"源"列只显示当前频道的
///   唯一源作为占位，为将来支持多源留出同样的视觉位置。
/// - 测速更新 / 刷新按钮按用户要求放在右侧面板顶部 header 栏（频道名旁边），
///   而非 Nexus 视频区的悬浮控制条。
class TvHomePage extends ConsumerStatefulWidget {
  const TvHomePage({super.key});

  @override
  ConsumerState<TvHomePage> createState() => _TvHomePageState();
}

class _TvHomePageState extends ConsumerState<TvHomePage> {
  final _focusNode = FocusNode();

  @override
  void dispose() {
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final speedtestRunning =
        ref.watch(speedtestProvider.select((s) => s.running));

    return KeyboardListener(
      focusNode: _focusNode,
      autofocus: true,
      onKeyEvent: (e) {
        if (e is! KeyDownEvent) return;
        if (e.logicalKey == LogicalKeyboardKey.space) {
          ref.read(playerServiceProvider).playOrPause();
        }
      },
      child: Scaffold(
        backgroundColor: Colors.black,
        body: Stack(
          children: [
            const Row(
              children: [
                Expanded(flex: 7, child: _VideoArea()),
                SizedBox(width: 360, child: _ChannelPanel()),
              ],
            ),
            if (speedtestRunning) const SpeedtestOverlay(),
          ],
        ),
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
// 左侧视频区：hover 时显示悬浮控制条 (顶部返回/频道名/LIVE, 底部播放/音量)
// ─────────────────────────────────────────────────────────────────────────

class _VideoArea extends ConsumerStatefulWidget {
  const _VideoArea();

  @override
  ConsumerState<_VideoArea> createState() => _VideoAreaState();
}

class _VideoAreaState extends ConsumerState<_VideoArea> {
  bool _showControls = true;

  void _onMouseMove() {
    if (!_showControls) setState(() => _showControls = true);
  }

  @override
  Widget build(BuildContext context) {
    final tv = ref.watch(tvProvider);
    final player = ref.watch(playerServiceProvider);
    final controller = player.controller;

    return MouseRegion(
      onHover: (_) => _onMouseMove(),
      onEnter: (_) => _onMouseMove(),
      cursor: SystemMouseCursors.basic,
      child: Stack(
        fit: StackFit.expand,
        children: [
          ColoredBox(
            color: Colors.black,
            child: controller == null
                ? const _IdlePlaceholder()
                : VlcPlayer(
                    controller: controller,
                    backgroundColor: Colors.black,
                    fit: VlcVideoFit.contain,
                  ),
          ),
          if (player.isBuffering)
            const Center(
              child: CircularProgressIndicator(color: Colors.white),
            ),
          AnimatedOpacity(
            opacity: _showControls ? 1.0 : 0.0,
            duration: const Duration(milliseconds: 220),
            child: Stack(
              children: [
                // ── 顶部：频道名 + LIVE ──────────────────────────────
                Positioned(
                  top: 0,
                  left: 0,
                  right: 0,
                  child: Container(
                    decoration: const BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.topCenter,
                        end: Alignment.bottomCenter,
                        colors: [Colors.black87, Colors.transparent],
                      ),
                    ),
                    padding: const EdgeInsets.fromLTRB(16, 14, 16, 32),
                    child: Row(
                      children: [
                        Expanded(
                          child: Text(
                            tv.currentChannel?.name ?? '未播放',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 15,
                              fontWeight: FontWeight.w600,
                            ),
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        if (tv.currentChannel != null)
                          Container(
                            padding: const EdgeInsets.symmetric(
                                horizontal: 8, vertical: 3),
                            decoration: BoxDecoration(
                              color: Colors.red,
                              borderRadius: BorderRadius.circular(3),
                            ),
                            child: const Text(
                              'LIVE',
                              style: TextStyle(
                                color: Colors.white,
                                fontWeight: FontWeight.bold,
                                fontSize: 11,
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
                // ── 底部：播放/暂停 + 音量 ───────────────────────────
                Positioned(
                  bottom: 0,
                  left: 0,
                  right: 0,
                  child: Container(
                    decoration: const BoxDecoration(
                      gradient: LinearGradient(
                        begin: Alignment.bottomCenter,
                        end: Alignment.topCenter,
                        colors: [Colors.black87, Colors.transparent],
                      ),
                    ),
                    padding: const EdgeInsets.fromLTRB(16, 32, 16, 14),
                    child: Row(
                      children: [
                        IconButton(
                          icon: Icon(
                            player.isPlaying
                                ? Icons.pause_rounded
                                : Icons.play_arrow_rounded,
                            color: Colors.white,
                            size: 28,
                          ),
                          onPressed: controller == null
                              ? null
                              : () => player.playOrPause(),
                        ),
                        const SizedBox(width: 4),
                        const Icon(Icons.volume_up,
                            color: Colors.white70, size: 18),
                        SizedBox(
                          width: 110,
                          child: SliderTheme(
                            data: SliderTheme.of(context).copyWith(
                              trackHeight: 2,
                              thumbShape: const RoundSliderThumbShape(
                                  enabledThumbRadius: 5),
                              activeTrackColor: Colors.white,
                              inactiveTrackColor: Colors.white30,
                              thumbColor: Colors.white,
                              overlayShape: SliderComponentShape.noOverlay,
                            ),
                            child: Slider(
                              value: player.volume.clamp(0, 100),
                              min: 0,
                              max: 100,
                              onChanged: (v) => player.setVolume(v),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _IdlePlaceholder extends StatelessWidget {
  const _IdlePlaceholder();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text('📺', style: TextStyle(fontSize: 48)),
          SizedBox(height: 12),
          Text(
            '点击右侧频道开始播放',
            style: TextStyle(fontSize: 13, color: Colors.white54),
          ),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
// 右侧面板：Header(频道名 + 测速更新/刷新按钮) + 三栏 body(分组/频道/源)
// ─────────────────────────────────────────────────────────────────────────

class _ChannelPanel extends ConsumerWidget {
  const _ChannelPanel();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tv = ref.watch(tvProvider);

    return Container(
      color: AppColors.bg2,
      child: Column(
        children: [
          _PanelHeader(channelName: tv.currentChannel?.name),
          if (tv.playerError != null)
            Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              color: const Color(0x33EF5350),
              child: Text(
                '⚠ ${tv.playerError}',
                style: const TextStyle(fontSize: 11, color: AppColors.red),
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          Expanded(
            child: tv.groups.isEmpty
                ? const _EmptyChannels()
                : Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: const [
                      // Column 1: 分组 (80px 固定)
                      SizedBox(width: 80, child: _GroupColumn()),
                      VerticalDivider(width: 1, color: AppColors.border),
                      // Column 2: 频道 (自适应)
                      Expanded(child: _ChannelColumn()),
                      VerticalDivider(width: 1, color: AppColors.border),
                      // Column 3: 源 (72px 固定，当前每频道只有单一源，
                      // 为将来多源扩展保留同样的视觉位置)
                      SizedBox(width: 72, child: _StreamColumn()),
                    ],
                  ),
          ),
        ],
      ),
    );
  }
}

class _PanelHeader extends ConsumerWidget {
  const _PanelHeader({required this.channelName});

  final String? channelName;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final running = ref.watch(speedtestProvider.select((s) => s.running));
    final doneMsg = ref.watch(speedtestProvider.select((s) => s.doneMsg));

    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 12, 12),
      decoration: const BoxDecoration(
        border: Border(bottom: BorderSide(color: AppColors.border)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  channelName?.isNotEmpty == true ? channelName! : '我的电视',
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: AppColors.text,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              _HeaderButton(
                label: running ? '测速中…' : '测速更新',
                spin: running,
                primary: true,
                onPressed:
                    running ? null : () => ref.read(speedtestProvider.notifier).start(),
              ),
              const SizedBox(width: 8),
              _HeaderButton(
                label: '刷新',
                onPressed: () => ref.read(tvProvider.notifier).loadChannels(),
              ),
            ],
          ),
          if (doneMsg.isNotEmpty) ...[
            const SizedBox(height: 8),
            Text(
              '✓ $doneMsg',
              style: const TextStyle(fontSize: 11, color: AppColors.green),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ],
      ),
    );
  }
}

class _HeaderButton extends StatelessWidget {
  const _HeaderButton({
    required this.label,
    this.onPressed,
    this.primary = false,
    this.spin = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final bool primary;
  final bool spin;

  @override
  Widget build(BuildContext context) {
    return ElevatedButton(
      onPressed: onPressed,
      style: ElevatedButton.styleFrom(
        backgroundColor: primary ? AppColors.accent : AppColors.bg3,
        foregroundColor: primary ? Colors.white : AppColors.text2,
        elevation: 0,
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        textStyle: const TextStyle(fontSize: 12),
        minimumSize: Size.zero,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (spin)
            const Padding(
              padding: EdgeInsets.only(right: 6),
              child: SizedBox(
                width: 12,
                height: 12,
                child: CircularProgressIndicator(
                  strokeWidth: 2,
                  valueColor: AlwaysStoppedAnimation(Colors.white),
                ),
              ),
            ),
          Text(label),
        ],
      ),
    );
  }
}

class _EmptyChannels extends StatelessWidget {
  const _EmptyChannels();

  @override
  Widget build(BuildContext context) {
    return const Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(Icons.live_tv_outlined, size: 48, color: AppColors.text2),
          SizedBox(height: 12),
          Text('暂无频道，请先测速',
              style: TextStyle(fontSize: 12, color: AppColors.text2)),
        ],
      ),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
// Column 1 — 分组
// ─────────────────────────────────────────────────────────────────────────

class _GroupColumn extends ConsumerWidget {
  const _GroupColumn();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tv = ref.watch(tvProvider);
    final groups = tv.groups;

    return ListView.builder(
      padding: const EdgeInsets.symmetric(vertical: 4),
      itemCount: groups.length,
      itemBuilder: (_, i) {
        final g = groups[i];
        final isBrowsing = tv.activeGroup == g;
        final isPlaying = tv.playingGroup == g;
        return InkWell(
          onTap: () => ref.read(tvProvider.notifier).setActiveGroup(g),
          child: AnimatedContainer(
            duration: const Duration(milliseconds: 130),
            margin: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 10),
            decoration: BoxDecoration(
              color: isBrowsing
                  ? AppColors.accent
                  : isPlaying
                      ? AppColors.accent.withValues(alpha: 0.22)
                      : Colors.transparent,
              borderRadius: BorderRadius.circular(6),
            ),
            child: Text(
              g,
              textAlign: TextAlign.center,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: TextStyle(
                fontSize: 11,
                color: isBrowsing
                    ? Colors.white
                    : isPlaying
                        ? AppColors.accent2
                        : AppColors.text,
                fontWeight:
                    (isBrowsing || isPlaying) ? FontWeight.w600 : FontWeight.normal,
              ),
            ),
          ),
        );
      },
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
// Column 2 — 频道 (当前浏览分组下的频道列表)
// ─────────────────────────────────────────────────────────────────────────

class _ChannelColumn extends ConsumerWidget {
  const _ChannelColumn();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tv = ref.watch(tvProvider);
    final list = tv.filteredChannels;

    if (list.isEmpty) {
      return const Center(
        child: Text('该分组暂无频道',
            style: TextStyle(fontSize: 12, color: AppColors.text2)),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.symmetric(vertical: 4),
      itemCount: list.length,
      itemBuilder: (_, i) {
        final ch = list[i];
        final isPlaying = tv.currentChannel?.url == ch.url;
        return _ChannelTile(
          channel: ch,
          selected: isPlaying,
          onTap: () => ref.read(tvProvider.notifier).playChannel(ch),
        );
      },
    );
  }
}

class _ChannelTile extends StatefulWidget {
  const _ChannelTile({
    required this.channel,
    required this.selected,
    required this.onTap,
  });

  final Channel channel;
  final bool selected;
  final VoidCallback onTap;

  @override
  State<_ChannelTile> createState() => _ChannelTileState();
}

class _ChannelTileState extends State<_ChannelTile> {
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    return MouseRegion(
      onEnter: (_) => setState(() => _hovered = true),
      onExit: (_) => setState(() => _hovered = false),
      child: InkWell(
        onTap: widget.onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 100),
          color: widget.selected
              ? AppColors.accent.withValues(alpha: 0.28)
              : _hovered
                  ? AppColors.bg3
                  : Colors.transparent,
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 9),
          child: Row(
            children: [
              _ChannelLogo(logo: widget.channel.logo),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  widget.channel.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight:
                        widget.selected ? FontWeight.w700 : FontWeight.normal,
                    color: widget.selected ? Colors.white : AppColors.text,
                  ),
                ),
              ),
              if (widget.selected)
                const Icon(Icons.play_arrow_rounded,
                    size: 13, color: AppColors.accent2),
            ],
          ),
        ),
      ),
    );
  }
}

class _ChannelLogo extends StatelessWidget {
  const _ChannelLogo({required this.logo});

  final String logo;

  @override
  Widget build(BuildContext context) {
    if (logo.isEmpty) {
      return const _ChannelLogoFallback();
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(3),
      child: Image.network(
        logo,
        width: 24,
        height: 24,
        fit: BoxFit.contain,
        errorBuilder: (_, __, ___) => const _ChannelLogoFallback(),
      ),
    );
  }
}

class _ChannelLogoFallback extends StatelessWidget {
  const _ChannelLogoFallback();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 24,
      height: 24,
      decoration: BoxDecoration(
        color: AppColors.accent.withValues(alpha: 0.18),
        borderRadius: BorderRadius.circular(3),
      ),
      child: const Icon(Icons.live_tv, size: 13, color: AppColors.accent2),
    );
  }
}

// ─────────────────────────────────────────────────────────────────────────
// Column 3 — 源 (当前频道的播放源；每频道目前只有单一 url，
// 显示为"源1"占位，为将来多源支持保留同样的视觉位置)
// ─────────────────────────────────────────────────────────────────────────

class _StreamColumn extends ConsumerWidget {
  const _StreamColumn();

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tv = ref.watch(tvProvider);
    final hasCurrent = tv.currentChannel != null;

    return ListView(
      padding: const EdgeInsets.symmetric(vertical: 6),
      children: [
        if (hasCurrent)
          Container(
            margin: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
            decoration: BoxDecoration(
              color: AppColors.accent,
              borderRadius: BorderRadius.circular(16),
            ),
            child: const Text(
              '源1',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w700,
                color: Colors.white,
              ),
            ),
          ),
      ],
    );
  }
}
