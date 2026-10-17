
package com.woode.vpn

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient

import androidx.webkit.WebViewAssetLoader

import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config

import org.json.JSONObject

import java.io.ByteArrayInputStream
import java.util.concurrent.Executors

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_CONFIG = 2101
        private const val REQUEST_VPN_PERMISSION = 2102

        private const val ASSET_HOST =
            "appassets.androidplatform.net"

        private const val ASSET_BASE_URL =
            "https://appassets.androidplatform.net/assets/"
    }

    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader

    private val executor = Executors.newSingleThreadExecutor()

    @Volatile
    private var activityDestroyed = false

    private val backend: GoBackend by lazy {
        GoBackend(applicationContext)
    }

    private var selectedConfig: Config? = null
    private var connectionInProgress = false

    private val tunnel = object : Tunnel {

        override fun getName(): String = "woodevpn"

        override fun onStateChange(newState: Tunnel.State) {
            val isUp = newState == Tunnel.State.UP

            publish(
                if (isUp) "connected" else "disconnected",
                if (isUp) {
                    "حالة نفق WireGuard نشطة"
                } else {
                    "نفق VPN غير متصل"
                },
                isUp
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/",
                WebViewAssetLoader.AssetsPathHandler(this)
            )
            .build()

        webView = WebView(this)

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

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse {
                val uri = request?.url

                if (uri != null && isLocalAssetUrl(uri)) {
                    return assetLoader.shouldInterceptRequest(uri)
                        ?: blockedResponse()
                }

                // لا نسمح بتحميل موارد من نطاقات خارجية.
                return blockedResponse()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return true

                // التنقل مسموح لملفات واجهة التطبيق المحلية فقط.
                return !isLocalAssetUrl(uri)
            }

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)

                if (url != null &&
                    isLocalAssetUrl(Uri.parse(url))
                ) {
                    publish(
                        "ready",
                        "واجهة WOODE VPN جاهزة"
                    )
                }
            }
        }

        setContentView(webView)

        webView.loadUrl(
            ASSET_BASE_URL + "index.html"
        )
    }

    private fun isLocalAssetUrl(uri: Uri): Boolean {
        return uri.scheme == "https" &&
            uri.host == ASSET_HOST &&
            (uri.path?.startsWith("/assets/") == true)
    }

    private fun blockedResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            403,
            "Blocked by application policy",
            emptyMap<String, String>(),
            ByteArrayInputStream(ByteArray(0))
        )
    }

    private inner class VpnBridge {

        @JavascriptInterface
        fun selectConfig() {
            runOnUiThread {
                if (!activityDestroyed) {
                    openConfigPicker()
                }
            }
        }

        @JavascriptInterface
        fun connect() {
            runOnUiThread {
                if (!activityDestroyed) {
                    requestConnection()
                }
            }
        }

        @JavascriptInterface
        fun disconnect() {
            runOnUiThread {
                if (!activityDestroyed) {
                    disconnectTunnel()
                }
            }
        }
    }

    private fun openConfigPicker() {
        val intent = Intent(
            Intent.ACTION_OPEN_DOCUMENT
        ).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }

        try {
            startActivityForResult(
                intent,
                REQUEST_CONFIG
            )
        } catch (_: Exception) {
            publish(
                "error",
                "تعذر فتح مستعرض الملفات"
            )
        }
    }

    @Deprecated("Uses the activity result API for compatibility")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (activityDestroyed) return

        when (requestCode) {

            REQUEST_CONFIG -> {
                if (resultCode != RESULT_OK) {
                    publish(
                        "ready",
                        "لم يتم اختيار ملف إعداد"
                    )
                    return
                }

                val uri = data?.data

                if (uri == null) {
                    publish(
                        "error",
                        "تعذر الوصول إلى الملف المختار"
                    )
                    return
                }

                importConfig(uri)
            }

            REQUEST_VPN_PERMISSION -> {
                if (resultCode == RESULT_OK) {
                    startTunnel()
                } else {
                    publish(
                        "error",
                        "لم تتم الموافقة على إذن VPN"
                    )
                }
            }
        }
    }

    private fun importConfig(uri: Uri) {
        try {
            val config = contentResolver
                .openInputStream(uri)
                ?.use { stream ->
                    Config.parse(stream)
                }
                ?: throw IllegalArgumentException(
                    "Empty configuration"
                )

            require(config.peers.isNotEmpty()) {
                "Missing WireGuard peer"
            }

            selectedConfig = config

            publish(
                "config_loaded",
                "تم تحميل إعداد WireGuard"
            )

        } catch (_: Exception) {
            selectedConfig = null

            publish(
                "error",
                "تعذر قراءة الملف. اختر إعداد WireGuard صحيحًا."
            )
        }
    }

    private fun requestConnection() {
        if (connectionInProgress) return

        if (selectedConfig == null) {
            publish(
                "config_required",
                "اختر ملف إعداد WireGuard أولًا"
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
        val config = selectedConfig

        if (config == null) {
            publish(
                "config_required",
                "اختر ملف الإعداد أولًا"
            )
            return
        }

        if (connectionInProgress) return

        connectionInProgress = true

        publish(
            "connecting",
            "جارٍ تشغيل نفق WireGuard"
        )

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
                    if (activityDestroyed) return@runOnUiThread

                    connectionInProgress = false

                    if (confirmed) {
                        publish(
                            "connected",
                            "حالة نفق WireGuard نشطة",
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
                    if (activityDestroyed) return@runOnUiThread

                    connectionInProgress = false

                    publish(
                        "error",
                        "فشل تشغيل النفق. تحقق من إعداد الخادم والمفاتيح."
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

        publish(
            "disconnecting",
            "جارٍ فصل اتصال VPN"
        )

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
                    if (activityDestroyed) return@runOnUiThread

                    connectionInProgress = false

                    if (confirmed) {
                        publish(
                            "disconnected",
                            "تم فصل نفق WireGuard",
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
                    if (activityDestroyed) return@runOnUiThread

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
        if (activityDestroyed || !::webView.isInitialized) {
            return
        }

        val event = JSONObject()
            .put("type", type)
            .put("message", message)

        if (connected != null) {
            event.put("connected", connected)
        }

        val json = event.toString()

        runOnUiThread {
            if (activityDestroyed || !::webView.isInitialized) {
                return@runOnUiThread
            }

            webView.evaluateJavascript(
                "window.onNativeVpnEvent && " +
                    "window.onNativeVpnEvent($json);",
                null
            )
        }
    }

    override fun onDestroy() {
        activityDestroyed = true

        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.removeJavascriptInterface("WoodeVPN")
            webView.destroy()
        }

        executor.shutdown()

        super.onDestroy()
    }
}
