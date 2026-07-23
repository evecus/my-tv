import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../speedtest/config.dart';
import '../speedtest/speedtest_runner.dart';
import '../speedtest/types.dart';
import '../utils/app_paths.dart';
import 'tv_provider.dart';

/// 测速状态。对应 `src/stores/tv.js` 中 speedtest 相关字段 + listen('speedtest://*')。
class SpeedtestState {
  const SpeedtestState({
    this.running = false,
    this.progress = '',
    this.percent = 0,
    this.error,
    this.doneMsg = '',
  });

  final bool running;
  final String progress; // 阶段文本
  final int percent;
  final String? error;
  final String doneMsg;

  SpeedtestState copyWith({
    bool? running,
    String? progress,
    int? percent,
    String? error,
    String? doneMsg,
    bool keepPercent = false,
  }) =>
      SpeedtestState(
        running: running ?? this.running,
        progress: progress ?? this.progress,
        percent: keepPercent ? this.percent : (percent ?? this.percent),
        error: error,
        doneMsg: doneMsg ?? this.doneMsg,
      );
}

class SpeedtestNotifier extends Notifier<SpeedtestState> {
  @override
  SpeedtestState build() => const SpeedtestState();

  /// 启动测速 (对应 Pinia startSpeedtest -> invoke('start_speedtest', {workers:60, top:10}))。
  Future<void> start() async {
    state = const SpeedtestState(
      running: true,
      progress: '准备中…',
      percent: 0,
    );

    try {
      final outputPath = await AppPaths.m3uPath();
      await runSpeedtest(
        workers: SpeedtestConfig.defaultWorkers,
        topN: SpeedtestConfig.defaultTop,
        urls: const [SpeedtestConfig.defaultSubUrl],
        outputPath: outputPath,
        onProgress: _onProgress,
      ).then((count) {
        if (count == 0) {
          state = state.copyWith(
            running: false,
            error: '未找到可用频道，请检查网络后重试',
          );
        } else {
          state = SpeedtestState(
            running: false,
            percent: 100,
            doneMsg: '测速完成，共 $count 个频道',
          );
          // 测速完成后自动刷新频道列表 (对应 listen('speedtest://done') 里的 loadChannels)
          ref.read(tvProvider.notifier).loadChannels();
        }
      });
    } catch (e) {
      debugPrint('speedtest: $e');
      state = state.copyWith(running: false, error: '$e');
    }
  }

  void _onProgress(SpeedtestProgress p) {
    state = state.copyWith(
      progress: p.phase,
      percent: p.percent,
      keepPercent: p.percent < 0,
    );
  }
}

final speedtestProvider =
    NotifierProvider<SpeedtestNotifier, SpeedtestState>(SpeedtestNotifier.new);
