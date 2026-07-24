import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:window_manager/window_manager.dart';

import 'app.dart';

void main() async {
  // 桌面端固定横屏、不缩放
  WidgetsFlutterBinding.ensureInitialized();
  // window_manager 要求在 runApp 前完成初始化，否则后续
  // windowManager.setFullScreen() 等调用会抛异常。
  await windowManager.ensureInitialized();
  runApp(const ProviderScope(child: MyTvApp()));
}
