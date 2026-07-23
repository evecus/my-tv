import 'package:flutter/material.dart';

import 'theme/app_theme.dart';
import 'ui/tv_home_page.dart';

class MyTvApp extends StatelessWidget {
  const MyTvApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '我的电视',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark,
      home: const TvHomePage(),
    );
  }
}
