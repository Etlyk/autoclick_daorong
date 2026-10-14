package com.example.autoclick

import android.content.Intent
import android.provider.Settings
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val ch = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "autoclick")

        // Service gửi trạng thái ngược về Dart
        ClickService.onStatus = { text, sim ->
            runOnUiThread { ch.invokeMethod("status", mapOf("text" to text, "similarity" to sim)) }
        }

        ch.setMethodCallHandler { call, result ->
            when (call.method) {
                "openAccessibilitySettings" -> {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    result.success(null)
                }
                "captureReference" -> {
                    val s = ClickService.instance
                    if (s == null) result.success(false)
                    else s.captureReference(
                        call.argument("l")!!, call.argument("t")!!,
                        call.argument("w")!!, call.argument("h")!!
                    ) { ok -> runOnUiThread { result.success(ok) } }
                }
                "start" -> {
                    val s = ClickService.instance
                    if (s == null) {
                        ClickService.onStatus?.invoke("Chưa bật dịch vụ hỗ trợ tiếp cận", null)
                    } else {
                        s.start(
                            call.argument("x")!!, call.argument("y")!!,
                            call.argument("l")!!, call.argument("t")!!,
                            call.argument("w")!!, call.argument("h")!!,
                            call.argument("interval")!!, call.argument("maxClicks")!!,
                            call.argument<Double>("threshold")!!
                        )
                    }
                    result.success(null)
                }
                "stop" -> { ClickService.instance?.stop("Đã dừng thủ công"); result.success(null) }
                else -> result.notImplemented()
            }
        }
    }
}
