package com.futtv.app.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL            = "extra_url"
        const val EXTRA_CHANNEL_NAME   = "extra_channel_name"
        const val EXTRA_SERVER_LABEL   = "extra_server_label"
        const val EXTRA_SERVERS_LABELS = "extra_servers_labels"
        const val EXTRA_SERVERS_URLS   = "extra_servers_urls"
        const val EXTRA_SERVER_INDEX   = "extra_server_index"

        fun start(
            context: Context,
            url: String,
            channelName: String,
            serverLabels: Array<String>,
            serverUrls: Array<String>,
            serverIndex: Int
        ) {
            context.startActivity(
                Intent(context, PlayerActivity::class.java).apply {
                    putExtra(EXTRA_URL, url)
                    putExtra(EXTRA_CHANNEL_NAME, channelName)
                    putExtra(EXTRA_SERVERS_LABELS, serverLabels)
                    putExtra(EXTRA_SERVERS_URLS, serverUrls)
                    putExtra(EXTRA_SERVER_INDEX, serverIndex)
                }
            )
        }
    }

    // Views
    private var rootLayout: FrameLayout? = null
    private var exoPlayerView: PlayerView? = null
    private var webView: WebView? = null
    private var fullscreenContainer: FrameLayout? = null
    private var overlayLayout: LinearLayout? = null
    private var loadingBar: ProgressBar? = null
    private var statusTextView: TextView? = null
    private var errorLayout: LinearLayout? = null

    // Player
    private var exoPlayer: ExoPlayer? = null

    // State
    private var channelName = ""
    private var serverLabels = arrayOf<String>()
    private var serverUrls   = arrayOf<String>()
    private var currentIndex = 0
    private var isOverlayVisible = false
    private val hideOverlayRunnable = Runnable { hideOverlay() }
    // Índice del botón con foco en el overlay: 0..serverLabels.size-1 = servidores, size = Salir
    private var overlayFocusIndex = 0

    // WebView stream detection
    private var videoStarted = false
    @Volatile private var webViewCurrentUrl = ""
    // Evita auto-saltar de servidor si ExoPlayer ya estaba reproduciendo bien
    private var exoWasPlaying = false
    private val webViewTimeoutRunnable = Runnable {
        if (!videoStarted) {
            runOnUiThread { tryNextServerAuto() }
        }
    }
    private val WEBVIEW_TIMEOUT_MS = 20_000L

    // Watchdog: si ExoPlayer lleva demasiado tiempo en STATE_BUFFERING después de haber
    // reproducido bien, el stream se cortó → intentar siguiente servidor.
    // 60 s da tiempo suficiente a que microcortes de red se recuperen solos.
    private val STALL_TIMEOUT_MS = 60_000L
    private val stallWatchdogRunnable = Runnable {
        tryNextServerAuto()
    }

    // URL y headers del stream activo en ExoPlayer (para reintentar el mismo servidor)
    private var currentExoUrl     = ""
    private var currentExoHeaders = emptyMap<String, String>()
    // Reintentos sobre el mismo servidor antes de pasar al siguiente
    private var sameServerRetries = 0
    private val MAX_SAME_SERVER_RETRIES = 1

    // iframe: soporte para players que requieren ser embebidos en iframe
    private var iframeRetried = false

    // Detección de video congelado (imagen fija, audio continúa)
    private var lastRenderedFrames = 0
    private var frameStallCount    = 0
    private val FRAME_CHECK_INTERVAL_MS = 5_000L
    private val FRAME_STALL_MAX_COUNT   = 3   // 15 s sin frames nuevos = video congelado
    private val videoFreezeCheckRunnable = object : Runnable {
        override fun run() {
            val player = exoPlayer ?: return
            if (!player.isPlaying) return
            val frames = player.videoDecoderCounters?.renderedOutputBufferCount ?: 0
            if (frames == lastRenderedFrames && frames > 0) {
                frameStallCount++
                android.util.Log.w("FutTV_Player", "Video congelado ($frameStallCount/$FRAME_STALL_MAX_COUNT) frames=$frames")
                if (frameStallCount >= FRAME_STALL_MAX_COUNT) {
                    frameStallCount = 0; lastRenderedFrames = 0
                    runOnUiThread {
                        showStatus("Video congelado, reconectando...")
                        if (sameServerRetries < MAX_SAME_SERVER_RETRIES) {
                            sameServerRetries++
                            val u = currentExoUrl; val h = currentExoHeaders
                            rootLayout?.postDelayed({
                                if (u.isNotBlank()) playWithExoPlayer(u, h) else loadServer(currentIndex)
                            }, 2_000)
                        } else { tryNextServerAuto() }
                    }
                    return
                }
            } else {
                frameStallCount = 0
                lastRenderedFrames = frames
            }
            rootLayout?.postDelayed(this, FRAME_CHECK_INTERVAL_MS)
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        channelName  = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Canal"
        serverLabels = intent.getStringArrayExtra(EXTRA_SERVERS_LABELS) ?: arrayOf("Servidor 1")
        serverUrls   = intent.getStringArrayExtra(EXTRA_SERVERS_URLS) ?: arrayOf("")
        currentIndex = intent.getIntExtra(EXTRA_SERVER_INDEX, 0)

        // Fix: con enableOnBackInvokedCallback=true el KEYCODE_BACK puede
        // no llegar a onKeyDown en algunas versiones/dispositivos.
        // OnBackPressedCallback es el canal confiable en ambos casos.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isOverlayVisible) hideOverlay() else finish()
            }
        })

        setupCookies()
        buildUI()
        loadServer(currentIndex)
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayLayout?.removeCallbacks(hideOverlayRunnable)
        rootLayout?.removeCallbacks(webViewTimeoutRunnable)
        rootLayout?.removeCallbacks(stallWatchdogRunnable)
        rootLayout?.removeCallbacks(videoFreezeCheckRunnable)
        exoPlayer?.release(); exoPlayer = null
        // FIX: remove WebView from parent BEFORE destroy to avoid "still attached" warning
        webView?.let { wv ->
            wv.stopLoading()
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        webView = null
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private fun setupCookies() {
        CookieManager.getInstance().setAcceptCookie(true)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildUI() {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        rootLayout = root

        // ── 1. ExoPlayer ──────────────────────────────────────────────────────
        exoPlayerView = PlayerView(this).apply {
            visibility = View.GONE
            setShutterBackgroundColor(Color.BLACK)
            useController = false
        }
        root.addView(exoPlayerView, matchParent())

        // ── 2. WebView ────────────────────────────────────────────────────────
        webView = WebView(this).apply {
            visibility = View.GONE
            settings.apply {
                javaScriptEnabled                = true
                domStorageEnabled                = true
                @Suppress("DEPRECATION")
                databaseEnabled                  = true
                allowContentAccess               = true
                allowFileAccess                  = true
                mediaPlaybackRequiresUserGesture = false
                useWideViewPort                  = true
                loadWithOverviewMode             = true
                mixedContentMode                 = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                setSupportZoom(false)
                builtInZoomControls              = false
                displayZoomControls              = false
                cacheMode                        = WebSettings.LOAD_DEFAULT
                userAgentString =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Safari/537.36"
            }
            setBackgroundColor(Color.BLACK)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    android.util.Log.d("FutTV_Player", "shouldOverride: ${request.url}")
                    val url = request.url.toString()
                    // Si es un stream directo, ExoPlayer lo reproduce
                    if (url.contains(".m3u8", ignoreCase = true) ||
                        url.contains(".mpd", ignoreCase = true) ||
                        url.endsWith(".ts", ignoreCase = true)) {
                        val referer = view.url
                        val headers = if (!referer.isNullOrBlank()) mapOf("Referer" to referer) else emptyMap()
                        playWithExoPlayer(url, headers)
                        return true
                    }
                    // Para redirects internos del main frame: resetear timeout para que
                    // la página destino tenga tiempo completo de carga
                    if (request.isForMainFrame && !videoStarted) {
                        rootLayout?.removeCallbacks(webViewTimeoutRunnable)
                        rootLayout?.postDelayed(webViewTimeoutRunnable, WEBVIEW_TIMEOUT_MS)
                        android.util.Log.d("FutTV_Player", "Timeout reset on redirect → $url")
                    }
                    view.loadUrl(url)
                    return true
                }

                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (url == "about:blank") return
                    webViewCurrentUrl = url
                    android.util.Log.d("FutTV_Player", "WebView loading: $url")
                    showLoading(true)
                    hideError()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    // about:blank se carga intencionalmente para matar audio de Clappr
                    if (url == "about:blank") return
                    showLoading(false)
                    injectAutoplay(view, 0)
                    injectAutoplay(view, 800)
                    injectAutoplay(view, 2000)
                    injectAutoplay(view, 4000)
                    injectStreamUrlScan(view, 0)
                    injectStreamUrlScan(view, 1500)
                    injectStreamUrlScan(view, 3500)
                    // Detectar mensaje "Use iframe to load this player"
                    injectIframeDetection(view, 600)
                    injectIframeDetection(view, 2500)
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    android.util.Log.e("FutTV_Player", "WebView error: ${error.errorCode} ${error.description} url=${request.url}")
                    if (request.isForMainFrame) {
                        showLoading(false)
                        view.loadUrl("about:blank")
                        rootLayout?.removeCallbacks(webViewTimeoutRunnable)
                        runOnUiThread { tryNextServerAuto() }
                    }
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    android.util.Log.e("FutTV_Player", "SSL error: ${error.primaryError} url=${error.url}")
                    handler.proceed()
                }

                override fun onPageCommitVisible(view: WebView, url: String) {
                    android.util.Log.d("FutTV_Player", "PageCommitVisible: $url")
                }

                // Intercepta TODOS los requests del WebView (incluidos XHR/fetch de Clappr).
                // Cuando Clappr pide el .m3u8 del stream, lo capturamos y se lo pasamos a
                // ExoPlayer en lugar de dejarlo reproducir dentro del WebView.
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: android.webkit.WebResourceRequest
                ): android.webkit.WebResourceResponse? {
                    val url = request.url.toString()
                    if (!videoStarted &&
                        (url.contains(".m3u8", ignoreCase = true) ||
                         url.contains(".mpd",  ignoreCase = true))) {

                        android.util.Log.d("FutTV_Player", "Intercepted stream URL: $url")
                        videoStarted = true   // volatile: evita doble intercepción desde bg thread

                        val referer = webViewCurrentUrl
                        val headers = buildMap<String, String> {
                            if (referer.isNotBlank()) put("Referer", referer)
                            request.url.host?.let { put("Origin", "https://$it") }
                        }
                        // removeCallbacks DEBE ejecutarse en el main thread
                        runOnUiThread {
                            rootLayout?.removeCallbacks(webViewTimeoutRunnable)
                            playWithExoPlayer(url, headers)
                        }

                        // Devuelve respuesta vacía para que Clappr no intente reproducir también
                        return android.webkit.WebResourceResponse(
                            "application/x-mpegURL", "utf-8",
                            java.io.ByteArrayInputStream(ByteArray(0))
                        )
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }

            // Bridge para detectar si el video realmente arrancó
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun onVideoPlaying() {
                    videoStarted = true
                    rootLayout?.removeCallbacks(webViewTimeoutRunnable)
                    runOnUiThread { showLoading(false) }
                }
            }, "FutTVPlayer")

            // Bridge para capturar URL de stream desde inputs ocultos de la página
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun onStreamUrlFound(url: String) {
                    if (url.isBlank() || videoStarted) return
                    android.util.Log.d("FutTV_Player", "Stream URL found in input: $url")
                    val referer = webView?.url ?: ""
                    val headers = buildMap {
                        if (referer.isNotBlank()) put("Referer", referer)
                        put("Origin", "https://streamhdx.com")
                    }
                    runOnUiThread { playWithExoPlayer(url, headers) }
                }
            }, "Android")

            // Bridge para manejar players que piden cargarse dentro de un iframe
            addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun onNeedIframe(originalUrl: String) {
                    if (iframeRetried || videoStarted) return
                    iframeRetried = true
                    android.util.Log.d("FutTV_Player", "iframe requerido: $originalUrl")
                    runOnUiThread { playAsIframe(originalUrl) }
                }
            }, "AndroidIframe")

            webChromeClient = object : WebChromeClient() {
                private var customView: View? = null
                private var customViewCallback: CustomViewCallback? = null

                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    customView = view
                    customViewCallback = callback
                    fullscreenContainer?.addView(view, matchParent())
                    fullscreenContainer?.visibility = View.VISIBLE
                }

                override fun onHideCustomView() {
                    fullscreenContainer?.removeAllViews()
                    fullscreenContainer?.visibility = View.GONE
                    customViewCallback?.onCustomViewHidden()
                    customView = null
                    customViewCallback = null
                }

                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                    return false
                }
            }
        }
        root.addView(webView, matchParent())

        // ── 3. Fullscreen container ───────────────────────────────────────────
        fullscreenContainer = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        root.addView(fullscreenContainer, matchParent())

        // ── 4. Loading indicator + status text ────────────────────────────────
        loadingBar = ProgressBar(this, null, android.R.attr.progressBarStyleLarge).apply {
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E53935"))
            visibility = View.GONE
        }
        root.addView(loadingBar, FrameLayout.LayoutParams(dp(56), dp(56)).apply {
            gravity = android.view.Gravity.CENTER
        })

        statusTextView = TextView(this).apply {
            setTextColor(Color.parseColor("#90CAF9"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setPadding(dp(20), dp(10), dp(20), dp(10))
            setBackgroundColor(Color.parseColor("#CC000010"))
            visibility = View.GONE
        }
        root.addView(statusTextView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(60)
        })

        // ── 5. Error overlay ──────────────────────────────────────────────────
        errorLayout = buildErrorLayout()
        root.addView(errorLayout, matchParent())

        // ── 6. Info/server overlay ────────────────────────────────────────────
        overlayLayout = buildOverlay()
        root.addView(overlayLayout, matchParent())

        setContentView(root)
    }

    // Inyecta autoplay con delay para que el player dinámico ya haya cargado su fuente
    private fun injectAutoplay(view: WebView, delayMs: Long) {
        val js = """
            (function() {
                function notifyPlaying() {
                    try { if (window.FutTVPlayer) window.FutTVPlayer.onVideoPlaying(); } catch(e) {}
                }
                // 1. Conecta listener de "playing" a videos ya presentes
                function attachListeners() {
                    document.querySelectorAll('video').forEach(function(v) {
                        if (!v._futtvListened) {
                            v._futtvListened = true;
                            v.addEventListener('playing', notifyPlaying);
                            v.addEventListener('timeupdate', function() {
                                if (v.currentTime > 0.5) notifyPlaying();
                            });
                        }
                    });
                }
                // 2. Reproduce elementos <video> nativos
                function tryPlay() {
                    attachListeners();
                    document.querySelectorAll('video').forEach(function(v) {
                        v.muted = false; v.volume = 1.0;
                        v.play().catch(function() {
                            v.muted = true;
                            v.play().then(function() {
                                setTimeout(function() { v.muted = false; v.volume = 1.0; }, 300);
                            }).catch(function(){});
                        });
                    });
                    // 3. Click en botones de play y "activar sonido"
                    ['.vjs-big-play-button','.jw-display-click','.jwplayer .jw-controls-backdrop',
                     '.plyr__control--overlaid','.fp-ui','.flowplayer',
                     '[class*="play-btn"]','[class*="playBtn"]','[class*="play_btn"]',
                     '[class*="btn-play"]','[class*="btn_play"]','[class*="play-button"]',
                     '[class*="play-overlay"]','[class*="overlay-play"]',
                     '[id*="play-btn"]','[id*="playBtn"]','[id*="play_button"]',
                     '[aria-label="Play"]','[aria-label="Reproducir"]',
                     '[title="Play"]','[title="Reproducir"]',
                     '.ytp-large-play-button','.icon-play','.fa-play','.bi-play-fill'
                    ].forEach(function(sel) {
                        try { document.querySelectorAll(sel).forEach(function(el) {
                            el.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));
                        }); } catch(e) {}
                    });
                    // 3b. Click en cualquier elemento que contenga texto "activar sonido", "click", "sonido", "unmute"
                    try {
                        var allEls = document.querySelectorAll('button,div,span,a,p');
                        allEls.forEach(function(el) {
                            var t = (el.innerText || el.textContent || '').toLowerCase();
                            if ((t.includes('activar') || t.includes('sonido') || t.includes('unmute') ||
                                 t.includes('click para') || t.includes('haz clic') || t.includes('press to')) &&
                                t.length < 60) {
                                el.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));
                            }
                        });
                    } catch(e) {}
                    // 3c. Desmutea el contexto de audio del navegador si estaba suspendido
                    try {
                        if (window.AudioContext || window.webkitAudioContext) {
                            var ac = new (window.AudioContext || window.webkitAudioContext)();
                            if (ac.state === 'suspended') ac.resume();
                        }
                    } catch(e) {}
                    // 4. Quita overlays que bloqueen el click
                    ['[class*="overlay"]','[class*="screen-cover"]','[class*="click-catcher"]',
                     '[class*="sound-btn"]','[class*="soundBtn"]','[class*="activate"]'
                    ].forEach(function(sel) {
                        try { document.querySelectorAll(sel).forEach(function(el) {
                            if (el.style && !el.querySelector('video')) el.style.pointerEvents='none';
                        }); } catch(e) {}
                    });
                }
                tryPlay();
            })();
        """.trimIndent()
        if (delayMs == 0L) {
            view.evaluateJavascript(js, null)
        } else {
            view.postDelayed({ view.evaluateJavascript(js, null) }, delayMs)
        }
    }

    // Escanea inputs ocultos buscando URL de stream y la reporta via la interfaz "Android"
    private fun injectStreamUrlScan(view: WebView, delayMs: Long) {
        val js = """
            (function() {
                var inputs = document.querySelectorAll('input');
                for (var i = 0; i < inputs.length; i++) {
                    var val = inputs[i].value;
                    if (val && (val.includes('.m3u8') || val.includes('stream') || val.includes('.php?stream'))) {
                        try { Android.onStreamUrlFound(val); } catch(e) {}
                        break;
                    }
                }
            })();
        """.trimIndent()
        if (delayMs == 0L) {
            view.evaluateJavascript(js, null)
        } else {
            view.postDelayed({ view.evaluateJavascript(js, null) }, delayMs)
        }
    }

    /** Detecta si la página muestra "use iframe" y carga el reproductor en un iframe. */
    private fun injectIframeDetection(view: WebView, delayMs: Long) {
        val js = """
            (function() {
                if (typeof AndroidIframe === 'undefined') return;
                var body = document.body;
                if (!body) return;
                var text = (body.innerText || body.textContent || '').toLowerCase();
                // Mensajes típicos cuando el player requiere contexto de iframe
                var needsIframe = text.includes('use iframe') ||
                                  text.includes('usa iframe') ||
                                  text.includes('usar iframe') ||
                                  text.includes('iframe to load') ||
                                  text.includes('load in iframe') ||
                                  text.includes('load this player') ||
                                  text.includes('cargar en iframe');
                // Solo actuar si la página es muy simple (solo texto de aviso, sin contenido real)
                var hasVideo   = document.querySelectorAll('video').length > 0;
                var hasCanvas  = document.querySelectorAll('canvas').length > 0;
                if (needsIframe && !hasVideo && !hasCanvas) {
                    try { AndroidIframe.onNeedIframe(window.location.href); } catch(e) {}
                }
            })();
        """.trimIndent()
        if (delayMs == 0L) view.evaluateJavascript(js, null)
        else view.postDelayed({ view.evaluateJavascript(js, null) }, delayMs)
    }

    /** Recarga la URL embebida dentro de un iframe HTML para satisfacer players que lo requieren. */
    private fun playAsIframe(url: String) {
        showLoading(true)
        showStatus("Cargando en iframe...")
        val baseUrl = try {
            val uri = android.net.Uri.parse(url)
            "${uri.scheme ?: "https"}://${uri.host ?: ""}"
        } catch (e: Exception) { "https://streamhdx.com" }

        val safeUrl = url.replace("\"", "&quot;")
        val html = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
*{margin:0;padding:0;border:0;overflow:hidden}
html,body{width:100%;height:100%;background:#000;display:block}
iframe{position:fixed;top:0;left:0;width:100%;height:100%;border:none}
</style>
</head>
<body>
<iframe src="$safeUrl"
  allowfullscreen
  allow="autoplay;encrypted-media;picture-in-picture;fullscreen"
  scrolling="no"
  frameborder="0">
</iframe>
</body>
</html>"""
        // Reset timeout para que el iframe tenga su propio tiempo de carga
        rootLayout?.removeCallbacks(webViewTimeoutRunnable)
        rootLayout?.postDelayed(webViewTimeoutRunnable, WEBVIEW_TIMEOUT_MS)
        webView?.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
    }

    private fun buildErrorLayout(): LinearLayout {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#CC000000"))
            visibility = View.GONE
        }

        val icon = TextView(this).apply {
            text = "⚠"
            textSize = 52f
            setTextColor(Color.parseColor("#E53935"))
            gravity = android.view.Gravity.CENTER
        }
        layout.addView(icon, rowLp(bottomMargin = dp(16)))

        val msgView = TextView(this).apply {
            tag = "error_msg"
            text = "Stream no disponible"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = android.view.Gravity.CENTER
            setPadding(dp(48), 0, dp(48), 0)
        }
        layout.addView(msgView, rowLp(bottomMargin = dp(32)))

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val btnRetry = buildTextButton("↻  OTRO SERVIDOR", "#1565C0", "#42A5F5") { tryNextServer() }
        val btnExit  = buildTextButton("✕  SALIR", "#B71C1C", "#EF5350") { finish() }

        btnRow.addView(btnRetry, rowLp(endMargin = dp(20)))
        btnRow.addView(btnExit)
        layout.addView(btnRow)
        return layout
    }

    private fun buildTextButton(
        label: String,
        bgColor: String,
        focusBgColor: String,
        onClick: () -> Unit
    ): TextView = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 16f
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.parseColor(bgColor))
        setPadding(dp(28), dp(14), dp(28), dp(14))
        setTypeface(null, Typeface.BOLD)
        setOnClickListener { onClick() }
        setOnFocusChangeListener { v, hasFocus ->
            (v as TextView).setBackgroundColor(
                Color.parseColor(if (hasFocus) focusBgColor else bgColor)
            )
        }
    }

    private fun buildOverlay(): LinearLayout {
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility  = View.GONE
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = LinearLayout.FOCUS_BLOCK_DESCENDANTS
            setBackgroundColor(Color.TRANSPARENT)
        }

        // ── Barra superior ────────────────────────────────────────────────────
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isFocusable = false
            setBackgroundColor(Color.parseColor("#E6000000"))
            setPadding(dp(40), dp(18), dp(40), dp(18))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val tvChannel = TextView(this).apply {
            text = channelName
            setTextColor(Color.WHITE)
            textSize = 26f
            setTypeface(null, Typeface.BOLD)
        }
        topBar.addView(tvChannel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val tvServer = TextView(this).apply {
            text = serverLabels.getOrElse(currentIndex) { "Servidor" }
            setTextColor(Color.parseColor("#90CAF9"))
            textSize = 16f
            tag = "server_label"
        }
        topBar.addView(tvServer)
        overlay.addView(topBar, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        // Spacer
        overlay.addView(View(this), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        // ── Barra inferior con botones de servidor ────────────────────────────
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = LinearLayout.FOCUS_BLOCK_DESCENDANTS
            setBackgroundColor(Color.parseColor("#E6000000"))
            setPadding(dp(40), dp(16), dp(40), dp(16))
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val tvLabel = TextView(this).apply {
            text = "SERVIDOR:"
            setTextColor(Color.parseColor("#78909C"))
            textSize = 13f
            letterSpacing = 0.10f
            setPadding(0, 0, dp(20), 0)
            isFocusable = false
        }
        bottomBar.addView(tvLabel)

        serverLabels.forEachIndexed { index, label ->
            val btn = TextView(this).apply {
                text = label
                textSize = 16f
                isFocusable = false          // Navegación 100% manual vía onKeyDown
                isFocusableInTouchMode = false
                setPadding(dp(22), dp(12), dp(22), dp(12))
                setTypeface(null, Typeface.BOLD)
                updateServerButtonStyle(this, index == currentIndex, false)
                tag = "server_btn_$index"
                setOnClickListener { switchServer(index) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(10) }
            bottomBar.addView(btn, lp)
        }

        bottomBar.addView(View(this), LinearLayout.LayoutParams(0, 0, 1f))

        val btnExit = TextView(this).apply {
            text = "✕  SALIR"
            setTextColor(Color.parseColor("#EF9A9A"))
            textSize = 14f
            isFocusable = false
            isFocusableInTouchMode = false
            setPadding(dp(20), dp(12), dp(8), dp(12))
            tag = "exit_btn"
            setOnClickListener { finish() }
        }
        bottomBar.addView(btnExit)
        overlay.addView(bottomBar, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        return overlay
    }

    /**
     * Mueve el cursor del overlay al índice dado.
     * 0..serverLabels.size-1 = botones de servidor; serverLabels.size = botón Salir.
     * No usa el sistema de foco de Android — actualiza los estilos directamente.
     */
    private fun moveOverlayFocus(index: Int) {
        overlayFocusIndex = index.coerceIn(0, serverLabels.size)

        // Actualizar estilo de cada botón de servidor
        serverLabels.forEachIndexed { i, _ ->
            overlayLayout?.findViewWithTag<TextView>("server_btn_$i")?.let {
                updateServerButtonStyle(it, i == currentIndex, i == overlayFocusIndex)
            }
        }

        // Actualizar botón Salir
        overlayLayout?.findViewWithTag<TextView>("exit_btn")?.let { exitBtn ->
            if (overlayFocusIndex == serverLabels.size) {
                exitBtn.setBackgroundColor(Color.parseColor("#FFD600"))
                exitBtn.setTextColor(Color.BLACK)
                exitBtn.setTypeface(null, Typeface.BOLD)
                exitBtn.scaleX = 1.10f; exitBtn.scaleY = 1.10f
            } else {
                exitBtn.setBackgroundColor(Color.TRANSPARENT)
                exitBtn.setTextColor(Color.parseColor("#EF9A9A"))
                exitBtn.setTypeface(null, Typeface.NORMAL)
                exitBtn.scaleX = 1.0f; exitBtn.scaleY = 1.0f
            }
        }

        scheduleHide()
    }

    private fun updateServerButtonStyle(btn: TextView, isActive: Boolean, hasFocus: Boolean) {
        when {
            isActive && hasFocus -> {
                // Activo + seleccionado: blanco con texto rojo — máximo contraste
                btn.setBackgroundColor(Color.WHITE)
                btn.setTextColor(Color.parseColor("#C62828"))
                btn.setTypeface(null, android.graphics.Typeface.BOLD)
                btn.scaleX = 1.10f; btn.scaleY = 1.10f
            }
            isActive -> {
                btn.setBackgroundColor(Color.parseColor("#C62828"))
                btn.setTextColor(Color.WHITE)
                btn.setTypeface(null, android.graphics.Typeface.BOLD)
                btn.scaleX = 1.0f; btn.scaleY = 1.0f
            }
            hasFocus -> {
                // Foco sin ser activo: amarillo para máxima visibilidad sobre el video
                btn.setBackgroundColor(Color.parseColor("#FFD600"))
                btn.setTextColor(Color.parseColor("#000000"))
                btn.setTypeface(null, android.graphics.Typeface.BOLD)
                btn.scaleX = 1.10f; btn.scaleY = 1.10f
            }
            else -> {
                btn.setBackgroundColor(Color.parseColor("#1E2D45"))
                btn.setTextColor(Color.parseColor("#B0BEC5"))
                btn.setTypeface(null, android.graphics.Typeface.NORMAL)
                btn.scaleX = 1.0f; btn.scaleY = 1.0f
            }
        }
    }

    // ─── Stream loading ───────────────────────────────────────────────────────

    private fun loadServer(index: Int) {
        val url = serverUrls.getOrElse(index) { "" }
        if (url.isBlank()) { tryNextServerAuto(); return }

        sameServerRetries = 0
        hideError()
        val label = serverLabels.getOrElse(index) { "Servidor ${index + 1}" }
        val total = serverUrls.size
        overlayLayout?.findViewWithTag<TextView>("server_label")?.text = label
        serverLabels.forEachIndexed { i, _ ->
            overlayLayout?.findViewWithTag<TextView>("server_btn_$i")?.let {
                updateServerButtonStyle(it, i == index, false)
            }
        }

        // Muestra estado solo si hay más de un servidor (para que se vea el intento)
        if (total > 1) showStatus("Probando ${label} (${ index + 1}/$total)...")

        if (isDirectStream(url)) playWithExoPlayer(url) else playWithWebView(url)
    }

    /** Avanza al siguiente servidor automáticamente; muestra error solo si se agotaron todos. */
    private fun tryNextServerAuto() {
        val next = currentIndex + 1
        if (next < serverUrls.size) {
            currentIndex = next
            hideError()
            loadServer(currentIndex)
        } else {
            hideStatus()
            showError("Sin señal — todos los servidores fallaron")
        }
    }

    private fun isDirectStream(url: String): Boolean =
        url.contains(".m3u8", ignoreCase = true) ||
        url.contains(".mpd", ignoreCase = true) ||
        url.endsWith(".ts", ignoreCase = true)

    private fun playWithExoPlayer(url: String, headers: Map<String, String> = emptyMap()) {
        rootLayout?.removeCallbacks(webViewTimeoutRunnable)
        rootLayout?.removeCallbacks(stallWatchdogRunnable)
        // Navegar a about:blank para que Clappr detenga completamente su audio.
        // webView?.onPause() solo suspende timers pero no garantiza silenciar el stream.
        webView?.stopLoading()
        webView?.loadUrl("about:blank")
        webView?.onPause()
        webView?.visibility = View.GONE
        exoPlayerView?.visibility = View.VISIBLE
        exoWasPlaying = false
        currentExoUrl     = url
        currentExoHeaders = headers
        showLoading(true)

        exoPlayer?.release()
        // Resetear contadores del detector de freeze para la nueva sesión de reproducción
        rootLayout?.removeCallbacks(videoFreezeCheckRunnable)
        lastRenderedFrames = 0
        frameStallCount    = 0

        // ── Buffer: más margen mínimo evita el freeze periódico en HLS live ─────
        // minBuffer 30 s: ExoPlayer siempre mantiene 30 s de segmentos adelante,
        // así el refresh del playlist no deja nunca un gap vacío.
        // bufferForPlaybackAfterRebufferMs 5 s: tras un corte, espera 5 s de datos
        // antes de reanudar (evita micro-freezes inmediatos por volver demasiado pronto).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs                      */ 30_000,
                /* maxBufferMs                      */ 90_000,
                /* bufferForPlaybackMs              */  2_000,
                /* bufferForPlaybackAfterRebufferMs */  5_000
            )
            .build()

        // ── Live speed control: velocidad fija 1.0× ──────────────────────────
        // Por defecto ExoPlayer acelera a 1.02–1.10× para «ponerse al día» con el
        // live edge después de un buffering. Esa aceleración/desaceleración causa
        // micro-stutters visibles cada vez que el player reajusta.
        // Con min/max = 1.0 el video siempre corre a velocidad normal.
        val liveSpeedControl = DefaultLivePlaybackSpeedControl.Builder()
            .setFallbackMinPlaybackSpeed(1.0f)
            .setFallbackMaxPlaybackSpeed(1.0f)
            .build()

        val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
            setConnectTimeoutMs(15_000)
            setReadTimeoutMs(20_000)
            if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
        }

        val playerBuilder = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setLivePlaybackSpeedControl(liveSpeedControl)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))

        // ── MediaItem con configuración live para HLS ─────────────────────────
        // targetOffsetMs 10 s: reproducir 10 s por detrás del live edge.
        // Esto garantiza que siempre haya varios segmentos HLS disponibles en el
        // playlist antes de que el player los necesite, eliminando el gap que
        // produce el freeze periódico.
        // Velocidad min/max = 1.0 también a nivel de MediaItem (doble seguro).
        val mediaItem = if (url.contains(".m3u8", ignoreCase = true)) {
            MediaItem.Builder()
                .setUri(url)
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(10_000)
                        .setMinOffsetMs(5_000)
                        .setMaxOffsetMs(30_000)
                        .setMinPlaybackSpeed(1.0f)
                        .setMaxPlaybackSpeed(1.0f)
                        .build()
                )
                .build()
        } else {
            MediaItem.fromUri(url)
        }

        exoPlayer = playerBuilder.build().also { player ->
            exoPlayerView?.player = player
            player.setMediaItem(mediaItem)
            player.prepare()
            // Sin forceHighestSupportedBitrate: ExoPlayer elige calidad de forma adaptativa
            // según el ancho de banda disponible, igual que hace el reproductor web.
            player.playWhenReady = true
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    when (state) {
                        Player.STATE_READY -> {
                            exoWasPlaying = true
                            sameServerRetries = 0
                            showLoading(false)
                            hideStatus()
                            // Stream recuperado: cancelar stall watchdog
                            rootLayout?.removeCallbacks(stallWatchdogRunnable)
                            // Iniciar detector de video congelado (frames detenidos con audio activo)
                            rootLayout?.removeCallbacks(videoFreezeCheckRunnable)
                            lastRenderedFrames = 0
                            frameStallCount    = 0
                            rootLayout?.postDelayed(videoFreezeCheckRunnable, FRAME_CHECK_INTERVAL_MS)
                        }
                        Player.STATE_BUFFERING -> {
                            showLoading(true)
                            // Pausar detector de freeze mientras hay buffering normal
                            rootLayout?.removeCallbacks(videoFreezeCheckRunnable)
                            if (exoWasPlaying) {
                                // Stream se congeló durante la reproducción — iniciar watchdog.
                                // Solo cambia de servidor si no se recupera en STALL_TIMEOUT_MS (60 s).
                                rootLayout?.removeCallbacks(stallWatchdogRunnable)
                                rootLayout?.postDelayed(stallWatchdogRunnable, STALL_TIMEOUT_MS)
                            }
                        }
                        Player.STATE_ENDED -> {
                            rootLayout?.removeCallbacks(stallWatchdogRunnable)
                            rootLayout?.removeCallbacks(videoFreezeCheckRunnable)
                            showLoading(false)
                        }
                        else -> {}
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    showLoading(false)
                    rootLayout?.removeCallbacks(stallWatchdogRunnable)
                    rootLayout?.removeCallbacks(videoFreezeCheckRunnable)
                    // Si ya estaba reproduciendo y no superamos el límite de reintentos,
                    // reintentamos el mismo servidor (los errores de segmento HLS son transitorios).
                    if (exoWasPlaying && sameServerRetries < MAX_SAME_SERVER_RETRIES) {
                        sameServerRetries++
                        val retryUrl     = currentExoUrl
                        val retryHeaders = currentExoHeaders
                        runOnUiThread {
                            showStatus("Reconectando...")
                            rootLayout?.postDelayed({
                                if (retryUrl.isNotBlank()) playWithExoPlayer(retryUrl, retryHeaders)
                                else loadServer(currentIndex)
                            }, 3_000)
                        }
                    } else {
                        sameServerRetries = 0
                        runOnUiThread { tryNextServerAuto() }
                    }
                }
            })
        }
    }

    private fun playWithWebView(url: String) {
        exoPlayer?.release(); exoPlayer = null
        exoPlayerView?.visibility = View.GONE
        webView?.onResume()
        webView?.visibility = View.VISIBLE

        // Reset detection state and start timeout
        videoStarted  = false
        iframeRetried = false
        rootLayout?.removeCallbacks(webViewTimeoutRunnable)
        rootLayout?.removeCallbacks(videoFreezeCheckRunnable)
        rootLayout?.postDelayed(webViewTimeoutRunnable, WEBVIEW_TIMEOUT_MS)
        showLoading(true)

        webView?.loadUrl(url)
    }

    private fun isWebEmbedUrl(url: String): Boolean =
        !url.contains(".m3u8", ignoreCase = true) &&
        !url.contains(".mpd", ignoreCase = true) &&
        !url.endsWith(".ts", ignoreCase = true)

    private fun tryNextServer() {
        switchServer((currentIndex + 1) % serverUrls.size)
    }

    private fun switchServer(index: Int) {
        currentIndex = index
        hideOverlay()
        hideError()
        loadServer(index)
    }

    // ─── State ────────────────────────────────────────────────────────────────

    private fun showLoading(show: Boolean) {
        loadingBar?.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) hideStatus()
    }

    private fun showStatus(msg: String) {
        statusTextView?.text = msg
        statusTextView?.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        statusTextView?.visibility = View.GONE
    }

    private fun showError(msg: String) {
        errorLayout?.findViewWithTag<TextView>("error_msg")?.text = msg
        errorLayout?.visibility = View.VISIBLE
        // Focus en "OTRO SERVIDOR"
        (errorLayout?.getChildAt(2) as? LinearLayout)?.getChildAt(0)?.requestFocus()
    }

    private fun hideError() {
        errorLayout?.visibility = View.GONE
    }

    // ─── Overlay ──────────────────────────────────────────────────────────────

    private fun showOverlay() {
        overlayLayout?.visibility = View.VISIBLE
        isOverlayVisible = true
        moveOverlayFocus(currentIndex)
    }

    private fun hideOverlay() {
        overlayLayout?.removeCallbacks(hideOverlayRunnable)
        overlayLayout?.visibility = View.GONE
        isOverlayVisible = false
    }

    private fun scheduleHide() {
        overlayLayout?.removeCallbacks(hideOverlayRunnable)
        overlayLayout?.postDelayed(hideOverlayRunnable, 6000)
    }

    // ─── Key events ───────────────────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (isOverlayVisible) {
                    if (overlayFocusIndex < serverUrls.size) {
                        switchServer(overlayFocusIndex)
                    } else {
                        finish()
                    }
                } else {
                    showOverlay()
                }
                true
            }

            KeyEvent.KEYCODE_BACK -> true  // Manejado por OnBackPressedCallback

            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isOverlayVisible) {
                    showOverlay()
                } else {
                    val delta = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                    moveOverlayFocus(overlayFocusIndex + delta)
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!isOverlayVisible) showOverlay() else scheduleHide()
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    // ─── Util ─────────────────────────────────────────────────────────────────

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun matchParent() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    )

    private fun rowLp(bottomMargin: Int = 0, endMargin: Int = 0) =
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            this.bottomMargin = bottomMargin
            this.marginEnd = endMargin
        }
}
