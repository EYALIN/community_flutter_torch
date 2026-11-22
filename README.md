# community_flutter_torch

Torch control for Flutter apps: on/off, strength levels and a state stream.

<!-- support:top:start -->
## ❤️ Support this plugin

I build and maintain this plugin, and [a whole family of free Flutter plugins](https://github.com/EYALIN?tab=repositories&q=community_flutter), in my free time. Every Android and iOS release changes something underneath them (new SDK levels, Swift Package Manager, privacy manifests, OEM quirks), and keeping them working takes hours every month.

**If this plugin saved you from writing the platform code yourself, or it ships in an app that earns money, please sponsor its development.** Even $5 a month makes a real difference. It decides how fast issues get fixed, how quickly the plugin supports each new Android and iOS release, and which features come next. Issues and feature requests from sponsors are looked at first.

[![Sponsor Me](https://img.shields.io/static/v1?label=Sponsor%20Me&style=for-the-badge&message=%E2%9D%A4&logo=GitHub&color=%23fe8e86)](https://github.com/sponsors/EYALIN)

Not able to sponsor? A ⭐ on [GitHub](https://github.com/EYALIN) and a 👍 on pub.dev help other developers find it. Thank you!
<!-- support:top:end -->

- `isAvailable()`, `maxStrength()` (1 = cannot dim), `switchOn(strength:)`, `switchOff()`
- `onStateChanged` stream — Android `TorchCallback`, iOS `isTorchActive` KVO.

Android dims on API 33+ (`turnOnTorchWithStrengthLevel`); iOS exposes 10 steps.

| | Android | iOS |
|---|---|---|
| Minimum | API 24 | iOS 15 |
| On / off, state stream | yes | yes |
| Strength levels | API 33+ | yes (10) |
| Quick Settings tiles, timed auto-off | opt-in | — |

## Install

```sh
flutter pub add community_flutter_torch
```

No permission is needed on either platform (the torch is not the camera).

## Usage

```dart
import 'package:community_flutter_torch/community_flutter_torch.dart';

if (await CommunityFlutterTorch.isAvailable()) {
  final max = await CommunityFlutterTorch.maxStrength();
  await CommunityFlutterTorch.switchOn(strength: max > 1 ? (max / 2).ceil() : null);
}

final sub = CommunityFlutterTorch.onStateChanged.listen((on) => print('torch on: $on'));

await CommunityFlutterTorch.switchOff();
await sub.cancel();
```

See [example/lib/main.dart](example/lib/main.dart) for a complete app.

## No foreground service

The plugin runs no service and declares no permission. `CameraManager.setTorchMode` keeps the torch on
while the app is in the background, so an app using this plugin needs no `FOREGROUND_SERVICE*`
permission and no Play "Foreground service permissions" declaration. Android switches the torch off
if the app's process dies.

Blink patterns (SOS, strobe, Morse) are the app's job: run them while the app is in the foreground and
stop them when it goes to the background.

## Android extras (opt-in per app)

`getFeatures()` and `requestAddTile()` report and use only what the host app's manifest declares,
same opt-in model as the Cordova plugin:

```xml
<service android:name="com.community.flutter.torch.TorchTileService" android:exported="true"
    android:icon="@drawable/flashlight_ic_torch" android:label="@string/flashlight_tile_label"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
  <intent-filter><action android:name="android.service.quicksettings.action.QS_TILE" /></intent-filter>
  <meta-data android:name="android.service.quicksettings.TOGGLEABLE_TILE" android:value="true" />
</service>
```

The torch tile switches the torch on / off natively; it also cancels a scheduled auto-off.

### Timed auto-off

`switchOn(autoOffSeconds: n)` schedules an inexact `AlarmManager` alarm (`setAndAllowWhileIdle`, so no
`SCHEDULE_EXACT_ALARM`) that switches the torch off after `n` seconds, also while the app is in the
background. It may fire somewhat late (Android batches inexact alarms; in Doze at most about one per
9 minutes per app), so keep a precise in-app countdown as well. A later `switchOn` without it,
`switchOff`, or a tap on the torch tile cancels it. It needs the receiver:

```xml
<receiver android:name="com.community.flutter.torch.TorchAutoOffReceiver" android:exported="false" />
```

`getFeatures().autoOff` is true when it is declared; without it `autoOffSeconds` is ignored.

### SOS tile

A tap on the SOS tile opens the app on its SOS screen through a deep link the app names; the app runs
the pattern in the foreground. Without a torch, without the link, or when no activity of the app
handles it, the tile shows as unavailable. `getFeatures().sosTile` is true when it is declared with its
link:

```xml
<service android:name="com.community.flutter.torch.SosTileService" android:exported="true"
    android:icon="@drawable/flashlight_ic_sos" android:label="@string/flashlight_sos_tile_label"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
  <intent-filter><action android:name="android.service.quicksettings.action.QS_TILE" /></intent-filter>
  <meta-data android:name="com.community.flutter.torch.SOS_LINK" android:value="myapp://torch?screen=sos" />
</service>
```

### Keeping tiles across a migration

Android keeps a tile the user added only while its service name is unchanged. `TorchTileService`,
`SosTileService` and `TorchAutoOffReceiver` are `open` and found by base class, so an app moving from
another plugin declares one-line subclasses under the old names (e.g. `class TorchTileService :
com.community.flutter.torch.TorchTileService()` in the old package) instead of the classes above.

iOS has none of these extras.

## License

MIT — see [LICENSE](LICENSE). Issues and pull requests are welcome at
[github.com/EYALIN/community_flutter_torch](https://github.com/EYALIN/community_flutter_torch/issues).

<!-- support:bottom:start -->
## Support

If this plugin is useful to you, please [sponsor its development](https://github.com/sponsors/EYALIN). It keeps the plugin maintained for every new Android and iOS release.
<!-- support:bottom:end -->
