import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../state/tv_provider.dart';
import '../../theme/app_theme.dart';

/// 左侧分组列表。对应 App.vue 的 <nav class="sidebar">。
class Sidebar extends ConsumerWidget {
  const Sidebar({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tv = ref.watch(tvProvider);
    return Container(
      width: 130,
      color: AppColors.bg2,
      child: tv.groups.isEmpty
          ? const Center(
              child: Padding(
                padding: EdgeInsets.symmetric(horizontal: 12),
                child: Text(
                  '暂无频道\n请先测速',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 12, color: AppColors.text2, height: 1.8),
                ),
              ),
            )
          : ListView.builder(
              itemCount: tv.groups.length,
              itemBuilder: (_, i) {
                final g = tv.groups[i];
                final active = tv.activeGroup == g;
                return InkWell(
                  onTap: () => ref.read(tvProvider.notifier).setActiveGroup(g),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    decoration: BoxDecoration(
                      color: active ? AppColors.bg3 : Colors.transparent,
                      border: Border(
                        left: BorderSide(
                          width: 3,
                          color: active ? AppColors.accent2 : Colors.transparent,
                        ),
                      ),
                    ),
                    child: Text(g,
                        style: TextStyle(
                          fontSize: 13,
                          color: active ? AppColors.text : AppColors.text2,
                        )),
                  ),
                );
              },
            ),
    );
  }
}
