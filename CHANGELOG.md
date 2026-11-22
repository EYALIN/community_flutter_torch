## 2.0.1

* First release on pub.dev.

* Android: method calls run on a background task queue instead of the main thread. The camera lookup
  (`cameraIdList` + `getCameraCharacteristics`), `setTorchMode` and the auto-off alarm are binder calls that can
  stall while the camera service is busy; on the main thread that froze the UI and risked an ANR. The queue is
  serial, so calls keep their order.
* Android: the torch camera is looked up on the first call instead of when the engine attaches (every cold
  start of every app linking the plugin paid for it), and the maximum strength level is read once instead of
  on every `switchOn` (each blink of a strobe).

## 2.0.0

* **Breaking:** the foreground service (`TorchForegroundService`) is gone, and with it the ongoing
  "Flashlight is on" notification (`setOngoingNotification`, `TorchNotificationResult`) and the native
  blink patterns (`startPattern` / `stopPattern`, `TorchFeatures.runningPattern`). An app no longer
  needs `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` or `POST_NOTIFICATIONS` for the torch.
  Run SOS / strobe in the app while it is in the foreground.
* **Breaking:** `switchOn` drops `hold`; `TorchFeatures` drops `torchService` and `ongoingNotification`.
* `switchOn(autoOffSeconds:)` now schedules an inexact `AlarmManager` alarm delivered to the opt-in
  `TorchAutoOffReceiver` (no exact-alarm permission); `getFeatures().autoOff` reports it. `switchOff`, a
  plain `switchOn` and the torch tile cancel it.
* The SOS tile opens the app through the deep link in its `com.community.flutter.torch.SOS_LINK`
  meta-data instead of blinking from a service; it shows as unavailable without one.

## 1.1.0

* SOS Quick Settings tile (`SosTileService`) and native SOS / strobe patterns in the foreground
  service (`startPattern`, `stopPattern`); `getFeatures()` reports `sosTile` and `runningPattern`.
* `switchOn` / `switchOff` and the torch tile halt a running pattern first.
* The tile services are `open` and discovered by base class (an app may declare subclasses under
  other names).

## 1.0.0

* Torch on / off, strength levels, state stream; opt-in Quick Settings tile and ongoing
  notification service.
