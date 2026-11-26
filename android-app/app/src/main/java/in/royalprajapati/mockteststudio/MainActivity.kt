package `in`.royalprajapati.mockteststudio

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
    private var pendingMime: String? = null
    private var pendingBase64: String? = null
    private var pageReady = false

    // GitHub Pages is the current production host for the PWA.
    private val appUrl = "https://royalprajapatigroup2026-collab.github.io/Mock-test-studio-cloud/"
    private val maxSharedBytes = 25L * 1024L * 1024L
    private val cacheName = "mock-test-studio-v15"

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
                pageReady = true
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
        if (::webView.isInitialized && pageReady) {
            webView.post { deliverPendingFile() }
        }
    }

    @Suppress("DEPRECATION")
    private fun extractSharedUri(intent: Intent): Uri? {
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                (intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
                    ?: intent.clipData?.getItemAt(0)?.uri
                    ?: intent.data
            }
            Intent.ACTION_VIEW -> intent.data ?: intent.clipData?.getItemAt(0)?.uri
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.clipData?.getItemAt(0)?.uri
                    ?: intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.firstOrNull()
            }
            else -> null
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uri = extractSharedUri(intent) ?: return
        try {
            val name = queryName(uri) ?: "shared-file"
            val mime = contentResolver.getType(uri) ?: guessMimeFromName(name)
            val input = contentResolver.openInputStream(uri)
                ?: throw IllegalStateException("Android could not read the selected file.")
            val output = ByteArrayOutputStream()
            input.use { stream ->
                val buffer = ByteArray(8192)
                var total = 0L
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxSharedBytes) {
                        throw IllegalArgumentException("The shared file is larger than 25 MB.")
                    }
                    output.write(buffer, 0, count)
                }
            }
            pendingName = name
            pendingMime = mime
            pendingBase64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not receive file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun guessMimeFromName(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "json" -> "application/json"
            "pdf" -> "application/pdf"
            "txt", "md", "csv" -> "text/plain"
            "html", "htm" -> "text/html"
            "xml" -> "application/xml"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> "application/octet-stream"
        }
    }

    private fun queryName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        contentResolver.query(
            uri,
            arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index)
            }
        }
        return null
    }

    private fun deliverPendingFile() {
        val name = pendingName ?: return
        val mime = pendingMime ?: "application/octet-stream"
        val data = pendingBase64 ?: return
        val safeName = org.json.JSONObject.quote(name)
        val safeMime = org.json.JSONObject.quote(mime)
        val safeData = org.json.JSONObject.quote(data)
        val safeCache = org.json.JSONObject.quote(cacheName)
        val script = """
            (async function() {
              try {
                const bytes = Uint8Array.from(atob($safeData), c => c.charCodeAt(0));
                const blob = new Blob([bytes], {type:$safeMime});
                const cache = await caches.open($safeCache);
                const baseUrl = new URL('./', location.href);
                const key = new Request(new URL('__share_target_file__', baseUrl).toString());
                const headers = new Headers({'Content-Type':$safeMime,'x-share-filename':$safeName});
                await cache.put(key, new Response(blob, {headers}));
                const destination = new URL('./', location.href);
                destination.searchParams.set('shared', '1');
                location.replace(destination.toString());
              } catch (e) {
                alert('File handoff to Mock Test Studio failed: ' + e);
              }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
        pendingName = null
        pendingMime = null
        pendingBase64 = null
    }
}
