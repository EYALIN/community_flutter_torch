import 'dart:async';

import 'package:community_flutter_torch/community_flutter_torch.dart';
import 'package:flutter/material.dart';

void main() => runApp(const MaterialApp(home: TorchPage()));

class TorchPage extends StatefulWidget {
  const TorchPage({super.key});

  @override
  State<TorchPage> createState() => _TorchPageState();
}

class _TorchPageState extends State<TorchPage> {
  bool _available = false;
  bool _on = false;
  int _max = 1;
  double _strength = 1;
  StreamSubscription<bool>? _sub;

  @override
  void initState() {
    super.initState();
    _sub = CommunityFlutterTorch.onStateChanged.listen((on) => setState(() => _on = on));
    _load();
  }

  Future<void> _load() async {
    final available = await CommunityFlutterTorch.isAvailable();
    final max = available ? await CommunityFlutterTorch.maxStrength() : 1;
    if (!mounted) return;
    setState(() {
      _available = available;
      _max = max;
      _strength = max.toDouble();
    });
  }

  Future<void> _toggle() async {
    if (_on) {
      await CommunityFlutterTorch.switchOff();
    } else {
      await CommunityFlutterTorch.switchOn(strength: _strength.round());
    }
  }

  @override
  void dispose() {
    _sub?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('community_flutter_torch')),
      body: Center(
        child: !_available
            ? const Text('No torch on this device')
            : Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  IconButton(
                    iconSize: 96,
                    icon: Icon(_on ? Icons.flashlight_on : Icons.flashlight_off),
                    onPressed: _toggle,
                  ),
                  Text(_on ? 'On' : 'Off'),
                  if (_max > 1)
                    Slider(
                      min: 1,
                      max: _max.toDouble(),
                      divisions: _max - 1,
                      value: _strength,
                      label: '${_strength.round()} / $_max',
                      onChanged: (v) => setState(() => _strength = v),
                      onChangeEnd: (v) {
                        if (_on) CommunityFlutterTorch.switchOn(strength: v.round());
                      },
                    ),
                ],
              ),
      ),
    );
  }
}
