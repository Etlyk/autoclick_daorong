package com.example.autoclick

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.io.File
import kotlin.math.abs

class ClickService : AccessibilityService() {

    companion object {
        var instance: ClickService? = null
        var onStatus: ((String, Double?) -> Unit)? = null
        private const val SAMPLE = 32          // so sánh ở độ phân giải 32x32 cho nhanh & bớt nhiễu
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var clicks = 0

    // cấu hình
    private var cx = 0; private var cy = 0
    private var rl = 0; private var rt = 0; private var rw = 0; private var rh = 0
    private var interval = 800L
    private var maxClicks = 500
    private var threshold = 0.8

    private var reference: IntArray? = null   // ảnh mẫu 32x32 xám

    override fun onServiceConnected() { instance = this; onStatus?.invoke("Dịch vụ đã sẵn sàng", null) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() { stop("Bị gián đoạn") }
    override fun onDestroy() { instance = null; super.onDestroy() }

    // ---------- Chụp ảnh mẫu ----------
    fun captureReference(l: Int, t: Int, w: Int, h: Int, done: (Boolean) -> Unit) {
        screenshot { bmp ->
            if (bmp == null) { done(false); return@screenshot }
            val gray = toGray(bmp, l, t, w, h)
            reference = gray
            File(filesDir, "ref.bin").writeBytes(ByteArray(gray.size) { gray[it].toByte() })
            done(true)
        }
    }

    private fun loadReference(): IntArray? {
        reference?.let { return it }
        val f = File(filesDir, "ref.bin")
        if (!f.exists()) return null
        return f.readBytes().map { it.toInt() and 0xFF }.toIntArray().also { reference = it }
    }

    // ---------- Vòng lặp chính ----------
    fun start(x: Int, y: Int, l: Int, t: Int, w: Int, h: Int, intervalMs: Int, max: Int, thr: Double) {
        if (running) return
        val ref = loadReference()
        if (ref == null) { onStatus?.invoke("Chưa có ảnh mẫu", null); return }
        cx = x; cy = y; rl = l; rt = t; rw = w; rh = h
        interval = intervalMs.coerceAtLeast(500).toLong()   // takeScreenshot bị giới hạn tần suất
        maxClicks = max; threshold = thr
        clicks = 0; running = true
        onStatus?.invoke("Đang chạy...", null)
        tick()
    }

    fun stop(reason: String) {
        running = false
        handler.removeCallbacksAndMessages(null)
        onStatus?.invoke("$reason (đã click $clicks lần)", null)
    }

    private fun tick() {
        if (!running) return
        screenshot { bmp ->
            if (!running) return@screenshot
            if (bmp == null) { handler.postDelayed({ tick() }, interval); return@screenshot }

            val sim = similarity(loadReference()!!, toGray(bmp, rl, rt, rw, rh))
            onStatus?.invoke("Đang chạy... ($clicks lần)", sim)

            if (sim < threshold) {                       // ảnh khác mẫu quá ngưỡng -> dừng
                stop("Dừng: độ giống ${"%.1f".format(sim * 100)}% < ${(threshold * 100).toInt()}%")
                return@screenshot
            }
            tap(cx, cy)
            clicks++
            if (clicks >= maxClicks) { stop("Dừng: đạt số lần tối đa"); return@screenshot }
            handler.postDelayed({ tick() }, interval)
        }
    }

    // ---------- Tiện ích ----------
    private fun tap(x: Int, y: Int) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50)).build()
        dispatchGesture(g, null, null)
    }

    private fun screenshot(cb: (Bitmap?) -> Unit) {
        takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(r: ScreenshotResult) {
                val hw = Bitmap.wrapHardwareBuffer(r.hardwareBuffer, r.colorSpace)
                val soft = hw?.copy(Bitmap.Config.ARGB_8888, false)
                r.hardwareBuffer.close()
                cb(soft)
            }
            override fun onFailure(errorCode: Int) = cb(null)
        })
    }

    // Cắt vùng, thu nhỏ về 32x32, đổi sang thang xám
    private fun toGray(src: Bitmap, l: Int, t: Int, w: Int, h: Int): IntArray {
        val left = l.coerceIn(0, src.width - 1); val top = t.coerceIn(0, src.height - 1)
        val ww = w.coerceIn(1, src.width - left); val hh = h.coerceIn(1, src.height - top)
        val crop = Bitmap.createBitmap(src, left, top, ww, hh)
        val small = Bitmap.createScaledBitmap(crop, SAMPLE, SAMPLE, true)
        return IntArray(SAMPLE * SAMPLE) {
            val p = small.getPixel(it % SAMPLE, it / SAMPLE)
            (Color.red(p) * 299 + Color.green(p) * 587 + Color.blue(p) * 114) / 1000
        }
    }

    // 1.0 = giống hệt, 0.0 = đen vs trắng hoàn toàn
    private fun similarity(a: IntArray, b: IntArray): Double {
        var sum = 0L
        for (i in a.indices) sum += abs(a[i] - b[i])
        return 1.0 - sum.toDouble() / (a.size * 255.0)
    }
}
