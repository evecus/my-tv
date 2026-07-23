import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../state/speedtest_provider.dart';
import '../state/tv_provider.dart';
import '../theme/app_theme.dart';
import 'widgets/channel_list_view.dart';
import 'widgets/player_panel.dart';
import 'widgets/sidebar.dart';
import 'widgets/speedtest_overlay.dart';
import 'widgets/topbar.dart';

/// 主页面。对应 `src/App.vue` 的整体布局。
class HomePage extends ConsumerWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final speedtestRunning = ref.watch(speedtestProvider.select((s) => s.running));

    return Scaffold(
      body: Stack(
        children: [
          Column(
            children: [
              const TopBar(),
              Expanded(
                child: Row(
                  children: const [
                    Sidebar(),
                    Expanded(child: ChannelListView()),
                    PlayerPanel(),
                  ],
                ),
              ),
            ],
          ),
          // 测速遮罩 (对应 App.vue 的 <Transition> overlay)
          if (speedtestRunning) const SpeedtestOverlay(),
        ],
      ),
    );
  }
}

/// 通用按钮样式, 复刻 App.vue 的 .btn 样式。
class AppButton extends StatelessWidget {
  const AppButton({
    super.key,
    required this.label,
    this.onPressed,
    this.kind = AppButtonKind.primary,
    this.spin = false,
  });

  final String label;
  final VoidCallback? onPressed;
  final AppButtonKind kind;
  final bool spin;

  @override
  Widget build(BuildContext context) {
    final Color bg;
    final Color fg;
    switch (kind) {
      case AppButtonKind.primary:
        bg = AppColors.accent;
        fg = Colors.white;
        break;
      case AppButtonKind.ghost:
        bg = AppColors.bg3;
        fg = AppColors.text2;
        break;
      case AppButtonKind.danger:
        bg = const Color(0xFFB71C1C);
        fg = Colors.white;
        break;
    }
    return ElevatedButton(
      onPressed: onPressed,
      style: ElevatedButton.styleFrom(
        backgroundColor: bg,
        foregroundColor: fg,
        elevation: 0,
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        textStyle: const TextStyle(fontSize: 13),
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

enum AppButtonKind { primary, ghost, danger }
