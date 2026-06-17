package com.jchshi.readalong;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final String PREFS_NAME = "readalong_web";
    private static final String PREF_SELECTED_URL = "selected_url";
    private static final String PREF_CUSTOM_URLS = "custom_urls";
    private static final int PERMISSION_REQUEST = 100;

    private GeckoView geckoView;
    private GeckoSession geckoSession;
    private boolean canGoBack;
    private GeckoSession.PermissionDelegate.Callback permissionCallback;
    private boolean edgeSwipeCandidate;
    private boolean edgeSwipeConsumed;
    private float edgeSwipeStartX;
    private float edgeSwipeStartY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        geckoView = new GeckoView(this);
        setContentView(geckoView);

        configureGeckoView();
        geckoSession.loadUri(getStartUrl());
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                edgeSwipeCandidate = event.getX() <= dp(24);
                edgeSwipeConsumed = false;
                edgeSwipeStartX = event.getX();
                edgeSwipeStartY = event.getY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (edgeSwipeCandidate && !edgeSwipeConsumed) {
                    float deltaX = event.getX() - edgeSwipeStartX;
                    float deltaY = Math.abs(event.getY() - edgeSwipeStartY);
                    if (deltaX >= dp(72) && deltaY <= dp(48)) {
                        edgeSwipeConsumed = true;
                        edgeSwipeCandidate = false;
                        showSiteChooser();
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                edgeSwipeCandidate = false;
                if (edgeSwipeConsumed) {
                    edgeSwipeConsumed = false;
                    return true;
                }
                break;
            default:
                break;
        }

        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
    }

    @Override
    public void onBackPressed() {
        if (canGoBack) {
            geckoSession.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_REQUEST || permissionCallback == null) {
            return;
        }

        GeckoSession.PermissionDelegate.Callback callback = permissionCallback;
        permissionCallback = null;

        boolean granted = grantResults.length > 0;
        for (int result : grantResults) {
            granted = granted && result == PackageManager.PERMISSION_GRANTED;
        }

        if (granted) {
            callback.grant();
        } else {
            callback.reject();
            Toast.makeText(this, R.string.microphone_permission_required, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (geckoSession != null) {
            geckoSession.close();
        }
        super.onDestroy();
    }

    private void configureGeckoView() {
        GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .useTrackingProtection(false)
            .build();

        geckoSession = new GeckoSession(settings);
        geckoSession.setNavigationDelegate(navigationDelegate);
        geckoSession.setPermissionDelegate(permissionDelegate);

        GeckoRuntimeSettings runtimeSettings = new GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .remoteDebuggingEnabled(false)
            .build();

        GeckoRuntime runtime = GeckoRuntime.create(this, runtimeSettings);
        geckoSession.open(runtime);
        geckoView.setSession(geckoSession);

        geckoView.setOnLongClickListener(view -> {
            showSiteChooser();
            return true;
        });
    }

    private final GeckoSession.NavigationDelegate navigationDelegate = new GeckoSession.NavigationDelegate() {
        @Override
        public void onCanGoBack(GeckoSession session, boolean canGoBack) {
            MainActivity.this.canGoBack = canGoBack;
        }

        @Override
        public GeckoResult<AllowOrDeny> onLoadRequest(
            GeckoSession session,
            GeckoSession.NavigationDelegate.LoadRequest request
        ) {
            Uri uri = Uri.parse(request.uri);
            if ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) {
                return GeckoResult.fromValue(AllowOrDeny.ALLOW);
            }

            openExternal(uri);
            return GeckoResult.fromValue(AllowOrDeny.DENY);
        }

        @Override
        public GeckoResult<GeckoSession> onNewSession(GeckoSession session, String uri) {
            geckoSession.loadUri(uri);
            return null;
        }
    };

    private final GeckoSession.PermissionDelegate permissionDelegate = new GeckoSession.PermissionDelegate() {
        @Override
        public void onAndroidPermissionsRequest(
            GeckoSession session,
            String[] permissions,
            GeckoSession.PermissionDelegate.Callback callback
        ) {
            List<String> missing = new ArrayList<>();
            if (permissions != null) {
                for (String permission : permissions) {
                    if (!Manifest.permission.RECORD_AUDIO.equals(permission)) {
                        callback.reject();
                        return;
                    }
                    if (!hasPermission(permission)) {
                        missing.add(permission);
                    }
                }
            }

            if (missing.isEmpty()) {
                callback.grant();
            } else {
                permissionCallback = callback;
                requestPermissions(missing.toArray(new String[0]), PERMISSION_REQUEST);
            }
        }

        @Override
        public GeckoResult<Integer> onContentPermissionRequest(
            GeckoSession session,
            GeckoSession.PermissionDelegate.ContentPermission perm
        ) {
            return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW);
        }

        @Override
        public void onMediaPermissionRequest(
            GeckoSession session,
            String uri,
            GeckoSession.PermissionDelegate.MediaSource[] video,
            GeckoSession.PermissionDelegate.MediaSource[] audio,
            GeckoSession.PermissionDelegate.MediaCallback callback
        ) {
            GeckoSession.PermissionDelegate.MediaSource microphone = null;
            if (audio != null) {
                for (GeckoSession.PermissionDelegate.MediaSource source : audio) {
                    if (source.source == GeckoSession.PermissionDelegate.MediaSource.SOURCE_MICROPHONE) {
                        microphone = source;
                        break;
                    }
                }
                if (microphone == null && audio.length > 0) {
                    microphone = audio[0];
                }
            }

            if (video != null || microphone == null) {
                callback.reject();
                return;
            }

            callback.grant(null, microphone);
        }
    };

    private void showSiteChooser() {
        List<WebEntry> sites = getSites();
        String[] labels = new String[sites.size()];
        for (int i = 0; i < sites.size(); i++) {
            WebEntry site = sites.get(i);
            labels[i] = site.title + "\n" + site.url;
        }

        new AlertDialog.Builder(this)
            .setTitle(R.string.choose_site)
            .setItems(labels, (dialog, index) -> {
                WebEntry site = sites.get(index);
                saveSelectedUrl(site.url);
                geckoSession.loadUri(site.url);
            })
            .setPositiveButton(R.string.add_site, (dialog, which) -> showAddSiteDialog())
            .setNegativeButton(R.string.manage_sites, (dialog, which) -> showUrlManager())
            .setNeutralButton(R.string.reload, (dialog, which) -> geckoSession.reload())
            .show();
    }

    private void showAddSiteDialog() {
        EditText input = new EditText(this);
        input.setHint(getString(R.string.site_url_hint));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        input.setPadding(dpInt(20), dpInt(12), dpInt(20), dpInt(12));

        new AlertDialog.Builder(this)
            .setTitle(R.string.add_site)
            .setView(input)
            .setPositiveButton(R.string.save, (dialog, which) -> {
                String url = normalizeUrl(input.getText().toString());
                if (url == null) {
                    Toast.makeText(this, R.string.invalid_url, Toast.LENGTH_SHORT).show();
                    return;
                }

                List<String> existing = getCustomSiteUrls();
                boolean exists = false;
                for (WebEntry site : getSites()) {
                    exists = exists || site.url.equals(url);
                }
                if (!exists) {
                    existing.add(url);
                    saveCustomSiteUrls(existing);
                }
                saveSelectedUrl(url);
                geckoSession.loadUri(url);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showUrlManager() {
        List<String> customUrls = getCustomSiteUrls();
        if (customUrls.isEmpty()) {
            Toast.makeText(this, R.string.no_custom_sites, Toast.LENGTH_SHORT).show();
            showAddSiteDialog();
            return;
        }

        String[] labels = customUrls.toArray(new String[0]);
        new AlertDialog.Builder(this)
            .setTitle(R.string.delete_custom_site)
            .setItems(labels, (dialog, index) -> {
                String removed = customUrls.get(index);
                customUrls.remove(index);
                saveCustomSiteUrls(customUrls);
                if (removed.equals(getSavedSelectedUrl())) {
                    String defaultUrl = AppConfig.SITES.get(AppConfig.DEFAULT_SITE_INDEX).url;
                    saveSelectedUrl(defaultUrl);
                    geckoSession.loadUri(defaultUrl);
                }
            })
            .setPositiveButton(R.string.add_site, (dialog, which) -> showAddSiteDialog())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    private void openExternal(Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private List<WebEntry> getSites() {
        Map<String, WebEntry> sites = new LinkedHashMap<>();
        for (WebEntry site : AppConfig.SITES) {
            sites.put(site.url, site);
        }
        for (String url : getCustomSiteUrls()) {
            Uri uri = Uri.parse(url);
            String host = uri.getHost() == null ? url : uri.getHost();
            sites.put(url, new WebEntry(host, url));
        }
        return new ArrayList<>(sites.values());
    }

    private String getStartUrl() {
        String saved = getSavedSelectedUrl();
        if (saved != null) {
            for (WebEntry site : getSites()) {
                if (site.url.equals(saved)) {
                    return site.url;
                }
            }
        }
        return AppConfig.SITES.get(AppConfig.DEFAULT_SITE_INDEX).url;
    }

    private String getSavedSelectedUrl() {
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(PREF_SELECTED_URL, null);
    }

    private void saveSelectedUrl(String url) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_SELECTED_URL, url)
            .apply();
    }

    private List<String> getCustomSiteUrls() {
        String raw = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(PREF_CUSTOM_URLS, "[]");
        List<String> urls = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < array.length(); i++) {
                urls.add(array.getString(i));
            }
        } catch (JSONException ignored) {
        }
        return urls;
    }

    private void saveCustomSiteUrls(List<String> urls) {
        JSONArray array = new JSONArray();
        for (String url : urls) {
            boolean seen = false;
            for (int i = 0; i < array.length(); i++) {
                seen = seen || url.equals(array.optString(i));
            }
            if (!seen) {
                array.put(url);
            }
        }

        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_CUSTOM_URLS, array.toString())
            .apply();
    }

    private String normalizeUrl(String input) {
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String candidate = trimmed.startsWith("http://") || trimmed.startsWith("https://")
            ? trimmed
            : "https://" + trimmed;

        Uri uri = Uri.parse(candidate);
        if (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null) {
            return uri.toString();
        }
        return null;
    }

    private boolean hasPermission(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private int dpInt(int value) {
        return (int) dp(value);
    }
}
