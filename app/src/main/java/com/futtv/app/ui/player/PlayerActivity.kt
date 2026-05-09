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
import androidx.media3.exoplayer.ExoPlayer
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

    // WebView stream detection
    private var videoStarted = false
    private val webViewTimeoutRunnable = Runnable {
        if (!videoStarted) {
            runOnUiThread { tryNextServerAuto() }
        }
    }
    private val WEBVIEW_TIMEOUT_MS = 20_000L

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
                        playWithExoPlayer(url)
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
                    android.util.Log.d("FutTV_Player", "WebView loading: $url")
                    showLoading(true)
                    hideError()
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    showLoading(false)
                    injectAutoplay(view, 0)
                    injectAutoplay(view, 800)
                    injectAutoplay(view, 2000)
                    injectAutoplay(view, 4000)
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
            setBackgroundColor(Color.TRANSPARENT)
        }

        // ── Barra superior ────────────────────────────────────────────────────
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
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
        }
        bottomBar.addView(tvLabel)

        serverLabels.forEachIndexed { index, label ->
            val btn = TextView(this).apply {
                text = label
                textSize = 16f
                isFocusable = true
                isFocusableInTouchMode = true
                setPadding(dp(22), dp(12), dp(22), dp(12))
                setTypeface(null, Typeface.BOLD)
                updateServerButtonStyle(this, index == currentIndex, false)
                tag = "server_btn_$index"
                setOnClickListener { switchServer(index) }
                setOnFocusChangeListener { v, hasFocus ->
                    updateServerButtonStyle(v as TextView, index == currentIndex, hasFocus)
                    if (hasFocus) scheduleHide()
                }
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
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dp(20), dp(12), dp(8), dp(12))
            setOnClickListener { finish() }
            setOnFocusChangeListener { _, hasFocus ->
                setTextColor(if (hasFocus) Color.WHITE else Color.parseColor("#EF9A9A"))
                if (hasFocus) scheduleHide()
            }
        }
        bottomBar.addView(btnExit)
        overlay.addView(bottomBar, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        return overlay
    }

    private fun updateServerButtonStyle(btn: TextView, isActive: Boolean, hasFocus: Boolean) {
        when {
            isActive -> { btn.setBackgroundColor(Color.parseColor("#C62828")); btn.setTextColor(Color.WHITE) }
            hasFocus -> { btn.setBackgroundColor(Color.parseColor("#1565C0")); btn.setTextColor(Color.WHITE) }
            else     -> { btn.setBackgroundColor(Color.parseColor("#1E2D45")); btn.setTextColor(Color.parseColor("#B0BEC5")) }
        }
    }

    // ─── Stream loading ───────────────────────────────────────────────────────

    private fun loadServer(index: Int) {
        val url = serverUrls.getOrElse(index) { "" }
        if (url.isBlank()) { tryNextServerAuto(); return }

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

    private fun playWithExoPlayer(url: String) {
        rootLayout?.removeCallbacks(webViewTimeoutRunnable)
        webView?.visibility = View.GONE
        exoPlayerView?.visibility = View.VISIBLE
        showLoading(true)

        exoPlayer?.release()
        exoPlayer = ExoPlayer.Builder(this).build().also { player ->
            exoPlayerView?.player = player
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setForceHighestSupportedBitrate(true)
                .build()
            player.playWhenReady = true
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) showLoading(false)
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    showLoading(false)
                    runOnUiThread { tryNextServerAuto() }
                }
            })
        }
    }

    private fun playWithWebView(url: String) {
        exoPlayer?.release(); exoPlayer = null
        exoPlayerView?.visibility = View.GONE
        webView?.visibility = View.VISIBLE

        // Reset detection state and start timeout
        videoStarted = false
        rootLayout?.removeCallbacks(webViewTimeoutRunnable)
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
        // FIX: prevent WebView from stealing D-pad events while overlay is visible
        webView?.isFocusable = false
        webView?.isFocusableInTouchMode = false

        overlayLayout?.visibility = View.VISIBLE
        isOverlayVisible = true
        overlayLayout?.findViewWithTag<TextView>("server_btn_$currentIndex")?.requestFocus()
        scheduleHide()
    }

    private fun hideOverlay() {
        overlayLayout?.removeCallbacks(hideOverlayRunnable)
        overlayLayout?.visibility = View.GONE
        isOverlayVisible = false

        // Restore WebView focusability
        webView?.isFocusable = true
        webView?.isFocusableInTouchMode = true
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
                if (isOverlayVisible) hideOverlay() else showOverlay()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                // Consumed here; OnBackPressedCallback in onCreate handles the logic exclusively
                true
            }
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!isOverlayVisible) {
                    showOverlay()
                } else {
                    // Navigate between overlay buttons, then always consume
                    // so D-pad NEVER reaches the WebView
                    super.onKeyDown(keyCode, event)
                    scheduleHide()
                }
                true // Always consume D-pad — prevents WebView interference
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
