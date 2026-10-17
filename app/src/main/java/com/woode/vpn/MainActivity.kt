
package com.woode.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config

import org.json.JSONObject
import java.util.concurrent.Executors

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_CONFIG = 2101
        private const val REQUEST_VPN_PERMISSION = 2102
        private const val ASSET_BASE = "file:///android_asset/"
    }

    private lateinit var webView: WebView

    private val executor = Executors.newSingleThreadExecutor()

    private val backend by lazy {
        GoBackend(applicationContext)
    }

    private var selectedConfig: Config? = null
    private var connectionInProgress = false

    private val tunnel = object : Tunnel {

        override fun getName(): String = "woodevpn"

        override fun onStateChange(newState: Tunnel.State) {
            val isUp = newState == Tunnel.State.UP

            runOnUiThread {
                publish(
                    if (isUp) "connected" else "disconnected",
                    if (isUp)
                        "تم إنشاء نفق WireGuard"
                    else
                        "نفق VPN غير متصل",
                    isUp
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode =
                android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        webView.addJavascriptInterface(
            VpnBridge(),
            "WoodeVPN"
        )

        webView.webViewClient = object : WebViewClient() {

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return true

                // السماح بملفات الواجهة المحلية فقط.
                return !url.startsWith(ASSET_BASE)
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)
                publish("ready", "واجهة WOODE VPN جاهزة")
            }
        }

        webView.loadUrl(
            "file:///android_asset/index.html"
        )
    }

    private inner class VpnBridge {

        @JavascriptInterface
        fun selectConfig() {
            runOnUiThread {
                openConfigPicker()
            }
        }

        @JavascriptInterface
        fun connect() {
            runOnUiThread {
                requestConnection()
            }
        }

        @JavascriptInterface
        fun disconnect() {
            runOnUiThread {
                disconnectTunnel()
            }
        }
    }

    private fun openConfigPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        try {
            startActivityForResult(intent, REQUEST_CONFIG)
        } catch (_: Exception) {
            publish(
                "error",
                "تعذر فتح مستعرض الملفات على هذا الجهاز"
            )
        }
    }

    @Deprecated("Uses activity result API for broad compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CONFIG) {
            if (resultCode != RESULT_OK) {
                publish("ready", "لم يتم اختيار ملف")
                return
            }

            val uri: Uri = data?.data ?: run {
                publish("error", "تعذر قراءة الملف المختار")
                return
            }

            importConfig(uri)
            return
        }

        if (requestCode == REQUEST_VPN_PERMISSION) {
            if (resultCode == RESULT_OK) {
                startTunnel()
            } else {
                publish(
                    "error",
                    "يجب الموافقة على إذن VPN من نظام Android"
                )
            }
        }
    }

    private fun importConfig(uri: Uri) {
        try {
            val config = contentResolver
                .openInputStream(uri)
                ?.use { stream -> Config.parse(stream) }
                ?: throw IllegalArgumentException("Empty config")

            if (config.peers.isEmpty()) {
                throw IllegalArgumentException("Missing peer")
            }

            selectedConfig = config

            publish(
                "config_loaded",
                "تم استيراد إعداد WireGuard بنجاح"
            )

        } catch (_: Exception) {
            selectedConfig = null

            publish(
                "error",
                "تعذر قراءة الإعداد. اختر ملف WireGuard صحيحًا بامتداد .conf"
            )
        }
    }

    private fun requestConnection() {
        if (connectionInProgress) return

        if (selectedConfig == null) {
            publish(
                "config_required",
                "اختر ملف إعدادات WireGuard أولًا"
            )
            return
        }

        try {
            val permissionIntent = VpnService.prepare(this)

            if (permissionIntent != null) {
                startActivityForResult(
                    permissionIntent,
                    REQUEST_VPN_PERMISSION
                )
            } else {
                startTunnel()
            }
        } catch (_: Exception) {
            publish(
                "error",
                "تعذر طلب إذن VPN من النظام"
            )
        }
    }

    private fun startTunnel() {
        val config = selectedConfig ?: run {
            publish("config_required", "اختر ملف الإعداد أولًا")
            return
        }

        if (connectionInProgress) return

        connectionInProgress = true
        publish("connecting", "جارٍ إنشاء نفق VPN")

        executor.execute {
            try {
                val result = backend.setState(
                    tunnel,
                    Tunnel.State.UP,
                    config
                )

                val confirmed =
                    result == Tunnel.State.UP &&
                    backend.getState(tunnel) == Tunnel.State.UP

                runOnUiThread {
                    connectionInProgress = false

                    if (confirmed) {
                        publish(
                            "connected",
                            "تم إنشاء نفق WireGuard",
                            true
                        )
                    } else {
                        publish(
                            "error",
                            "لم يتم تأكيد حالة النفق"
                        )
                    }
                }

            } catch (_: Exception) {
                runOnUiThread {
                    connectionInProgress = false

                    publish(
                        "error",
                        "فشل الاتصال. تحقق من إعداد الخادم والشبكة والمفاتيح."
                    )
                }
            }
        }
    }

    private fun disconnectTunnel() {
        if (connectionInProgress) {
            publish(
                "busy",
                "انتظر انتهاء العملية الحالية"
            )
            return
        }

        connectionInProgress = true
        publish("disconnecting", "جارٍ فصل اتصال VPN")

        executor.execute {
            try {
                backend.setState(
                    tunnel,
                    Tunnel.State.DOWN,
                    null
                )

                val confirmed =
                    backend.getState(tunnel) == Tunnel.State.DOWN

                runOnUiThread {
                    connectionInProgress = false

                    if (confirmed) {
                        publish(
                            "disconnected",
                            "تم فصل نفق VPN",
                            false
                        )
                    } else {
                        publish(
                            "error",
                            "تعذر تأكيد فصل النفق"
                        )
                    }
                }

            } catch (_: Exception) {
                runOnUiThread {
                    connectionInProgress = false

                    publish(
                        "error",
                        "حدث خطأ أثناء فصل اتصال VPN"
                    )
                }
            }
        }
    }

    private fun publish(
        type: String,
        message: String,
        connected: Boolean? = null
    ) {
        if (!::webView.isInitialized) return

        val event = JSONObject()
            .put("type", type)
            .put("message", message)

        if (connected != null) {
            event.put("connected", connected)
        }

        val json = event.toString()

        runOnUiThread {
            webView.evaluateJavascript(
                "window.onNativeVpnEvent && " +
                    "window.onNativeVpnEvent($json);",
                null
            )
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.destroy()
        }

        executor.shutdown()
        super.onDestroy()
    }
}
