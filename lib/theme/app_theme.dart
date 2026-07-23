import 'package:flutter/material.dart';

/// 对应 App.vue :root 中的 CSS 变量与配色。
class AppColors {
  static const bg = Color(0xFF161B2A);
  static const bg2 = Color(0xFF1E253A);
  static const bg3 = Color(0xFF252D45);
  static const accent = Color(0xFF1976D2);
  static const accent2 = Color(0xFF42A5F5);
  static const text = Color(0xFFE8EAF6);
  static const text2 = Color(0xFF8C9ABB);
  static const border = Color(0xFF2A3454);
  static const green = Color(0xFF00C853);
  static const red = Color(0xFFEF5350);
}

class AppTheme {
  static ThemeData get dark {
    final base = ThemeData.dark(useMaterial3: true);
    return base.copyWith(
      scaffoldBackgroundColor: AppColors.bg,
      colorScheme: base.colorScheme.copyWith(
        primary: AppColors.accent,
        secondary: AppColors.accent2,
        surface: AppColors.bg2,
      ),
      textTheme: base.textTheme.apply(
        fontFamily: 'PingFang SC',
        bodyColor: AppColors.text,
        displayColor: AppColors.text,
      ),
    );
  }
}
