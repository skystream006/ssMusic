package com.skystream.ssmusic;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Hosts a single Chromium-backed WebView for YouTube Music. */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "ssmusic_prefs";
    private static final String KEY_THEME = "theme";
    private static final String KEY_DESKTOP_MODE = "desktop_mode";
    private static final String KEY_LAST_URL = "last_url";
    private static final int REQUEST_APP_PERMISSIONS = 1001;
    private static final int REQUEST_WEB_PERMISSIONS = 1002;

    static final String AD_HIDING_SCRIPT =
            "(function(){"
                    + "var id='ssmusic-adblock';"
                    + "var css='ytmusic-mealbar-promo-renderer,"
                    + "ytmusic-statement-banner-renderer,"
                    + "ytmusic-promo-panel-renderer,"
                    + "ytmusic-you-there-renderer,"
                    + "ytd-ad-slot-renderer,"
                    + "ytm-promoted-video-renderer,"
                    + ".ytp-ad-module,"
                    + ".video-ads,"
                    + ".ytp-ad-overlay-container{display:none!important;}';"
                    + "function apply(){"
                    + "if(document.getElementById(id)){return true;}"
                    + "var parent=document.head||document.documentElement;"
                    + "if(!parent){return false;}"
                    + "var style=document.createElement('style');"
                    + "style.id=id;"
                    + "style.textContent=css;"
                    + "parent.appendChild(style);"
                    + "return true;"
                    + "}"
                    + "if(!apply()){setTimeout(apply,50);}"
                    + "})()";

    static final String AD_JSON_PRUNE_SCRIPT =
            "(function(){"
                    + "if(window.__ssmusicJsonPruneInstalled){return;}"
                    + "window.__ssmusicJsonPruneInstalled=true;"
                    + "var keys=['playerAds','adPlacements','adSlots','adBreakHeartbeatParams',"
                    + "'playerAdParams','adPlacementConfig','adBreakParams'];"
                    + "function prune(value,depth){"
                    + "if(!value||typeof value!=='object'||depth>8){return;}"
                    + "if(Array.isArray(value)){for(var i=0;i<value.length;i++){prune(value[i],depth+1);}return;}"
                    + "for(var j=0;j<keys.length;j++){if(keys[j] in value){delete value[keys[j]];}}"
                    + "for(var key in value){if(Object.prototype.hasOwnProperty.call(value,key)){prune(value[key],depth+1);}}"
                    + "}"
                    + "var parse=JSON.parse;"
                    + "JSON.parse=function(){var result=parse.apply(this,arguments);prune(result,0);return result;};"
                    + "if(window.Response&&Response.prototype.json){"
                    + "var json=Response.prototype.json;"
                    + "Response.prototype.json=function(){return json.apply(this,arguments).then(function(result){prune(result,0);return result;});};"
                    + "}"
                    + "prune(window.ytInitialPlayerResponse,0);"
                    + "prune(window.ytInitialData,0);"
                    + "})()";

    static final String BACKGROUND_PLAYBACK_SCRIPT =
            "(function(){"
                    + "if(window.__ssmusicBackgroundPlaybackInstalled){return;}"
                    + "window.__ssmusicBackgroundPlaybackInstalled=true;"
                    + "function visible(value){return {get:function(){return value;},configurable:true};}"
                    + "try{Object.defineProperty(document,'hidden',visible(false));}catch(e){}"
                    + "try{Object.defineProperty(document,'visibilityState',visible('visible'));}catch(e){}"
                    + "try{Object.defineProperty(document,'webkitHidden',visible(false));}catch(e){}"
                    + "try{Object.defineProperty(document,'webkitVisibilityState',visible('visible'));}catch(e){}"
                    + "function stop(event){event.stopImmediatePropagation();}"
                    + "document.addEventListener('visibilitychange',stop,true);"
                    + "document.addEventListener('webkitvisibilitychange',stop,true);"
                    + "function report(){"
                    + "if(window.ssmusicPlayback){window.ssmusicPlayback.setLocation(location.href);}"
                    + "var nodes=document.querySelectorAll('audio,video');"
                    + "var playing=false;"
                    + "for(var i=0;i<nodes.length;i++){"
                    + "var node=nodes[i];"
                    + "if(!node.paused&&!node.ended&&node.readyState>2){playing=true;break;}"
                    + "}"
                    + "if(window.ssmusicPlayback){window.ssmusicPlayback.setPlaying(playing);}"
                    + "}"
                    + "document.addEventListener('play',report,true);"
                    + "document.addEventListener('pause',report,true);"
                    + "document.addEventListener('ended',report,true);"
                    + "window.addEventListener('popstate',report);"
                    + "window.addEventListener('hashchange',report);"
                    + "var pushState=history.pushState;"
                    + "history.pushState=function(){var result=pushState.apply(this,arguments);report();return result;};"
                    + "var replaceState=history.replaceState;"
                    + "history.replaceState=function(){var result=replaceState.apply(this,arguments);report();return result;};"
                    + "setInterval(report,5000);"
                    + "report();"
                    + "})()";

    private WebView webView;
    private ImageButton settingsButton;
    private SharedPreferences preferences;
    private PermissionRequest pendingPermissionRequest;
    private volatile boolean playbackActive;
    private boolean usingDefaultUserAgent;
    private boolean playbackBridgeEnabled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applyTheme(preferences.getInt(KEY_THEME, Preferences.THEME_SYSTEM));
        setContentView(R.layout.activity_main);
        webView = findViewById(R.id.webview);
        settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> showPreferences());
        configureWebView();
        settingsButton.setVisibility(View.VISIBLE);
        requestAppPermissions();

        String target = urlFromIntent(getIntent());
        if (savedInstanceState != null && target == null) {
            if (webView.restoreState(savedInstanceState) == null) {
                loadUrl(Preferences.restoreUrl(preferences.getString(KEY_LAST_URL, null)));
            }
        } else {
            loadUrl(target == null
                    ? Preferences.restoreUrl(preferences.getString(KEY_LAST_URL, null))
                    : target);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onStop() {
        super.onStop();
        persistLocation(webView.getUrl());
        CookieManager.getInstance().flush();
        if (!isFinishing() && playbackActive) {
            startPlaybackKeepAliveService();
        }
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            stopService(new Intent(this, PlaybackKeepAliveService.class));
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String target = urlFromIntent(intent);
        if (target != null) {
            loadUrl(target);
        }
    }

    @Override
    public void onBackPressed() {
        int steps = historySteps(false);
        if (steps != 0) {
            webView.goBackOrForward(steps);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_WEB_PERMISSIONS && pendingPermissionRequest != null) {
            PermissionRequest request = pendingPermissionRequest;
            pendingPermissionRequest = null;
            grantWebPermissionsIfAllowed(request, false);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setUserAgentString(Preferences.userAgent(isDesktopMode()));
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new MusicWebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> grantWebPermissionsIfAllowed(request, true));
            }
        });
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Set<String> origins = new HashSet<>();
            origins.add("https://music.youtube.com");
            WebViewCompat.addDocumentStartJavaScript(
                    webView, BACKGROUND_PLAYBACK_SCRIPT, origins);
        }
    }

    private String urlFromIntent(Intent intent) {
        if (intent == null || intent.getDataString() == null) {
            return null;
        }
        return SiteScope.normalizeInAppUrl(intent.getDataString());
    }

    private boolean isDesktopMode() {
        return preferences.getBoolean(KEY_DESKTOP_MODE, false);
    }

    private void showPreferences() {
        View content = getLayoutInflater().inflate(R.layout.dialog_preferences, null);
        TextView version = content.findViewById(R.id.app_version);
        version.setText(getString(R.string.app_version_format, BuildConfig.VERSION_NAME));

        RadioGroup themeGroup = content.findViewById(R.id.theme_group);
        int theme = preferences.getInt(KEY_THEME, Preferences.THEME_SYSTEM);
        themeGroup.check(theme == Preferences.THEME_LIGHT ? R.id.theme_light
                : theme == Preferences.THEME_DARK ? R.id.theme_dark : R.id.theme_system);
        themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int value = checkedId == R.id.theme_light ? Preferences.THEME_LIGHT
                    : checkedId == R.id.theme_dark ? Preferences.THEME_DARK
                    : Preferences.THEME_SYSTEM;
            preferences.edit().putInt(KEY_THEME, value).apply();
            applyTheme(value);
        });

        RadioGroup siteModeGroup = content.findViewById(R.id.site_mode_group);
        siteModeGroup.check(isDesktopMode() ? R.id.site_mode_desktop : R.id.site_mode_mobile);
        siteModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean desktopMode = checkedId == R.id.site_mode_desktop;
            preferences.edit().putBoolean(KEY_DESKTOP_MODE, desktopMode).apply();
            applyUserAgentForUrl(webView.getUrl());
            webView.reload();
        });

        content.findViewById(R.id.back_button).setOnClickListener(v -> goHistory(false));
        content.findViewById(R.id.forward_button).setOnClickListener(v -> goHistory(true));
        content.findViewById(R.id.refresh_button).setOnClickListener(v -> webView.reload());
        content.findViewById(R.id.home_button).setOnClickListener(v -> loadUrl(Preferences.homeUrl()));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.preferences)
                .setView(content)
                .create();
        dialog.setOnShowListener(d -> {
            Window shownWindow = dialog.getWindow();
            if (shownWindow != null) {
                shownWindow.setGravity(Gravity.BOTTOM);
                shownWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
    }

    private void applyTheme(int theme) {
        int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (theme == Preferences.THEME_LIGHT) {
            mode = AppCompatDelegate.MODE_NIGHT_NO;
        } else if (theme == Preferences.THEME_DARK) {
            mode = AppCompatDelegate.MODE_NIGHT_YES;
        }
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    private void goHistory(boolean forward) {
        int steps = historySteps(forward);
        if (steps != 0) {
            webView.goBackOrForward(steps);
        }
    }

    private int historySteps(boolean forward) {
        WebBackForwardList list = webView.copyBackForwardList();
        List<String> urls = new ArrayList<>();
        for (int i = 0; i < list.getSize(); i++) {
            urls.add(list.getItemAtIndex(i).getUrl());
        }
        return forward ? NavigationHistory.forwardSteps(urls, list.getCurrentIndex())
                : NavigationHistory.backSteps(urls, list.getCurrentIndex());
    }

    private void requestAppPermissions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        List<String> missing = new ArrayList<>();
        addMissingPermission(missing, Manifest.permission.CAMERA);
        addMissingPermission(missing, Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addMissingPermission(missing, Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQUEST_APP_PERMISSIONS);
        }
    }

    private void addMissingPermission(List<String> missing, String permission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            missing.add(permission);
        }
    }

    private void grantWebPermissionsIfAllowed(PermissionRequest request, boolean mayRequestMissing) {
        if (!isTrustedPermissionOrigin(request.getOrigin().toString())) {
            request.deny();
            return;
        }
        List<String> grant = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Set<String> requested = new HashSet<>();
        for (String resource : request.getResources()) {
            requested.add(resource);
        }
        if (requested.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
                && hasPermission(Manifest.permission.RECORD_AUDIO)) {
            grant.add(PermissionRequest.RESOURCE_AUDIO_CAPTURE);
        } else if (requested.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
            missing.add(Manifest.permission.RECORD_AUDIO);
        }
        if (requested.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
                && hasPermission(Manifest.permission.CAMERA)) {
            grant.add(PermissionRequest.RESOURCE_VIDEO_CAPTURE);
        } else if (requested.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
            missing.add(Manifest.permission.CAMERA);
        }
        if (mayRequestMissing && !missing.isEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingPermissionRequest = request;
            requestPermissions(missing.toArray(new String[0]), REQUEST_WEB_PERMISSIONS);
        } else if (!grant.isEmpty()) {
            request.grant(grant.toArray(new String[0]));
        } else {
            request.deny();
        }
    }

    private boolean hasPermission(String permission) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isTrustedPermissionOrigin(String origin) {
        if (origin == null) {
            return false;
        }
        return SiteScope.isPlaybackUrl(origin) || SiteScope.isGoogleAccountUrl(origin);
    }

    private void injectPageScripts(WebView view) {
        if (!SiteScope.isPlaybackUrl(view.getUrl())) {
            return;
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            view.evaluateJavascript(BACKGROUND_PLAYBACK_SCRIPT, null);
        }
        view.evaluateJavascript(AD_HIDING_SCRIPT, null);
        view.evaluateJavascript(AD_JSON_PRUNE_SCRIPT, null);
    }

    private void loadUrl(String url) {
        prepareForUrl(url);
        webView.loadUrl(url);
    }

    private void prepareForUrl(String url) {
        playbackActive = false;
        setPlaybackBridgeEnabled(SiteScope.isPlaybackUrl(url));
        applyUserAgentForUrl(url);
    }

    private void applyUserAgentForUrl(String url) {
        WebSettings settings = webView.getSettings();
        boolean shouldUseDefault = SiteScope.isGoogleAccountUrl(url);
        if (shouldUseDefault) {
            settings.setUserAgentString(null);
        } else {
            settings.setUserAgentString(Preferences.userAgent(isDesktopMode()));
        }
        usingDefaultUserAgent = shouldUseDefault;
    }

    private void setPlaybackBridgeEnabled(boolean enabled) {
        if (enabled == playbackBridgeEnabled) {
            return;
        }
        if (enabled) {
            webView.addJavascriptInterface(new PlaybackBridge(), "ssmusicPlayback");
        } else {
            webView.removeJavascriptInterface("ssmusicPlayback");
        }
        playbackBridgeEnabled = enabled;
    }

    private void startPlaybackKeepAliveService() {
        Intent serviceIntent = new Intent(this, PlaybackKeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (RuntimeException e) {
            stopService(serviceIntent);
        }
    }

    private void persistLocation(String url) {
        String normalized = SiteScope.normalizeInAppUrl(url);
        if (SiteScope.isPlaybackUrl(normalized)) {
            preferences.edit().putString(KEY_LAST_URL, normalized).apply();
        }
    }

    private final class PlaybackBridge {
        @JavascriptInterface
        public void setPlaying(boolean playing) {
            playbackActive = playing;
            runOnUiThread(() -> {
                if (playing) {
                    startPlaybackKeepAliveService();
                } else {
                    stopService(new Intent(MainActivity.this,
                            PlaybackKeepAliveService.class));
                }
            });
        }

        @JavascriptInterface
        public void setLocation(String url) {
            persistLocation(url);
        }
    }

    private final class MusicWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUrl(view, request.getUrl().toString());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleUrl(view, url);
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            return blockedResponse(request.getUrl().toString());
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return blockedResponse(url);
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            playbackActive = false;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            injectPageScripts(view);
            persistLocation(url);
            settingsButton.setVisibility(View.VISIBLE);
            CookieManager.getInstance().flush();
        }

        private boolean handleUrl(WebView view, String url) {
            String normalized = SiteScope.normalizeInAppUrl(url);
            if (normalized == null) {
                return true;
            }
            boolean needsReload = !normalized.equals(url)
                    || SiteScope.isGoogleAccountUrl(normalized) != usingDefaultUserAgent;
            if (needsReload) {
                loadUrl(normalized);
                return true;
            }
            prepareForUrl(normalized);
            return false;
        }

        private WebResourceResponse blockedResponse(String url) {
            if (!AdBlocker.isAd(url)) {
                return null;
            }
            return new WebResourceResponse("text/plain", "utf-8",
                    new ByteArrayInputStream(new byte[0]));
        }
    }
}
