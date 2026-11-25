package in.royalprajapati.mockteststudio

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.io.ByteArrayOutputStream

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var pendingName: String? = null
    private var pendingBase64: String? = null
    private val appUrl = "https://mock-test-studio-cloud.netlify.app/"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                deliverPendingFile()
            }
        }
        handleIntent(intent)
        webView.loadUrl(appUrl)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
        if (::webView.isInitialized && webView.url != null) deliverPendingFile()
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
                    ?: intent.clipData?.getItemAt(0)?.uri
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri == null) return
        try {
            val name = queryName(uri) ?: "questions.json"
            if (!name.lowercase().endsWith(".json")) {
                Toast.makeText(this, "Please select a .json question file", Toast.LENGTH_LONG).show()
                return
            }
            val input = contentResolver.openInputStream(uri) ?: throw IllegalStateException("Cannot read file")
            val output = ByteArrayOutputStream()
            input.use { stream -> stream.copyTo(output) }
            val bytes = output.toByteArray()
            if (bytes.size > 10 * 1024 * 1024) throw IllegalArgumentException("JSON file is larger than 10 MB")
            pendingName = name
            pendingBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open JSON: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun queryName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    private fun deliverPendingFile() {
        val name = pendingName ?: return
        val data = pendingBase64 ?: return
        val safeName = org.json.JSONObject.quote(name)
        val safeData = org.json.JSONObject.quote(data)
        val script = """
            (async function() {
              try {
                const bytes = Uint8Array.from(atob($safeData), c => c.charCodeAt(0));
                const blob = new Blob([bytes], {type:'application/json'});
                const cache = await caches.open('mock-test-studio-v8');
                const key = new Request(new URL('__share_target_file__', location.origin + location.pathname.replace(/[^/]*$/, '')).toString());
                const headers = new Headers({'Content-Type':'application/json','x-share-filename':$safeName});
                await cache.put(key, new Response(blob, {headers}));
                location.href = location.pathname + '?shared=1';
              } catch (e) { alert('JSON import handoff failed: ' + e); }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
        pendingName = null
        pendingBase64 = null
    }
}
