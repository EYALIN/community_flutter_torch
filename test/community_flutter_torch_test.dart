import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:community_flutter_torch/community_flutter_torch.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const method = MethodChannel('community_flutter_torch');
  const events = EventChannel('community_flutter_torch/state');
  final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  final calls = <MethodCall>[];
  Object? Function(MethodCall call) reply = (_) => null;

  setUp(() {
    calls.clear();
    reply = (_) => null;
    messenger.setMockMethodCallHandler(method, (call) async {
      calls.add(call);
      return reply(call);
    });
  });

  tearDown(() {
    messenger.setMockMethodCallHandler(method, null);
    messenger.setMockStreamHandler(events, null);
  });

  group('isAvailable', () {
    test('sends isAvailable with no arguments and returns the bool', () async {
      reply = (_) => true;
      expect(await CommunityFlutterTorch.isAvailable(), isTrue);
      expect(calls.single.method, 'isAvailable');
      expect(calls.single.arguments, isNull);
    });

    test('false from native', () async {
      reply = (_) => false;
      expect(await CommunityFlutterTorch.isAvailable(), isFalse);
    });

    test('null result becomes false', () async {
      expect(await CommunityFlutterTorch.isAvailable(), isFalse);
    });

    test('wrong type throws', () async {
      reply = (_) => 'yes';
      expect(CommunityFlutterTorch.isAvailable(), throwsA(isA<TypeError>()));
    });

    test('PlatformException propagates', () async {
      reply = (_) => throw PlatformException(code: 'X');
      expect(CommunityFlutterTorch.isAvailable(), throwsA(isA<PlatformException>()));
    });
  });

  group('maxStrength', () {
    test('returns native value', () async {
      reply = (_) => 5;
      expect(await CommunityFlutterTorch.maxStrength(), 5);
      expect(calls.single.method, 'maxStrength');
      expect(calls.single.arguments, isNull);
    });

    test('null result defaults to 1', () async {
      expect(await CommunityFlutterTorch.maxStrength(), 1);
    });

    test('wrong type throws', () async {
      reply = (_) => '5';
      expect(CommunityFlutterTorch.maxStrength(), throwsA(isA<TypeError>()));
    });

    test('PlatformException propagates', () async {
      reply = (_) => throw PlatformException(code: 'X');
      expect(CommunityFlutterTorch.maxStrength(), throwsA(isA<PlatformException>()));
    });
  });

  group('switchOn', () {
    test('defaults send a null strength and no auto-off', () async {
      await CommunityFlutterTorch.switchOn();
      expect(calls.single.method, 'switchOn');
      expect(calls.single.arguments, {'strength': null});
    });

    test('strength is forwarded', () async {
      await CommunityFlutterTorch.switchOn(strength: 3);
      expect(calls.single.arguments, {'strength': 3});
    });

    test('autoOffSeconds is forwarded when positive (schedules the alarm), left out otherwise (cancels it)', () async {
      await CommunityFlutterTorch.switchOn(autoOffSeconds: 300);
      await CommunityFlutterTorch.switchOn(autoOffSeconds: 0);
      await CommunityFlutterTorch.switchOn(autoOffSeconds: -5);
      expect(calls.map((c) => c.arguments), [
        {'strength': null, 'autoOffSeconds': 300},
        {'strength': null},
        {'strength': null},
      ]);
    });

    test('no foreground-service argument is sent any more', () async {
      await CommunityFlutterTorch.switchOn(strength: 2, autoOffSeconds: 60);
      expect((calls.single.arguments as Map).keys, isNot(contains('hold')));
    });

    test('NO_TORCH PlatformException propagates', () async {
      reply = (_) => throw PlatformException(code: 'NO_TORCH', message: 'This device has no torch');
      await expectLater(
        CommunityFlutterTorch.switchOn(),
        throwsA(isA<PlatformException>().having((e) => e.code, 'code', 'NO_TORCH')),
      );
    });

    test('TORCH_ERROR PlatformException propagates', () async {
      reply = (_) => throw PlatformException(code: 'TORCH_ERROR', message: 'boom');
      await expectLater(CommunityFlutterTorch.switchOn(strength: 1), throwsA(isA<PlatformException>()));
    });
  });

  group('switchOff', () {
    test('sends switchOff with no arguments', () async {
      await CommunityFlutterTorch.switchOff();
      expect(calls.single.method, 'switchOff');
      expect(calls.single.arguments, isNull);
    });

    test('PlatformException propagates', () async {
      reply = (_) => throw PlatformException(code: 'TORCH_ERROR');
      await expectLater(CommunityFlutterTorch.switchOff(), throwsA(isA<PlatformException>()));
    });
  });

  group('onStateChanged', () {
    test('maps events to bool and is a cached single stream', () async {
      final listens = <Object?>[];
      messenger.setMockStreamHandler(
        events,
        MockStreamHandler.inline(
          onListen: (args, sink) {
            listens.add(args);
            sink.success(true);
            sink.success(false);
            sink.success(null);
            sink.success('true');
            sink.success(1);
            sink.endOfStream();
          },
        ),
      );
      expect(identical(CommunityFlutterTorch.onStateChanged, CommunityFlutterTorch.onStateChanged), isTrue);
      final values = await CommunityFlutterTorch.onStateChanged.toList();
      // Only a real `true` counts as on.
      expect(values, [true, false, false, false, false]);
      expect(listens, hasLength(1));
    });
  });

  group('getFeatures', () {
    test('sends getFeatures and parses a full map', () async {
      reply = (_) => {'qsTile': true, 'sosTile': true, 'autoOff': true, 'canRequestAddTile': true};
      final f = await CommunityFlutterTorch.getFeatures();
      expect(calls.single.method, 'getFeatures');
      expect(calls.single.arguments, isNull);
      expect([f.qsTile, f.sosTile, f.autoOff, f.canRequestAddTile], everyElement(isTrue));
    });

    test('missing keys default to false; keys of the removed service are ignored', () async {
      reply = (_) => {'qsTile': true, 'torchService': true, 'ongoingNotification': true, 'runningPattern': 'sos'};
      final f = await CommunityFlutterTorch.getFeatures();
      expect(f.qsTile, isTrue);
      expect([f.sosTile, f.autoOff, f.canRequestAddTile], everyElement(isFalse));
    });

    test('non-bool values are false', () async {
      reply = (_) => {'qsTile': 'true', 'sosTile': 1, 'autoOff': null};
      final f = await CommunityFlutterTorch.getFeatures();
      expect([f.qsTile, f.sosTile, f.autoOff], everyElement(isFalse));
    });

    test('null result gives TorchFeatures.none', () async {
      expect(await CommunityFlutterTorch.getFeatures(), same(TorchFeatures.none));
    });

    test('PlatformException gives TorchFeatures.none', () async {
      reply = (_) => throw PlatformException(code: 'X');
      expect(await CommunityFlutterTorch.getFeatures(), same(TorchFeatures.none));
    });

    test('MissingPluginException (iOS) gives TorchFeatures.none', () async {
      messenger.setMockMethodCallHandler(method, null);
      expect(await CommunityFlutterTorch.getFeatures(), same(TorchFeatures.none));
    });

    test('TorchFeatures defaults are all false', () {
      const f = TorchFeatures();
      expect([f.qsTile, f.sosTile, f.autoOff, f.canRequestAddTile], everyElement(isFalse));
    });
  });

  group('requestAddTile', () {
    test('returns native code and sends no arguments', () async {
      reply = (_) => TileAddResult.added;
      expect(await CommunityFlutterTorch.requestAddTile(), 2);
      expect(calls.single.method, 'requestAddTile');
      expect(calls.single.arguments, isNull);
    });

    test('already added', () async {
      reply = (_) => 1;
      expect(await CommunityFlutterTorch.requestAddTile(), TileAddResult.alreadyAdded);
    });

    test('null result is notAdded', () async {
      expect(await CommunityFlutterTorch.requestAddTile(), TileAddResult.notAdded);
    });

    test('PlatformException propagates', () async {
      reply = (_) => throw PlatformException(code: 'UNSUPPORTED');
      await expectLater(CommunityFlutterTorch.requestAddTile(), throwsA(isA<PlatformException>()));
    });

    test('result codes match StatusBarManager values', () {
      expect([TileAddResult.notAdded, TileAddResult.alreadyAdded, TileAddResult.added], [0, 1, 2]);
    });
  });
}
