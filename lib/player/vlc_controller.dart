import 'package:flutter/foundation.dart';
import 'package:vlc_player/vlc_player.dart';

/// vlc_player 控制器封装。对应 Rust `src-tauri/src/player.rs` (libmpv 等价物)。
///
/// API 已对照真实的 vlc_player ^2.1.2 包校准（参考 windows 平台的
/// player_shared 实现 lib/player/vlc_backend.dart）：
///   - VlcPlayerController 不是无参构造，需要在构造时/切换媒体时传入
///     VlcMediaSource(uri: ..., mediaOptions: [...])。
///   - 没有独立的 openMedia() 方法，播放新地址用 setMedia()（或直接
///     重建 controller，本实现采用后者：每次 play() 都是一次新的直播/
///     媒体切换，重建 controller 更符合 IPTV 换台的语义，并避免旧
///     controller 状态残留）。
///   - controller.value.state 是 VlcPlaybackState 枚举，没有内建的
///     isPlaying getter。
///   - dispose() 是同步方法（继承自 ValueNotifier），不是 Future。
///
/// 对应原 libmpv 参数:
///   vo=gpu, hwdec=auto, cache=yes, demuxer-max-bytes=50MiB,
///   cache-secs=10, keep-open=yes
/// vlc 等价: 通过 mediaOptions 传 --network-caching=<ms> 等原生参数。
class VlcPlayerService extends ChangeNotifier {
  VlcPlayerController? _controller;
  bool _isPlaying = false;
  String? _error;
  bool _disposed = false;

  /// 当前的 controller。为 null 表示尚未开始播放（初始态或已 stop）。
  /// UI 层需要判空后再传给 VlcPlayer widget。
  VlcPlayerController? get controller => _controller;
  bool get isPlaying => _isPlaying;
  String? get error => _error;

  /// 播放指定 URL。对应 Rust `player::play`。
  ///
  /// IPTV 换台等同于加载新媒体源，这里销毁旧 controller 并创建新的，
  /// 而不是复用同一个 controller 调用 setMedia —— 这样可以避免旧直播流
  /// 的解码/网络状态残留到新台。
  Future<void> play(String url) async {
    if (_disposed) return;
    _error = null;

    final Uri uri;
    try {
      uri = Uri.parse(url);
    } catch (e) {
      _error = '无效的播放地址: $e';
      notifyListeners();
      return;
    }

    // 销毁旧 controller
    final old = _controller;
    if (old != null) {
      old.removeListener(_onChanged);
      old.dispose();
      _controller = null;
    }

    // IPTV 直播流优化参数：
    // :network-caching=1500 —— 默认 1000ms 太短，抖动时容易卡顿；
    //   太长会增加直播延迟，1500ms 是平衡值。
    final mediaOptions = <String>[':network-caching=1500'];

    final source = VlcMediaSource(uri: uri, mediaOptions: mediaOptions);

    try {
      final next = VlcPlayerController(mediaSource: source, autoPlay: true);
      _controller = next;
      next.addListener(_onChanged);
      // 保险起见显式 setMedia 一次，参考实现里说明构造期传入的
      // mediaSource 会在 controller attach 到 VlcPlayer widget 时应用，
      // 这里再次调用确保加载成功。
      await next.setMedia(source, autoPlay: true);
      if (_disposed) return;
      _isPlaying = true;
      notifyListeners();
    } catch (e) {
      _error = '$e';
      _isPlaying = false;
      notifyListeners();
    }
  }

  void _onChanged() {
    final c = _controller;
    if (c == null) return;
    final playing = c.value.state == VlcPlaybackState.playing;
    if (playing != _isPlaying) {
      _isPlaying = playing;
      notifyListeners();
    }
  }

  /// 停止播放。对应 Rust `player::stop`。
  ///
  /// vlc_player 的 VlcPlayerController 没有独立的 stop() 方法，通过
  /// 销毁 controller 并置空来实现"停止播放并释放资源"的语义。
  Future<void> stop() async {
    final c = _controller;
    if (c != null) {
      c.removeListener(_onChanged);
      c.dispose();
      _controller = null;
    }
    _isPlaying = false;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    final c = _controller;
    if (c != null) {
      c.removeListener(_onChanged);
      c.dispose();
      _controller = null;
    }
    super.dispose();
  }
}
