import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() => runApp(const MaterialApp(home: AutoClickPage()));

class AutoClickPage extends StatefulWidget {
  const AutoClickPage({super.key});
  @override
  State<AutoClickPage> createState() => _AutoClickPageState();
}

class _AutoClickPageState extends State<AutoClickPage> {
  static const _ch = MethodChannel('autoclick');

  // Toạ độ điểm click
  final x = TextEditingController(text: '540');
  final y = TextEditingController(text: '1200');
  // Vùng ảnh dùng để so sánh (left, top, width, height)
  final rl = TextEditingController(text: '400');
  final rt = TextEditingController(text: '1000');
  final rw = TextEditingController(text: '200');
  final rh = TextEditingController(text: '200');
  // Cấu hình chạy
  final interval = TextEditingController(text: '800'); // ms, tối thiểu ~500
  final maxClicks = TextEditingController(text: '500');
  final threshold = TextEditingController(text: '80'); // %

  String status = 'Chưa chạy';
  double? lastSimilarity;

  @override
  void initState() {
    super.initState();
    // Nhận trạng thái từ service Kotlin
    _ch.setMethodCallHandler((call) async {
      if (call.method == 'status') {
        setState(() {
          status = call.arguments['text'] as String;
          lastSimilarity = (call.arguments['similarity'] as num?)?.toDouble();
        });
      }
    });
  }

  int _i(TextEditingController c) => int.tryParse(c.text) ?? 0;

  Map<String, int> get _region =>
      {'l': _i(rl), 't': _i(rt), 'w': _i(rw), 'h': _i(rh)};

  Future<void> _openSettings() => _ch.invokeMethod('openAccessibilitySettings');

  Future<void> _captureReference() async {
    // Hãy mở game ở đúng trạng thái "mẫu" rồi bấm nút này (có đếm ngược 5 giây)
    setState(() => status = 'Chuyển sang game, 5 giây nữa sẽ chụp ảnh mẫu...');
    await Future.delayed(const Duration(seconds: 5));
    final ok = await _ch.invokeMethod('captureReference', _region);
    setState(() => status = ok == true ? 'Đã lưu ảnh mẫu' : 'Chụp ảnh mẫu thất bại');
  }

  Future<void> _start() async {
    await _ch.invokeMethod('start', {
      'x': _i(x),
      'y': _i(y),
      ..._region,
      'interval': _i(interval),
      'maxClicks': _i(maxClicks),
      'threshold': _i(threshold) / 100.0,
    });
  }

  Future<void> _stop() => _ch.invokeMethod('stop');

  Widget _field(String label, TextEditingController c) => SizedBox(
        width: 110,
        child: TextField(
          controller: c,
          keyboardType: TextInputType.number,
          decoration: InputDecoration(labelText: label, isDense: true),
        ),
      );

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('AutoClick + dừng theo ảnh')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        FilledButton(onPressed: _openSettings, child: const Text('1. Bật dịch vụ hỗ trợ tiếp cận')),
        const SizedBox(height: 16),
        const Text('Điểm click (px)'),
        Wrap(spacing: 8, children: [_field('x', x), _field('y', y)]),
        const SizedBox(height: 12),
        const Text('Vùng so sánh ảnh (px)'),
        Wrap(spacing: 8, runSpacing: 8, children: [
          _field('left', rl), _field('top', rt), _field('rộng', rw), _field('cao', rh),
        ]),
        const SizedBox(height: 12),
        Wrap(spacing: 8, runSpacing: 8, children: [
          _field('Chu kỳ (ms)', interval),
          _field('Tối đa lần click', maxClicks),
          _field('Ngưỡng giống (%)', threshold),
        ]),
        const SizedBox(height: 16),
        OutlinedButton(onPressed: _captureReference, child: const Text('2. Chụp ảnh mẫu từ màn hình')),
        const SizedBox(height: 8),
        Row(children: [
          Expanded(child: FilledButton(onPressed: _start, child: const Text('3. Bắt đầu'))),
          const SizedBox(width: 8),
          Expanded(child: FilledButton.tonal(onPressed: _stop, child: const Text('Dừng'))),
        ]),
        const SizedBox(height: 16),
        Text('Trạng thái: $status'),
        if (lastSimilarity != null)
          Text('Độ giống gần nhất: ${(lastSimilarity! * 100).toStringAsFixed(1)}%'),
      ]),
    );
  }
}
