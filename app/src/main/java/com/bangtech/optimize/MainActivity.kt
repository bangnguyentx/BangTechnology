package com.bangtech.optimize

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    companion object {
        var shellService: IShellService? = null
        fun runShizukuGlobal(cmd: String): String {
            return try {
                shellService?.runCommand(cmd) ?: """{"exitCode":-2,"err":"Shizuku chưa kết nối"}"""
            } catch (e: Exception) {
                """{"exitCode":-1,"err":"${e.message}"}"""
            }
        }
    }

    private lateinit var webView: WebView
    private var volDownStartTime = 0L

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("com.bangtech.optimize", ShellUserService::class.java.name)
    ).daemon(false).processNameSuffix("bang_shell").debuggable(false).version(80)

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            shellService = IShellService.Stub.asInterface(binder)
            runOnUiThread {
                webView.evaluateJavascript("window.onShizukuReady && window.onShizukuReady(true);", null)
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
        }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) bindShellService()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Shizuku.addRequestPermissionResultListener(permissionListener)

        webView = WebView(this).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            addJavascriptInterface(BangJsBridge(), "BangBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    injectRealHardwareData()
                }
            }
        }
        setContentView(webView)
        webView.loadUrl("file:///android_asset/BangOptimize.html")

        startForegroundService(Intent(this, RealtimeRamService::class.java))
        checkAndBindShizuku()
    }

    private fun checkAndBindShizuku() {
        try {
            if (!Shizuku.pingBinder()) return
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindShellService()
            } else {
                Shizuku.requestPermission(1001)
            }
        } catch (_: Exception) {}
    }

    private fun bindShellService() {
        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
        } catch (_: Exception) {}
    }

    private fun injectRealHardwareData() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalMb = mem.totalMem / (1024 * 1024)
        val usedMb = (mem.totalMem - mem.availMem) / (1024 * 1024)
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempC = (batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 330) ?: 330) / 10.0
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 75) ?: 75

        val payload = JSONObject().apply {
            put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("android", "Android ${Build.VERSION.RELEASE}")
            put("totalRamMb", totalMb)
            put("usedRamMb", usedMb)
            put("tempC", tempC)
            put("battery", level)
            put("shizukuReady", shellService != null)
        }
        webView.evaluateJavascript("window.receiveNativeDeviceInfo && window.receiveNativeDeviceInfo($payload);", null)
    }

    // Phím cứu hộ vật lý: Giữ nút Giảm Âm Lượng 3 giây -> Trả về màn hình gốc 100%
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && volDownStartTime == 0L) {
            volDownStartTime = SystemClock.elapsedRealtime()
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && volDownStartTime > 0L) {
            val heldMs = SystemClock.elapsedRealtime() - volDownStartTime
            volDownStartTime = 0L
            if (heldMs >= 3000L) {
                runShizukuGlobal("wm size reset && wm density reset && settings delete system peak_refresh_rate && settings delete system min_refresh_rate")
                Toast.makeText(this, "BangOptimize: Đã khôi phục màn hình gốc khẩn cấp!", Toast.LENGTH_LONG).show()
                return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    inner class BangJsBridge {
        @JavascriptInterface
        fun exec(cmd: String): String = runShizukuGlobal(cmd)

        @JavascriptInterface
        fun requestShizuku() = runOnUiThread { checkAndBindShizuku() }

        @JavascriptInterface
        fun setRamThreshold(mb: Long) {
            startForegroundService(Intent(this@MainActivity, RealtimeRamService::class.java).apply {
                putExtra("threshold_mb", mb)
            })
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }
}
