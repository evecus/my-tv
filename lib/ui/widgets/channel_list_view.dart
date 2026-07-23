import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/tv_provider.dart';
import '../../theme/app_theme.dart';

/// 中间频道列表。对应 App.vue 的 <section class="channel-list">。
class ChannelListView extends ConsumerWidget {
  const ChannelListView({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tv = ref.watch(tvProvider);
    final channels = tv.filteredChannels;

    if (channels.isEmpty) {
      return const Center(
        child: Text('该分组暂无频道',
            style: TextStyle(fontSize: 12, color: AppColors.text2)),
      );
    }

    return ListView.builder(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      itemCount: channels.length,
      itemBuilder: (_, i) {
        final ch = channels[i];
        final active = tv.currentChannel?.url == ch.url;
        return InkWell(
          onTap: () => ref.read(tvProvider.notifier).playChannel(ch),
          child: Container(
            margin: const EdgeInsets.only(bottom: 3),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
            decoration: BoxDecoration(
              color: active ? AppColors.bg3 : Colors.transparent,
              border: Border.all(
                color: active ? AppColors.accent : Colors.transparent,
              ),
              borderRadius: BorderRadius.circular(8),
            ),
            child: Row(
              children: [
                _Logo(logo: ch.logo),
                const SizedBox(width: 10),
                Expanded(
                  child: Text(ch.name, style: const TextStyle(fontSize: 14)),
                ),
                if (active)
                  const Text('▶', style: TextStyle(color: AppColors.green, fontSize: 11)),
              ],
            ),
          ),
        );
      },
    );
  }
}

class _Logo extends StatelessWidget {
  const _Logo({required this.logo});
  final String logo;

  @override
  Widget build(BuildContext context) {
    if (logo.isEmpty) {
      return Container(
        width: 30,
        height: 30,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: AppColors.bg3,
          borderRadius: BorderRadius.circular(4),
        ),
        child: const Text('TV',
            style: TextStyle(fontSize: 10, color: AppColors.text2, fontWeight: FontWeight.w700)),
      );
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(4),
      child: Image.network(
        logo,
        width: 30,
        height: 30,
        fit: BoxFit.contain,
        errorBuilder: (_, __, ___) => Container(
          width: 30,
          height: 30,
          color: AppColors.bg3,
        ),
      ),
    );
  }
}
