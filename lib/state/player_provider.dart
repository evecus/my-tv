import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../player/vlc_controller.dart';

/// 单例播放器服务。UI 通过 ref.watch 拿 controller 渲染 VlcPlayer。
final playerServiceProvider =
    ChangeNotifierProvider<VlcPlayerService>((ref) {
  final svc = VlcPlayerService();
  ref.onDispose(svc.dispose);
  return svc;
});
