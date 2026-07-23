import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/speedtest_provider.dart';
import '../../theme/app_theme.dart';

/// 测速遮罩。对应 App.vue 的 <div class="overlay"> 全屏弹层。
class SpeedtestOverlay extends ConsumerWidget {
  const SpeedtestOverlay({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final speedtest = ref.watch(speedtestProvider);
    final pct = speedtest.percent > 0 ? speedtest.percent : 5;

    return Container(
      color: const Color(0xCC0A0E1C),
      alignment: Alignment.center,
      child: Container(
        constraints: const BoxConstraints(minWidth: 320),
        padding: const EdgeInsets.symmetric(horizontal: 56, vertical: 48),
        decoration: BoxDecoration(
          color: AppColors.bg2,
          border: Border.all(color: AppColors.border),
          borderRadius: BorderRadius.circular(16),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const SizedBox(
              width: 52,
              height: 52,
              child: CircularProgressIndicator(
                strokeWidth: 4,
                valueColor: AlwaysStoppedAnimation(AppColors.accent2),
              ),
            ),
            const SizedBox(height: 24),
            const Text('正在测速',
                style: TextStyle(fontSize: 22, fontWeight: FontWeight.w700)),
            const SizedBox(height: 12),
            Text(
              speedtest.progress.isEmpty ? '准备中…' : speedtest.progress,
              style: const TextStyle(fontSize: 14, color: AppColors.text2),
            ),
            const SizedBox(height: 20),
            // 进度条
            ClipRRect(
              borderRadius: BorderRadius.circular(3),
              child: LinearProgressIndicator(
                value: pct / 100,
                minHeight: 6,
                backgroundColor: AppColors.border,
                valueColor: const AlwaysStoppedAnimation(AppColors.accent2),
              ),
            ),
            const SizedBox(height: 6),
            Align(
              alignment: Alignment.centerRight,
              child: Text(
                speedtest.percent > 0 ? '${speedtest.percent}%' : '',
                style: const TextStyle(fontSize: 12, color: AppColors.text2),
              ),
            ),
            const SizedBox(height: 16),
            const Text('测速完成后自动刷新频道列表',
                style: TextStyle(fontSize: 12, color: AppColors.text2, height: 1.6)),
          ],
        ),
      ),
    );
  }
}
