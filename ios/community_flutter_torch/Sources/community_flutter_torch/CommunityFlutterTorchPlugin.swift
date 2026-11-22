import AVFoundation
import Flutter
import UIKit

/// iPhone torches dim continuously; the plugin exposes them as 10 steps so the Dart side
/// treats both platforms the same way (1..maxStrength).
public class CommunityFlutterTorchPlugin: NSObject, FlutterPlugin, FlutterStreamHandler {
  private static let levels = 10
  private var sink: FlutterEventSink?
  private var observation: NSKeyValueObservation?

  public static func register(with registrar: FlutterPluginRegistrar) {
    let instance = CommunityFlutterTorchPlugin()
    let channel = FlutterMethodChannel(name: "community_flutter_torch", binaryMessenger: registrar.messenger())
    registrar.addMethodCallDelegate(instance, channel: channel)
    let events = FlutterEventChannel(name: "community_flutter_torch/state", binaryMessenger: registrar.messenger())
    events.setStreamHandler(instance)
  }

  private var device: AVCaptureDevice? {
    guard let d = AVCaptureDevice.default(for: .video), d.hasTorch else { return nil }
    return d
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "isAvailable":
      result(device?.isTorchAvailable ?? false)
    case "maxStrength":
      result(device == nil ? 1 : CommunityFlutterTorchPlugin.levels)
    case "switchOn":
      guard let d = device else {
        result(FlutterError(code: "NO_TORCH", message: "This device has no torch", details: nil))
        return
      }
      let args = call.arguments as? [String: Any]
      let strength = args?["strength"] as? Int
      do {
        try d.lockForConfiguration()
        defer { d.unlockForConfiguration() }
        if let s = strength {
          let level = Float(min(max(s, 1), CommunityFlutterTorchPlugin.levels)) / Float(CommunityFlutterTorchPlugin.levels)
          try d.setTorchModeOn(level: level)
        } else {
          try d.setTorchModeOn(level: AVCaptureDevice.maxAvailableTorchLevel)
        }
        result(nil)
      } catch {
        result(FlutterError(code: "TORCH_ERROR", message: error.localizedDescription, details: nil))
      }
    case "switchOff":
      guard let d = device else { result(nil); return }
      do {
        try d.lockForConfiguration()
        d.torchMode = .off
        d.unlockForConfiguration()
        result(nil)
      } catch {
        result(FlutterError(code: "TORCH_ERROR", message: error.localizedDescription, details: nil))
      }
    default:
      result(FlutterMethodNotImplemented)
    }
  }

  public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
    sink = events
    observation = device?.observe(\.isTorchActive, options: [.new]) { [weak self] d, _ in
      DispatchQueue.main.async { self?.sink?(d.isTorchActive) }
    }
    return nil
  }

  public func onCancel(withArguments arguments: Any?) -> FlutterError? {
    observation?.invalidate()
    observation = nil
    sink = nil
    return nil
  }
}
