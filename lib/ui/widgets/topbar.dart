import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/speedtest_provider.dart';
import '../../state/tv_provider.dart';
import '../../theme/app_theme.dart';
import '../home_page.dart';

/// 顶部栏。对应 App.vue 的 <header class="topbar">。
class TopBar extends ConsumerWidget {
  const TopBar({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final doneMsg = ref.watch(speedtestProvider.select((s) => s.doneMsg));
    final running = ref.watch(speedtestProvider.select((s) => s.running));

    return Container(
      height: 52,
      padding: const EdgeInsets.symmetric(horizontal: 20),
      color: AppColors.bg2,
      child: Row(
        children: [
          const Text('📺 我的电视',
              style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
          const Spacer(),
          if (doneMsg.isNotEmpty)
            Container(
              margin: const EdgeInsets.only(right: 10),
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
              decoration: BoxDecoration(
                color: const Color(0x1A00C853),
                border: Border.all(color: const Color(0x4D00C853)),
                borderRadius: BorderRadius.circular(20),
              ),
              child: Text('✓ $doneMsg',
                  style: const TextStyle(fontSize: 12, color: AppColors.green)),
            ),
          AppButton(
            label: running ? '测速中…' : '测速更新',
            spin: running,
            onPressed: running
                ? null
                : () => ref.read(speedtestProvider.notifier).start(),
          ),
          const SizedBox(width: 10),
          AppButton(
            label: '刷新',
            kind: AppButtonKind.ghost,
            onPressed: () => ref.read(tvProvider.notifier).loadChannels(),
          ),
        ],
      ),
    );
  }
}
