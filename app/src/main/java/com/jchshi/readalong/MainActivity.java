package com.jchshi.readalong;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabsIntent;

public final class MainActivity extends Activity {
    private static final String CHROME_PACKAGE = "com.android.chrome";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setStatusBarColor(Color.TRANSPARENT);

        if (AppConfig.SITES.size() <= 1) {
            openUrl(AppConfig.SITES.get(AppConfig.DEFAULT_SITE_INDEX).url);
        } else {
            showSiteChooser();
        }
    }

    private void showSiteChooser() {
        String[] labels = new String[AppConfig.SITES.size()];
        for (int i = 0; i < AppConfig.SITES.size(); i++) {
            WebEntry site = AppConfig.SITES.get(i);
            labels[i] = site.title + "\n" + site.url;
        }

        new AlertDialog.Builder(this)
            .setTitle(R.string.choose_site)
            .setItems(labels, (dialog, index) -> openUrl(AppConfig.SITES.get(index).url))
            .setOnCancelListener(dialog -> finish())
            .show();
    }

    private void openUrl(String url) {
        Uri uri = Uri.parse(url);
        CustomTabsIntent chromeTab = new CustomTabsIntent.Builder()
            .setShowTitle(false)
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build();
        chromeTab.intent.setPackage(CHROME_PACKAGE);

        try {
            chromeTab.launchUrl(this, uri);
            finish();
            return;
        } catch (ActivityNotFoundException ignored) {
        }

        CustomTabsIntent fallbackTab = new CustomTabsIntent.Builder()
            .setShowTitle(false)
            .setUrlBarHidingEnabled(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build();
        try {
            fallbackTab.launchUrl(this, uri);
        } catch (ActivityNotFoundException ignored) {
            openExternalBrowser(uri);
        } finally {
            finish();
        }
    }

    private void openExternalBrowser(Uri uri) {
        Intent fallbackIntent = new Intent(Intent.ACTION_VIEW, uri)
            .addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(fallbackIntent);
        } catch (ActivityNotFoundException ignored) {
            Toast.makeText(this, R.string.no_supported_browser, Toast.LENGTH_LONG).show();
        }
    }
}
