import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';

void main() {
  // 桌面端固定横屏、不缩放
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ProviderScope(child: MyTvApp()));
}
