
package com.woode.vpn

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.util.Base64
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
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class MainActivity : Activity() {

    companion object {
        private const val REQUEST_CONFIG = 2101
        private const val REQUEST_VPN_PERMISSION = 2102
        private const val REQUEST_EXPORT_CONFIG = 2103

        private const val ASSET_HOST =
            "appassets.androidplatform.net"

        private const val ASSET_BASE_URL =
            "https://appassets.androidplatform.net/assets/"

        // الحد الأقصى لملف الإعداد: 1 ميغابايت
        private const val MAX_CONFIG_BYTES = 1024 * 1024

        // إصدار صيغة ملفات ود فيصل
        private const val WDF_VERSION = 1
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

    // نحتفظ بمحتوى الإعداد للتحقق منه وتصديره لاحقًا.
    private var selectedConfigBytes: ByteArray? = null

    private var connectionInProgress = false

    private val tunnel = object : Tunnel {

        override fun getName(): String = "woodevpn"

        override fun onStateChange(newState: Tunnel.State) {
            val isUp = newState == Tunnel.State.UP

            publish(
                if (isUp) "connected" else "disconnected",
                if (isUp) {
                    "اتصال WireGuard نشط"
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

                // منع تحميل موارد الواجهة من مواقع خارجية.
                return blockedResponse()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return true
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
                        "واجهة ود فيصل V3 جاهزة"
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
        fun exportConfig() {
            runOnUiThread {
                if (!activityDestroyed) {
                    openExportPicker()
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

    private fun openExportPicker() {
        if (selectedConfig == null ||
            selectedConfigBytes == null
        ) {
            publish(
                "config_required",
                "حمّل إعدادًا صالحًا قبل التصدير"
            )
            return
        }

        val intent = Intent(
            Intent.ACTION_CREATE_DOCUMENT
        ).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(
                Intent.EXTRA_TITLE,
                "wad-faisal-profile.wdf"
            )
        }

        try {
            startActivityForResult(
                intent,
                REQUEST_EXPORT_CONFIG
            )
        } catch (_: Exception) {
            publish(
                "error",
                "تعذر فتح نافذة حفظ الملف"
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

            REQUEST_EXPORT_CONFIG -> {
                if (resultCode != RESULT_OK) {
                    publish(
                        "ready",
                        "تم إلغاء حفظ الإعداد"
                    )
                    return
                }

                val uri = data?.data

                if (uri == null) {
                    publish(
                        "error",
                        "تعذر الوصول إلى مكان الحفظ"
                    )
                    return
                }

                exportConfigTo(uri)
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
            val fileBytes = contentResolver
                .openInputStream(uri)
                ?.use { input ->
                    readLimited(input, MAX_CONFIG_BYTES)
                }
                ?: throw IllegalArgumentException(
                    "تعذر قراءة الملف"
                )

            require(fileBytes.isNotEmpty()) {
                "ملف فارغ"
            }

            // يقبل ملف WireGuard المعتاد أو ملف WDF الخاص بنا.
            val configBytes = decodeImportedConfig(fileBytes)

            val config = Config.parse(
                ByteArrayInputStream(configBytes)
            )

            require(config.peers.isNotEmpty()) {
                "الإعداد لا يحتوي على خادم WireGuard"
            }

            selectedConfig = config
            selectedConfigBytes = configBytes.copyOf()

            publish(
                "config_loaded",
                "تم تحميل إعداد ود فيصل بنجاح"
            )

        } catch (_: Exception) {
            selectedConfig = null
            selectedConfigBytes = null

            publish(
                "error",
                "تعذر قراءة الإعداد. تحقق من صحة ملف WireGuard أو WDF."
            )
        }
    }

    /**
     * صيغة WDF الحالية:
     * {
     *   "format": "WDF",
     *   "version": 1,
     *   "engine": "wireguard",
     *   "payload": "Base64 لمحتوى ملف WireGuard"
     * }
     */
    private fun decodeImportedConfig(
        fileBytes: ByteArray
    ): ByteArray {
        val text = String(
            fileBytes,
            StandardCharsets.UTF_8
        ).trim()

        if (!text.startsWith("{")) {
            // ملف WireGuard عادي.
            return fileBytes
        }

        val json = JSONObject(text)

        if (json.optString("format") != "WDF") {
            // سيجري التحقق منه لاحقًا بواسطة محلل WireGuard.
            return fileBytes
        }

        require(json.optInt("version", -1) == WDF_VERSION) {
            "إصدار WDF غير مدعوم"
        }

        require(json.optString("engine") == "wireguard") {
            "محرك الاتصال الموجود في الملف غير مدعوم"
        }

        val payload = json.optString("payload", "")

        require(payload.isNotBlank()) {
            "محتوى ملف WDF فارغ"
        }

        val decoded = Base64.decode(
            payload,
            Base64.DEFAULT
        )

        require(decoded.isNotEmpty()) {
            "محتوى إعداد WDF فارغ"
        }

        require(decoded.size <= MAX_CONFIG_BYTES) {
            "حجم الإعداد أكبر من الحد المسموح"
        }

        return decoded
    }

    private fun readLimited(
        input: InputStream,
        maxBytes: Int
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0

        while (true) {
            val count = input.read(buffer)

            if (count == -1) break

            total += count

            require(total <= maxBytes) {
                "حجم الملف أكبر من الحد المسموح"
            }

            output.write(buffer, 0, count)
        }

        return output.toByteArray()
    }

    private fun exportConfigTo(uri: Uri) {
        try {
            val configBytes = selectedConfigBytes
                ?: throw IllegalStateException(
                    "لا يوجد إعداد محمّل"
                )

            val json = JSONObject()
                .put("format", "WDF")
                .put("version", WDF_VERSION)
                .put("engine", "wireguard")
                .put(
                    "payload",
                    Base64.encodeToString(
                        configBytes,
                        Base64.NO_WRAP
                    )
                )

            val outputBytes = json
                .toString(2)
                .toByteArray(StandardCharsets.UTF_8)

            val output = contentResolver.openOutputStream(uri)
                ?: throw IllegalStateException(
                    "تعذر فتح الملف للحفظ"
                )

            output.use {
                it.write(outputBytes)
                it.flush()
            }

            publish(
                "config_exported",
                "تم حفظ إعداد ود فيصل بصيغة WDF"
            )

        } catch (_: Exception) {
            publish(
                "error",
                "فشل تصدير الإعداد. حاول الحفظ في مكان آخر."
            )
        }
    }

    private fun requestConnection() {
        if (connectionInProgress) return

        if (selectedConfig == null) {
            publish(
                "config_required",
                "اختر ملف إعداد WireGuard أو WDF أولًا"
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
                            "اتصال WireGuard نشط",
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
                            "تم فصل اتصال VPN",
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
