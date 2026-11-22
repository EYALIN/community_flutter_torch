import 'dart:async';

import 'package:flutter/services.dart';

/// Opt-in Android extras the host app's manifest declares (all false on iOS).
class TorchFeatures {
  const TorchFeatures({
    this.qsTile = false,
    this.sosTile = false,
    this.autoOff = false,
    this.canRequestAddTile = false,
  });

  final bool qsTile;

  /// The SOS Quick Settings tile is declared with its deep link: a tap opens the app on its SOS screen.
  final bool sosTile;

  /// The auto-off receiver is declared: [CommunityFlutterTorch.switchOn]'s `autoOffSeconds` switches the
  /// torch off by an alarm, also while the app is in the background.
  final bool autoOff;
  final bool canRequestAddTile;

  static const none = TorchFeatures();
}

/// requestAddTile result codes (StatusBarManager.TILE_ADD_REQUEST_RESULT_*).
class TileAddResult {
  TileAddResult._();
  static const int notAdded = 0;
  static const int alreadyAdded = 1;
  static const int added = 2;
}

/// Native torch control: on/off, strength levels and a state stream (so the UI stays in sync when
/// the torch is switched from the Quick Settings tile or another app).
class CommunityFlutterTorch {
  CommunityFlutterTorch._();

  static const MethodChannel _channel = MethodChannel('community_flutter_torch');
  static const EventChannel _events = EventChannel('community_flutter_torch/state');

  static Stream<bool>? _state;

  /// True when the device has a usable torch (back camera flash).
  static Future<bool> isAvailable() async =>
      (await _channel.invokeMethod<bool>('isAvailable')) ?? false;

  /// Highest strength level the torch accepts. 1 means the torch cannot dim
  /// (Android before 13, or a flash unit with a single level).
  static Future<int> maxStrength() async =>
      (await _channel.invokeMethod<int>('maxStrength')) ?? 1;

  /// Switches the torch on. [strength] is 1..[maxStrength]; null means full strength. The torch stays
  /// on while the app is in the background (no service holds it).
  /// [autoOffSeconds] (Android, with the auto-off receiver declared): an alarm switches the torch off
  /// after that many seconds, also while the app is in the background (inexact: it may fire somewhat
  /// late). Every [switchOn] replaces it: one without [autoOffSeconds] cancels a scheduled off, and so
  /// does [switchOff].
  static Future<void> switchOn({int? strength, int? autoOffSeconds}) => _channel.invokeMethod<void>(
        'switchOn',
        {'strength': strength, if (autoOffSeconds != null && autoOffSeconds > 0) 'autoOffSeconds': autoOffSeconds},
      );

  static Future<void> switchOff() => _channel.invokeMethod<void>('switchOff');

  /// Emits whenever the torch turns on or off, whoever switched it.
  static Stream<bool> get onStateChanged =>
      _state ??= _events.receiveBroadcastStream().map((e) => e == true);

  /// Which opt-in extras this build has; [TorchFeatures.none] on iOS or on any error.
  static Future<TorchFeatures> getFeatures() async {
    try {
      final m = await _channel.invokeMapMethod<String, dynamic>('getFeatures');
      if (m == null) return TorchFeatures.none;
      return TorchFeatures(
        qsTile: m['qsTile'] == true,
        sosTile: m['sosTile'] == true,
        autoOff: m['autoOff'] == true,
        canRequestAddTile: m['canRequestAddTile'] == true,
      );
    } on PlatformException {
      return TorchFeatures.none;
    } on MissingPluginException {
      return TorchFeatures.none;
    }
  }

  /// Android 13+: asks the system to add the Quick Settings torch tile. Returns a [TileAddResult]
  /// code; throws a [PlatformException] when unsupported or not declared.
  static Future<int> requestAddTile() async =>
      (await _channel.invokeMethod<int>('requestAddTile')) ?? TileAddResult.notAdded;
}
