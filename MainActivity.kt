package com.masstudio.flexuremas

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import java.io.File

/**
 * Flexure MAS for Android.
 *
 * The whole beam analysis app lives in app/src/main/assets/index.html and runs
 * offline inside a WebView. This activity adds what a phone app needs:
 *  - Save: Android's "create document" picker writes a real .fmas file.
 *  - Open: the page's file input opens Android's document picker.
 *  - Autosave: the page sends the current beam here after each change; it is
 *    stored in the app's private storage and restored on the next launch.
 *  - Back button: returns to the Diagrams tab first, then leaves the app.
 *  - Edge-to-edge: content is padded clear of the status bar, navigation bar,
 *    camera cutout and keyboard.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var webView: WebView

    /** Callback from the WebView's file input, waiting for the picker result. */
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    /** File contents waiting for the user to pick where to save them. */
    private var pendingSaveText: String? = null

    private val autosaveFile by lazy { File(filesDir, "autosave.fmas") }

    private val openDocument =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            fileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
            fileCallback = null
        }

    private val createDocument =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
            val text = pendingSaveText
            pendingSaveText = null
            if (uri == null || text == null) {
                sendSaveResult(false, "cancelled")
                return@registerForActivityResult
            }
            try {
                writeText(uri, text)
                sendSaveResult(true, displayName(uri) ?: "your file")
            } catch (e: Exception) {
                sendSaveResult(false, "error")
            }
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingSaveText = savedInstanceState?.getString(KEY_PENDING_SAVE)

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        // The root shows behind the system bars; its colour matches the app header.
        root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor(if (night) "#1F292D" else "#F8FAF9"))
        }
        webView = WebView(this).apply {
            setBackgroundColor(Color.parseColor(if (night) "#182023" else "#E7ECEB"))
        }
        root.addView(
            webView,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        setContentView(root)

        // Keep the page clear of the status bar, navigation bar, cutout and keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false          // the app's own assets stay readable
            allowContentAccess = true        // needed to read files chosen in the picker
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
        }

        webView.addJavascriptInterface(Bridge(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.scheme == "file" && (url.path ?: "").startsWith("/android_asset/")) return false
                // Anything else (none today) opens outside the app.
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, url))
                } catch (e: ActivityNotFoundException) {
                    // No app can open it; ignore.
                }
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                try {
                    // .fmas has no registered type, so show every file.
                    openDocument.launch(arrayOf("*/*"))
                } catch (e: ActivityNotFoundException) {
                    fileCallback = null
                    callback.onReceiveValue(null)
                }
                return true
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "(window.flexureBack && window.flexureBack()) ? 'yes' : 'no'"
                ) { result ->
                    if (result == null || !result.contains("yes")) {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onPause() {
        // Write the latest beam to storage before Android may close the app.
        webView.evaluateJavascript("window.flexureFlush && window.flexureFlush();", null)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        pendingSaveText?.let { if (it.length < 100_000) outState.putString(KEY_PENDING_SAVE, it) }
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        root.removeView(webView)
        webView.destroy()
        super.onDestroy()
    }

    /** Methods the page calls as window.AndroidBridge.*; they run on a background thread. */
    inner class Bridge {
        @JavascriptInterface
        fun saveFile(name: String, text: String) {
            runOnUiThread {
                pendingSaveText = text
                try {
                    createDocument.launch(name)
                } catch (e: ActivityNotFoundException) {
                    pendingSaveText = null
                    sendSaveResult(false, "error")
                }
            }
        }

        @JavascriptInterface
        fun saveState(json: String) {
            try {
                val tmp = File(filesDir, "autosave.tmp")
                tmp.writeText(json, Charsets.UTF_8)
                if (!tmp.renameTo(autosaveFile)) {
                    autosaveFile.writeText(json, Charsets.UTF_8)
                    tmp.delete()
                }
            } catch (e: Exception) {
                // Storage full or unavailable: keep running without autosave.
            }
        }

        @JavascriptInterface
        fun loadState(): String = try {
            if (autosaveFile.exists()) autosaveFile.readText(Charsets.UTF_8) else ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun writeText(uri: Uri, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        // "wt" truncates an existing file; some providers only accept "w".
        val stream = try {
            contentResolver.openOutputStream(uri, "wt")
        } catch (e: Exception) {
            contentResolver.openOutputStream(uri, "w")
        } ?: throw IllegalStateException("Could not open the file for writing")
        stream.use { it.write(bytes) }
    }

    private fun displayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    private fun sendSaveResult(ok: Boolean, info: String) {
        val js = "window.flexureSaveResult && window.flexureSaveResult($ok, ${JSONObject.quote(info)});"
        webView.post { webView.evaluateJavascript(js, null) }
    }

    private companion object {
        const val KEY_PENDING_SAVE = "pendingSave"
    }
}
