package com.jchshi.readalong

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import org.json.JSONArray

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.statusBarColor = Color.TRANSPARENT

        webView = WebView(this)
        setContentView(createContentView())

        configureWebView()

        webView.loadUrl(getStartUrl())
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST) {
            val results = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            uploadCallback?.onReceiveValue(results)
            uploadCallback = null
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            maybeGrantPendingWebPermission()
        }
    }

    private fun configureWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            builtInZoomControls = false
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return if (uri.scheme == "http" || uri.scheme == "https") {
                    false
                } else {
                    openExternal(uri)
                    true
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                    pendingPermissionRequest = request
                    if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                        maybeGrantPendingWebPermission()
                    } else {
                        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST)
                    }
                } else {
                    request.deny()
                }
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams,
            ): Boolean {
                uploadCallback?.onReceiveValue(null)
                uploadCallback = filePathCallback
                return try {
                    startActivityForResult(fileChooserParams.createIntent(), FILE_CHOOSER_REQUEST)
                    true
                } catch (_: ActivityNotFoundException) {
                    uploadCallback = null
                    Toast.makeText(this@MainActivity, R.string.no_file_picker, Toast.LENGTH_SHORT).show()
                    false
                }
            }
        }

        webView.setDownloadListener(downloadListener)
        webView.setOnLongClickListener {
            showSiteChooser()
            true
        }
    }

    private fun createContentView(): View {
        return FrameLayout(this).apply {
            addView(
                webView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(createSiteHandle(), siteHandleLayoutParams())
        }
    }

    private fun createSiteHandle(): View {
        return View(this).apply {
            contentDescription = getString(R.string.choose_site)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.argb(132, 26, 115, 232))
                cornerRadii = floatArrayOf(
                    0f, 0f,
                    dp(10), dp(10),
                    dp(10), dp(10),
                    0f, 0f,
                )
            }
            elevation = dp(4)
            alpha = 0.72f
            setOnClickListener { showSiteChooser() }
            setOnLongClickListener {
                showUrlManager()
                true
            }
        }
    }

    private fun siteHandleLayoutParams(): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(dpInt(10), dpInt(96)).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }
    }

    private val downloadListener = DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url))
                .addRequestHeader("User-Agent", userAgent)
                .setMimeType(mimeType)
                .setTitle(fileName)
                .setDescription(getString(R.string.downloading))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)

            getSystemService(DownloadManager::class.java).enqueue(request)
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            openExternal(Uri.parse(url))
        }
    }

    private fun showSiteChooser() {
        val sites = getSites()
        val labels = sites.map { "${it.title}\n${it.url}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_site)
            .setItems(labels) { _, index ->
                saveSelectedUrl(sites[index].url)
                webView.loadUrl(sites[index].url)
            }
            .setPositiveButton(R.string.add_site) { _, _ -> showAddSiteDialog() }
            .setNegativeButton(R.string.manage_sites) { _, _ -> showUrlManager() }
            .setNeutralButton(R.string.reload) { _, _ ->
                webView.reload()
            }
            .show()
    }

    private fun showAddSiteDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.site_url_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine(true)
            setPadding(dpInt(20), dpInt(12), dpInt(20), dpInt(12))
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.add_site)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val url = normalizeUrl(input.text.toString())
                if (url == null) {
                    Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val existing = getCustomSiteUrls()
                if (!getSites().any { it.url == url }) {
                    existing += url
                    saveCustomSiteUrls(existing)
                }
                saveSelectedUrl(url)
                webView.loadUrl(url)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showUrlManager() {
        val customUrls = getCustomSiteUrls()
        if (customUrls.isEmpty()) {
            Toast.makeText(this, R.string.no_custom_sites, Toast.LENGTH_SHORT).show()
            showAddSiteDialog()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.delete_custom_site)
            .setItems(customUrls.toTypedArray()) { _, index ->
                val removed = customUrls[index]
                val remaining = customUrls.toMutableList().also { it.removeAt(index) }
                saveCustomSiteUrls(remaining)
                if (getSavedSelectedUrl() == removed) {
                    saveSelectedUrl(AppConfig.sites[AppConfig.defaultSiteIndex].url)
                    webView.loadUrl(AppConfig.sites[AppConfig.defaultSiteIndex].url)
                }
            }
            .setPositiveButton(R.string.add_site) { _, _ -> showAddSiteDialog() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun maybeGrantPendingWebPermission() {
        val request = pendingPermissionRequest ?: return
        pendingPermissionRequest = null

        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
        } else {
            request.deny()
            Toast.makeText(this, R.string.microphone_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    private fun openExternal(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW, uri)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun getSites(): List<WebEntry> {
        val custom = getCustomSiteUrls().map { url ->
            WebEntry(Uri.parse(url).host ?: url, url)
        }
        return (AppConfig.sites + custom).distinctBy { it.url }
    }

    private fun getStartUrl(): String {
        val saved = getSavedSelectedUrl()
        return getSites().firstOrNull { it.url == saved }?.url
            ?: AppConfig.sites[AppConfig.defaultSiteIndex].url
    }

    private fun getSavedSelectedUrl(): String? {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_SELECTED_URL, null)
    }

    private fun saveSelectedUrl(url: String) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_SELECTED_URL, url)
            .apply()
    }

    private fun getCustomSiteUrls(): MutableList<String> {
        val raw = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_CUSTOM_URLS, "[]") ?: "[]"
        val array = JSONArray(raw)
        return MutableList(array.length()) { index -> array.getString(index) }
    }

    private fun saveCustomSiteUrls(urls: List<String>) {
        val array = JSONArray()
        urls.distinct().forEach { array.put(it) }
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_CUSTOM_URLS, array.toString())
            .apply()
    }

    private fun normalizeUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val candidate = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        val uri = Uri.parse(candidate)
        return if ((uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()) {
            uri.toString()
        } else {
            null
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun dp(value: Int): Float = value * resources.displayMetrics.density

    private fun dpInt(value: Int): Int = dp(value).toInt()

    companion object {
        private const val PREFS_NAME = "readalong_web"
        private const val PREF_SELECTED_URL = "selected_url"
        private const val PREF_CUSTOM_URLS = "custom_urls"
        private const val PERMISSION_REQUEST = 100
        private const val FILE_CHOOSER_REQUEST = 101
    }
}
