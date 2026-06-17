package com.jchshi.readalong

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.Toast
import org.json.JSONArray
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import kotlin.math.abs

class MainActivity : Activity() {
    private lateinit var geckoView: GeckoView
    private lateinit var geckoSession: GeckoSession
    private var canGoBack = false
    private var permissionCallback: GeckoSession.PermissionDelegate.Callback? = null
    private var edgeSwipeCandidate = false
    private var edgeSwipeConsumed = false
    private var edgeSwipeStartX = 0f
    private var edgeSwipeStartY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        window.statusBarColor = Color.TRANSPARENT

        geckoView = GeckoView(this)
        setContentView(geckoView)

        configureGeckoView()
        geckoSession.loadUri(getStartUrl())
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                edgeSwipeCandidate = event.x <= dp(24)
                edgeSwipeConsumed = false
                edgeSwipeStartX = event.x
                edgeSwipeStartY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (edgeSwipeCandidate && !edgeSwipeConsumed) {
                    val deltaX = event.x - edgeSwipeStartX
                    val deltaY = abs(event.y - edgeSwipeStartY)
                    if (deltaX >= dp(72) && deltaY <= dp(48)) {
                        edgeSwipeConsumed = true
                        edgeSwipeCandidate = false
                        showSiteChooser()
                        return true
                    }
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL
            -> {
                edgeSwipeCandidate = false
                if (edgeSwipeConsumed) {
                    edgeSwipeConsumed = false
                    return true
                }
            }
        }

        return super.dispatchTouchEvent(event)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
    }

    override fun onBackPressed() {
        if (canGoBack) {
            geckoSession.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != PERMISSION_REQUEST) return

        val callback = permissionCallback ?: return
        permissionCallback = null

        if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            callback.grant()
        } else {
            callback.reject()
            Toast.makeText(this, R.string.microphone_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        geckoSession.close()
        super.onDestroy()
    }

    private fun configureGeckoView() {
        val settings = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .useTrackingProtection(false)
            .build()

        geckoSession = GeckoSession(settings)
        geckoSession.setNavigationDelegate(navigationDelegate)
        geckoSession.setPermissionDelegate(permissionDelegate)

        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .remoteDebuggingEnabled(false)
            .build()

        val runtime = GeckoRuntime.create(this, runtimeSettings)
        geckoSession.open(runtime)
        geckoView.setSession(geckoSession)

        geckoView.setOnLongClickListener {
            showSiteChooser()
            true
        }
    }

    private val navigationDelegate = object : GeckoSession.NavigationDelegate {
        override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
            this@MainActivity.canGoBack = canGoBack
        }

        override fun onLoadRequest(
            session: GeckoSession,
            request: GeckoSession.NavigationDelegate.LoadRequest,
        ): GeckoResult<AllowOrDeny>? {
            val uri = Uri.parse(request.uri)
            return if (uri.scheme == "http" || uri.scheme == "https") {
                GeckoResult.fromValue(AllowOrDeny.ALLOW)
            } else {
                openExternal(uri)
                GeckoResult.fromValue(AllowOrDeny.DENY)
            }
        }

        override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
            geckoSession.loadUri(uri)
            return null
        }
    }

    private val permissionDelegate = object : GeckoSession.PermissionDelegate {
        override fun onAndroidPermissionsRequest(
            session: GeckoSession,
            permissions: Array<String>?,
            callback: GeckoSession.PermissionDelegate.Callback,
        ) {
            val requested = permissions.orEmpty()
            val allowed = requested.filter { it == Manifest.permission.RECORD_AUDIO }.toTypedArray()
            if (allowed.size != requested.size) {
                callback.reject()
                return
            }

            val missing = allowed.filterNot(::hasPermission).toTypedArray()
            if (missing.isEmpty()) {
                callback.grant()
            } else {
                permissionCallback = callback
                requestPermissions(missing, PERMISSION_REQUEST)
            }
        }

        override fun onContentPermissionRequest(
            session: GeckoSession,
            perm: GeckoSession.PermissionDelegate.ContentPermission,
        ): GeckoResult<Int>? {
            return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
        }

        override fun onMediaPermissionRequest(
            session: GeckoSession,
            uri: String,
            video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
            audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
            callback: GeckoSession.PermissionDelegate.MediaCallback,
        ) {
            val microphone = audio?.firstOrNull {
                it.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE
            } ?: audio?.firstOrNull()

            if (video != null || microphone == null) {
                callback.reject()
                return
            }

            callback.grant(null, microphone)
        }
    }

    private fun showSiteChooser() {
        val sites = getSites()
        val labels = sites.map { "${it.title}\n${it.url}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_site)
            .setItems(labels) { _, index ->
                saveSelectedUrl(sites[index].url)
                geckoSession.loadUri(sites[index].url)
            }
            .setPositiveButton(R.string.add_site) { _, _ -> showAddSiteDialog() }
            .setNegativeButton(R.string.manage_sites) { _, _ -> showUrlManager() }
            .setNeutralButton(R.string.reload) { _, _ ->
                geckoSession.reload()
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
                geckoSession.loadUri(url)
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
                    val defaultUrl = AppConfig.sites[AppConfig.defaultSiteIndex].url
                    saveSelectedUrl(defaultUrl)
                    geckoSession.loadUri(defaultUrl)
                }
            }
            .setPositiveButton(R.string.add_site) { _, _ -> showAddSiteDialog() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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
    }
}
