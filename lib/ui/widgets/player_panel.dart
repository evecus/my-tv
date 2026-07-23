import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:vlc_player/vlc_player.dart';

import '../../state/player_provider.dart';
import '../../state/speedtest_provider.dart';
import '../../state/tv_provider.dart';
import '../../theme/app_theme.dart';
import '../home_page.dart';

/// 右侧面板 (应用内嵌播放区)。对应 App.vue 的 <aside class="panel">。
/// 用户选择"应用内嵌播放", 故播放画面嵌入此面板而非独立窗口。
class PlayerPanel extends ConsumerWidget {
  const PlayerPanel({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tv = ref.watch(tvProvider);
    final speedtest = ref.watch(speedtestProvider);

    return Container(
      width: 190,
      color: AppColors.bg2,
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // 正在播放 / 测速中 / 空闲
          if (tv.currentChannel != null) ...[
            const Text('正在播放',
                style: TextStyle(
                    fontSize: 11,
                    color: AppColors.text2,
                    letterSpacing: 1)),
            const SizedBox(height: 8),
            // 内嵌播放器
            Expanded(
              child: ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: Builder(builder: (context) {
                  final controller =
                      ref.watch(playerServiceProvider).controller;
                  if (controller == null) {
                    return const Center(
                      child: CircularProgressIndicator(strokeWidth: 2),
                    );
                  }
                  return VlcPlayer(
                    controller: controller,
                    backgroundColor: Colors.black,
                    fit: VlcVideoFit.contain,
                  );
                }),
              ),
            ),
            const SizedBox(height: 12),
            Text(tv.currentChannel!.name,
                style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700)),
            const SizedBox(height: 4),
            Text(tv.currentChannel!.group,
                style: const TextStyle(fontSize: 12, color: AppColors.text2)),
            const SizedBox(height: 16),
            AppButton(
              label: '■ 停止播放',
              kind: AppButtonKind.danger,
              onPressed: () => ref.read(tvProvider.notifier).stopPlayer(),
            ),
          ] else if (speedtest.running) ...[
            const SizedBox(height: 28),
            const Center(child: _Spinner()),
            const SizedBox(height: 8),
            Text(speedtest.progress,
                textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 12, color: AppColors.text2)),
            const SizedBox(height: 4),
            const Text('测速完成后自动刷新',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 12, color: AppColors.text2)),
          ] else ...[
            const SizedBox(height: 32),
            const Text('📺', textAlign: TextAlign.center, style: TextStyle(fontSize: 36)),
            const SizedBox(height: 8),
            const Text('点击频道开始播放',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 12, color: AppColors.text2)),
          ],
          if (tv.playerError != null)
            Padding(
              padding: const EdgeInsets.only(top: 16),
              child: Text('⚠ ${tv.playerError}',
                  style: const TextStyle(fontSize: 12, color: AppColors.red, height: 1.6)),
            ),
          if (speedtest.error != null)
            Padding(
              padding: const EdgeInsets.only(top: 16),
              child: Text('⚠ ${speedtest.error}',
                  style: const TextStyle(fontSize: 12, color: AppColors.red, height: 1.6)),
            ),
        ],
      ),
    );
  }
}

class _Spinner extends StatelessWidget {
  const _Spinner();

  @override
  Widget build(BuildContext context) {
    return const SizedBox(
      width: 32,
      height: 32,
      child: CircularProgressIndicator(
        strokeWidth: 3,
        valueColor: AlwaysStoppedAnimation(AppColors.accent2),
      ),
    );
  }
}
