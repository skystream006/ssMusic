package com.skystream.ssmusic;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebBackForwardList;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.annotation.RequiresApi;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Hosts a single Chromium-backed WebView for YouTube Music. */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = Logger.PREFS_NAME;
    private static final String KEY_THEME = "theme";
    private static final String KEY_DESKTOP_MODE = "desktop_mode";
    private static final String KEY_LAST_URL = "last_url";
    private static final String KEY_LAST_POSITION_URL = "last_position_url";
    private static final String KEY_LAST_POSITION_SECONDS = "last_position_seconds";
    private static final String LOG_FILE_PROVIDER_SUFFIX = ".logs";
    private static final int REQUEST_APP_PERMISSIONS = 1001;
    private static final int REQUEST_WEB_PERMISSIONS = 1002;
    static final String ACTION_MEDIA_COMMAND = "com.skystream.ssmusic.MEDIA_COMMAND";
    static final String EXTRA_MEDIA_COMMAND = "media_command";
    static final int MEDIA_COMMAND_TOGGLE = 0;
    static final int MEDIA_COMMAND_PLAY = 1;
    static final int MEDIA_COMMAND_PAUSE = 2;
    static final int MEDIA_COMMAND_NEXT = 3;
    static final int MEDIA_COMMAND_PREVIOUS = 4;
    static final int MEDIA_COMMAND_SEEK = 5;
    static final int MEDIA_COMMAND_STOP = 6;
    static final int MEDIA_COMMAND_SERVICE_STOPPED = 7;
    static final String EXTRA_MEDIA_POSITION_MS = "media_position_ms";
    static final String EXTRA_MEDIA_START_TOKEN = "media_start_token";
    private static final long PLAYBACK_SIGNAL_GRACE_MS = 15000L;
    private static final long AUTO_RESUME_SUPPRESSION_MS = 1500L;
    private static final float MEDIA_SESSION_POSITION_SYNC_THRESHOLD_SECONDS = 5f;
    private static final int MAX_METADATA_LENGTH = 200;
    private static final long POSITION_LOG_INTERVAL_MS = 30000L;
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    // Pick likely main media in priority order: paused with progress, paused fallback, then any non-ended node.
    private static final String PICK_MEDIA_NODE_HELPER =
            ";function pickMediaNode(nodes){"
                    + "for(var i=0;i<nodes.length;i++){"
                    + "if(!nodes[i].ended&&nodes[i].paused&&((nodes[i].currentTime||0)>0)){return nodes[i];}"
                    + "}"
                    + "for(var j=0;j<nodes.length;j++){if(!nodes[j].ended&&nodes[j].paused){return nodes[j];}}"
                    + "for(var k=0;k<nodes.length;k++){if(!nodes[k].ended){return nodes[k];}}"
                    + "return nodes.length?nodes[0]:null;"
                    + "}";
    private static final String PAUSE_ACTIVE_MEDIA_HELPER =
            ";function pauseActiveMedia(nodes){"
                    + "for(var i=0;i<nodes.length;i++){"
                    + "var node=nodes[i];"
                    + "if(!node.ended&&!node.paused&&typeof node.pause==='function'){node.pause();}"
                    + "}"
                    + "}";

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

    // Treat unpaused-but-buffering media as active in fallback checks so app backgrounding
    // transitions do not briefly report paused and make notification controls oscillate.
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
                    + "function isBackgrounded(){"
                    + "try{return document.visibilityState==='hidden'||!document.hasFocus();}"
                    + "catch(e){return true;}"
                    + "}"
                    + "function keepPlaying(event){"
                    + "var node=event&&event.target;"
                    + "if(!node||typeof node.play!=='function'||!isBackgrounded()){return;}"
                    + "if(window.ssmusicPlayback&&window.ssmusicPlayback.shouldAutoResume"
                    + "&&!window.ssmusicPlayback.shouldAutoResume()){return;}"
                    + "var p=node.play();"
                    + "if(p&&typeof p.catch==='function'){p.catch(function(){});}"
                    + "setTimeout(report,150);"
                    + "}"
                    + "document.addEventListener('visibilitychange',stop,true);"
                    + "document.addEventListener('webkitvisibilitychange',stop,true);"
                    + "document.addEventListener('pause',keepPlaying,true);"
                    + "function report(){"
                    + "if(window.ssmusicPlayback){window.ssmusicPlayback.setLocation(location.href);}"
                    + "var nodes=document.querySelectorAll('audio,video');"
                    + "var playing=false;"
                    + "var position=0;"
                    + "var duration=0;"
                    + "var active=null;"
                    + "var tentative=null;"
                    + "for(var i=0;i<nodes.length;i++){"
                    + "var node=nodes[i];"
                    + "if(!node.paused&&!node.ended&&node.readyState>2){"
                    + "active=node;playing=true;break;"
                    + "}"
                    + "if(!tentative&&!node.paused&&!node.ended&&node.readyState>=2){"
                    + "tentative=node;"
                    + "}"
                    + "if(typeof node.currentTime==='number'&&isFinite(node.currentTime)&&node.currentTime>position){"
                    + "position=node.currentTime;"
                    + "var nodeDuration=(typeof node.duration==='number'&&isFinite(node.duration))?node.duration:0;"
                    + "if(nodeDuration>0){duration=nodeDuration;}"
                    + "}"
                    + "}"
                    + "if(!active&&tentative){active=tentative;playing=true;}"
                    + "if(active){position=active.currentTime||0;"
                    + "duration=(typeof active.duration==='number'&&isFinite(active.duration))?active.duration:0;}"
                    + "if(window.ssmusicPlayback){"
                    + "window.ssmusicPlayback.setPosition(location.href,position,duration);"
                    + "var player=document.querySelector('ytmusic-player-bar');"
                    + "var text=function(selector){var element=(player||document).querySelector(selector);"
                    + "return element&&element.textContent?element.textContent.trim():'';};"
                    + "var title=text('.title');"
                    + "if(!title){title=(document.title||'').replace(/\\s*-\\s*YouTube Music\\s*$/i,'');}"
                    + "var artist=text('.byline')||text('.subtitle');"
                    + "window.ssmusicPlayback.setMetadata(title,artist);"
                    + "window.ssmusicPlayback.setPlaying(playing);"
                    + "}"
                    + "}"
                    + "window.__ssmusicForceReport=report;"
                    + "document.addEventListener('play',report,true);"
                    + "document.addEventListener('pause',report,true);"
                    + "document.addEventListener('ended',report,true);"
                    + "window.addEventListener('pagehide',report);"
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
    private boolean mediaCommandReceiverRegistered;
    private boolean keepAliveServiceRunning;
    private long keepAliveStartToken;
    private volatile long suppressAutoResumeUntilElapsedMs;
    private long lastPlaybackSignalAtElapsedMs;
    private String lastPersistedPositionIdentityUrl;
    private float lastPersistedPositionSeconds;
    private String lastReportedPositionUrl;
    private float lastReportedPositionSeconds;
    private float lastServicePositionSeconds = Float.NaN;
    private long lastPlaybackDurationMs;
    private volatile long lastPositionLogAtElapsedMs;
    private String currentTrackTitle;
    private String currentTrackArtist;
    private final BroadcastReceiver mediaCommandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            if (!ACTION_MEDIA_COMMAND.equals(intent.getAction())) {
                return;
            }
            Logger.event(TAG, "Media command broadcast received: "
                    + intent.getIntExtra(EXTRA_MEDIA_COMMAND, MEDIA_COMMAND_TOGGLE));
            applyMediaCommand(intent.getIntExtra(EXTRA_MEDIA_COMMAND, MEDIA_COMMAND_TOGGLE),
                    intent.getLongExtra(EXTRA_MEDIA_POSITION_MS, 0L),
                    intent.getLongExtra(EXTRA_MEDIA_START_TOKEN, 0L));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.init(this);
        Logger.event(TAG, "onCreate, restored state: " + (savedInstanceState != null));
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        applyTheme(preferences.getInt(KEY_THEME, Preferences.THEME_SYSTEM));
        setContentView(R.layout.activity_main);
        lastPersistedPositionIdentityUrl = Preferences.playbackIdentityUrl(
                preferences.getString(KEY_LAST_POSITION_URL, null));
        lastPersistedPositionSeconds = preferences.getFloat(KEY_LAST_POSITION_SECONDS, 0f);
        lastReportedPositionUrl = lastPersistedPositionIdentityUrl;
        lastReportedPositionSeconds = lastPersistedPositionSeconds;
        webView = findViewById(R.id.webview);
        settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            Logger.event(TAG, "Settings panel opened");
            showPreferences();
        });
        configureWebView();
        registerMediaCommandReceiver();
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
        Logger.debug(TAG, "onSaveInstanceState");
        webView.saveState(outState);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Logger.event(TAG, "onStop, playback active: " + playbackActive);
        persistPlaybackPosition(lastReportedPositionUrl, lastReportedPositionSeconds);
        capturePlaybackPosition();
        persistLocation(webView.getUrl());
        CookieManager.getInstance().flush();
        if (!isFinishing() && isPlaybackLikelyActive()) {
            startPlaybackKeepAliveService(playbackActive ? Boolean.TRUE : null);
        }
    }

    @Override
    protected void onDestroy() {
        Logger.event(TAG, "onDestroy, finishing: " + isFinishing());
        if (mediaCommandReceiverRegistered) {
            unregisterReceiver(mediaCommandReceiver);
            mediaCommandReceiverRegistered = false;
        }
        if (isFinishing()) {
            stopPlaybackKeepAliveService();
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String target = urlFromIntent(intent);
        Logger.event(TAG, "onNewIntent, target: " + target);
        if (target != null) {
            loadUrl(target);
        }
    }

    @Override
    public void onBackPressed() {
        int steps = historySteps(false);
        Logger.event(TAG, "Back pressed, history steps: " + steps);
        if (steps != 0) {
            webView.goBackOrForward(steps);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.event(TAG, "Permission result for request " + requestCode + ": "
                + Arrays.toString(permissions)
                + " -> " + Arrays.toString(grantResults));
        if (requestCode == REQUEST_WEB_PERMISSIONS && pendingPermissionRequest != null) {
            PermissionRequest request = pendingPermissionRequest;
            pendingPermissionRequest = null;
            grantWebPermissionsIfAllowed(request, false);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        Logger.event(TAG, "Configuring WebView, desktop mode: " + isDesktopMode());
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
            Logger.event(TAG, "Theme preference changed to " + value);
            preferences.edit().putInt(KEY_THEME, value).apply();
            applyTheme(value);
        });

        RadioGroup siteModeGroup = content.findViewById(R.id.site_mode_group);
        siteModeGroup.check(isDesktopMode() ? R.id.site_mode_desktop : R.id.site_mode_mobile);
        siteModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean desktopMode = checkedId == R.id.site_mode_desktop;
            Logger.event(TAG, "Site mode preference changed, desktop: " + desktopMode);
            preferences.edit().putBoolean(KEY_DESKTOP_MODE, desktopMode).apply();
            applyUserAgentForUrl(webView.getUrl());
            webView.reload();
        });

        Switch loggingSwitch = content.findViewById(R.id.logging_switch);
        loggingSwitch.setChecked(Logger.isEnabled());
        loggingSwitch.setOnCheckedChangeListener(
                (button, checked) -> Logger.setEnabled(MainActivity.this, checked));
        content.findViewById(R.id.share_log_button).setOnClickListener(v -> shareLog());
        content.findViewById(R.id.clear_log_button).setOnClickListener(v -> clearLog());

        content.findViewById(R.id.back_button).setOnClickListener(v -> goHistory(false));
        content.findViewById(R.id.forward_button).setOnClickListener(v -> goHistory(true));
        content.findViewById(R.id.refresh_button).setOnClickListener(v -> {
            Logger.event(TAG, "Reload requested from settings");
            webView.reload();
        });
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

    private void shareLog() {
        File logFile = Logger.logFile(this);
        if (logFile == null || !logFile.isFile() || logFile.length() == 0L) {
            Toast.makeText(this, R.string.log_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(
                    this, getPackageName() + LOG_FILE_PROVIDER_SUFFIX, logFile);
            Intent share = new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, getString(R.string.log_share_title))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Logger.event(TAG, "Sharing log file, bytes: " + logFile.length());
            startActivity(Intent.createChooser(share, getString(R.string.log_share_title)));
        } catch (IllegalArgumentException | ActivityNotFoundException e) {
            Logger.error(TAG, "Unable to share log file", e);
            Toast.makeText(this, R.string.log_share_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void clearLog() {
        Logger.clear(this);
        Logger.event(TAG, "Log cleared by user");
        Toast.makeText(this, R.string.log_cleared, Toast.LENGTH_SHORT).show();
    }

    private void applyTheme(int theme) {
        Logger.event(TAG, "Applying theme " + theme);
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
        Logger.event(TAG, (forward ? "Forward" : "Back") + " navigation, steps: " + steps);
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
            Logger.event(TAG, "Requesting app permissions: " + missing);
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
            Logger.warn(TAG, "Denied web permission request from untrusted origin: "
                    + request.getOrigin(), null);
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
        Logger.event(TAG, "Web permission request from " + request.getOrigin()
                + ", granting: " + grant + ", missing: " + missing);
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
        Logger.debug(TAG, "Injecting page scripts");
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            view.evaluateJavascript(BACKGROUND_PLAYBACK_SCRIPT, null);
        }
        view.evaluateJavascript(AD_HIDING_SCRIPT, null);
        view.evaluateJavascript(AD_JSON_PRUNE_SCRIPT, null);
    }

    private void registerMediaCommandReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_MEDIA_COMMAND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mediaCommandReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mediaCommandReceiver, filter);
        }
        mediaCommandReceiverRegistered = true;
    }

    private void loadUrl(String url) {
        Logger.event(TAG, "Loading url: " + url);
        prepareForUrl(url);
        webView.loadUrl(url);
    }

    private void prepareForUrl(String url) {
        playbackActive = false;
        stopPlaybackKeepAliveService();
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
        Logger.event(TAG, "Playback bridge enabled: " + enabled);
        if (enabled) {
            webView.addJavascriptInterface(new PlaybackBridge(), "ssmusicPlayback");
        } else {
            webView.removeJavascriptInterface("ssmusicPlayback");
        }
        playbackBridgeEnabled = enabled;
    }

    private void startPlaybackKeepAliveService(Boolean playingState) {
        Intent serviceIntent = new Intent(this, PlaybackKeepAliveService.class);
        serviceIntent.setAction(PlaybackKeepAliveService.ACTION_SYNC_PLAYBACK_STATE);
        if (playingState != null) {
            serviceIntent.putExtra(PlaybackKeepAliveService.EXTRA_SYNC_PLAYING, playingState);
        }
        serviceIntent.putExtra(PlaybackKeepAliveService.EXTRA_SYNC_POSITION_MS,
                (long) (lastReportedPositionSeconds * 1000f));
        serviceIntent.putExtra(PlaybackKeepAliveService.EXTRA_SYNC_DURATION_MS,
                lastPlaybackDurationMs);
        if (currentTrackTitle != null && !currentTrackTitle.isEmpty()) {
            serviceIntent.putExtra(PlaybackKeepAliveService.EXTRA_SYNC_TITLE, currentTrackTitle);
        }
        if (currentTrackArtist != null && !currentTrackArtist.isEmpty()) {
            serviceIntent.putExtra(PlaybackKeepAliveService.EXTRA_SYNC_ARTIST, currentTrackArtist);
        }
        serviceIntent.putExtra(PlaybackKeepAliveService.EXTRA_SYNC_START_TOKEN,
                ++keepAliveStartToken);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            keepAliveServiceRunning = true;
            Logger.event(TAG, "Playback keep-alive service started, playing: " + playingState);
        } catch (RuntimeException e) {
            Logger.error(TAG, "Unable to start playback keep-alive service", e);
            // A background start can be rejected; only tear down when no notification exists yet,
            // so an already running notification is never dropped by a failed state sync.
            if (!keepAliveServiceRunning) {
                stopPlaybackKeepAliveService();
            }
        }
    }

    private void stopPlaybackKeepAliveService() {
        Logger.event(TAG, "Stopping playback keep-alive service");
        keepAliveServiceRunning = false;
        lastServicePositionSeconds = Float.NaN;
        stopService(new Intent(this, PlaybackKeepAliveService.class));
    }

    private void persistLocation(String url) {
        String normalized = SiteScope.normalizeInAppUrl(url);
        if (SiteScope.isPlaybackUrl(normalized)) {
            String currentIdentityUrl = Preferences.playbackIdentityUrl(normalized);
            if (currentIdentityUrl != null
                    && currentIdentityUrl.equals(lastPersistedPositionIdentityUrl)) {
                String withTimestamp = Preferences.buildPersistedPlaybackUrl(
                        normalized, lastPersistedPositionSeconds);
                if (withTimestamp != null) {
                    normalized = withTimestamp;
                }
            }
            preferences.edit().putString(KEY_LAST_URL, normalized).apply();
        }
    }

    private void persistPlaybackPosition(String url, double seconds) {
        String normalized = SiteScope.normalizeInAppUrl(url);
        String identityUrl = Preferences.playbackIdentityUrl(normalized);
        if (identityUrl == null || !Double.isFinite(seconds) || seconds < 0d) {
            return;
        }
        // Persist only meaningful progress changes to avoid high-frequency disk writes.
        float value = (float) seconds;
        if (identityUrl.equals(lastPersistedPositionIdentityUrl)
                && Math.abs(value - lastPersistedPositionSeconds) < 1f) {
            return;
        }
        String urlWithTimestamp = Preferences.buildPersistedPlaybackUrl(normalized, value);
        preferences.edit()
                .putString(KEY_LAST_POSITION_URL, identityUrl)
                .putFloat(KEY_LAST_POSITION_SECONDS, value)
                .putString(KEY_LAST_URL, urlWithTimestamp == null ? normalized : urlWithTimestamp)
                .apply();
        lastPersistedPositionIdentityUrl = identityUrl;
        lastPersistedPositionSeconds = value;
        Logger.debug(TAG, "Persisted playback position " + value + "s for " + identityUrl);
    }

    private void capturePlaybackPosition() {
        if (playbackBridgeEnabled) {
            webView.evaluateJavascript(
                    "if(window.__ssmusicForceReport){window.__ssmusicForceReport();}", null);
        }
    }

    private void restorePlaybackPosition(WebView view, String url) {
        String normalized = SiteScope.normalizeInAppUrl(url);
        if (!SiteScope.isPlaybackUrl(normalized)
                || !Preferences.isSamePlaybackItem(normalized,
                        preferences.getString(KEY_LAST_POSITION_URL, null))) {
            return;
        }
        // Restore only for the same playback URL so stale progress is never applied elsewhere.
        float savedSeconds = preferences.getFloat(KEY_LAST_POSITION_SECONDS, 0f);
        if (savedSeconds <= 0f) {
            return;
        }
        Logger.event(TAG, "Restoring playback position " + savedSeconds + "s");
        String target = String.format(Locale.US, "%.3f", savedSeconds);
        String script = "(function(){"
                + "var target=" + target + ";"
                + "if(!(target>0)){return;}"
                + "function seek(){"
                + "var node=document.querySelector('audio,video');"
                + "if(!node){return false;}"
                + "var maxTarget=target;"
                + "if(node.duration&&isFinite(node.duration)&&target>=node.duration){"
                + "maxTarget=Math.max(0,node.duration-1);"
                + "}"
                + "if(Math.abs((node.currentTime||0)-maxTarget)<1){return true;}"
                + "try{node.currentTime=maxTarget;}catch(e){}"
                + "return true;"
                + "}"
                + "if(seek()){return;}"
                + "var tries=0;"
                + "var timer=setInterval(function(){"
                + "tries++;"
                + "if(seek()||tries>40){clearInterval(timer);}"
                + "},250);"
                + "})();";
        view.evaluateJavascript(script, null);
    }

    private void applyMediaCommand(int command, long positionMs, long startToken) {
        Logger.event(TAG, "Applying media command " + command + ", position: " + positionMs);
        if (command == MEDIA_COMMAND_SERVICE_STOPPED) {
            // Ignore a stale notice from an older service instance that a newer start replaced.
            if (startToken >= keepAliveStartToken) {
                keepAliveServiceRunning = false;
            }
            return;
        }
        if (command == MEDIA_COMMAND_STOP) {
            keepAliveServiceRunning = false;
        }
        if (!playbackBridgeEnabled) {
            return;
        }
        if (command == MEDIA_COMMAND_PAUSE || command == MEDIA_COMMAND_STOP) {
            suppressAutoResumeUntilElapsedMs = SystemClock.elapsedRealtime() + AUTO_RESUME_SUPPRESSION_MS;
        } else {
            suppressAutoResumeUntilElapsedMs = 0L;
        }
        String script;
        if (command == MEDIA_COMMAND_PLAY) {
            script = "(function(){var nodes=document.querySelectorAll('audio,video');var active=null;var node=null;"
                    + PICK_MEDIA_NODE_HELPER
                    + "for(var i=0;i<nodes.length;i++){"
                    + "if(!nodes[i].ended&&!nodes[i].paused){active=nodes[i];break;}"
                    + "}"
                    + "if(!active){"
                    + "node=pickMediaNode(nodes);"
                    + "if(node&&typeof node.play==='function'){"
                    + "var p=node.play();if(p&&typeof p.catch==='function'){p.catch(function(){});}"
                    + "}"
                    + "}"
                    + "if(window.__ssmusicForceReport){window.__ssmusicForceReport();}})();";
        } else if (command == MEDIA_COMMAND_PAUSE || command == MEDIA_COMMAND_STOP) {
            script = "(function(){var nodes=document.querySelectorAll('audio,video');"
                    + PAUSE_ACTIVE_MEDIA_HELPER
                    + "pauseActiveMedia(nodes);"
                    + "if(window.__ssmusicForceReport){window.__ssmusicForceReport();}})();";
        } else if (command == MEDIA_COMMAND_NEXT) {
            script = "(function(){"
                    + "var btn=document.querySelector('ytmusic-player-bar .next-button,tp-yt-paper-icon-button.next-button');"
                    + "if(btn){btn.click();}"
                    + "if(window.__ssmusicForceReport){window.__ssmusicForceReport();}"
                    + "})();";
        } else if (command == MEDIA_COMMAND_PREVIOUS) {
            script = "(function(){"
                    + "var btn=document.querySelector('ytmusic-player-bar .previous-button,tp-yt-paper-icon-button.previous-button');"
                    + "if(btn){btn.click();}"
                    + "if(window.__ssmusicForceReport){window.__ssmusicForceReport();}"
                    + "})();";
        } else if (command == MEDIA_COMMAND_SEEK) {
            String position = String.format(Locale.US, "%.3f", Math.max(0L, positionMs) / 1000d);
            script = "(function(){var nodes=document.querySelectorAll('audio,video');var node=null;"
                    + "for(var i=0;i<nodes.length;i++){if(!nodes[i].ended&&!nodes[i].paused){node=nodes[i];break;}}"
                    + "if(!node&&nodes.length){node=nodes[0];}"
                    + "if(node){try{node.currentTime=" + position + ";}catch(e){}}"
                    + "if(window.__ssmusicForceReport){window.__ssmusicForceReport();}})();";
        } else {
            script = "(function(){var nodes=document.querySelectorAll('audio,video');var node=null;"
                    + PICK_MEDIA_NODE_HELPER
                    + PAUSE_ACTIVE_MEDIA_HELPER
                    + "for(var i=0;i<nodes.length;i++){if(!nodes[i].ended&&!nodes[i].paused){node=nodes[i];break;}}"
                    + "if(node){"
                    + "pauseActiveMedia(nodes);"
                    + "}else{"
                    + "node=pickMediaNode(nodes);"
                    + "if(node&&typeof node.play==='function'){"
                    + "var p=node.play();if(p&&typeof p.catch==='function'){p.catch(function(){});}"
                    + "}"
                    + "}"
                    + "if(window.__ssmusicForceReport){window.__ssmusicForceReport();}})();";
        }
        runOnUiThread(() -> webView.evaluateJavascript(script, null));
    }

    private synchronized void updatePlaybackService(boolean playing) {
        if (playing) {
            lastPlaybackSignalAtElapsedMs = SystemClock.elapsedRealtime();
        }
        if (playbackActive == playing) {
            if (playing) {
                runOnUiThread(() -> startPlaybackKeepAliveService(Boolean.TRUE));
            }
            return;
        }
        playbackActive = playing;
        Logger.event(TAG, "Playback state changed, playing: " + playing);
        runOnUiThread(() -> {
            if (playing) {
                startPlaybackKeepAliveService(Boolean.TRUE);
            } else if (keepAliveServiceRunning) {
                // Keep the media notification up while paused so transport controls survive
                // pausing from the notification itself or from leaving the app.
                startPlaybackKeepAliveService(Boolean.FALSE);
            }
        });
    }

    private final class PlaybackBridge {
        @JavascriptInterface
        public void setPlaying(boolean playing) {
            Logger.debug(TAG, "Bridge playing state: " + playing);
            updatePlaybackService(playing);
        }

        @JavascriptInterface
        public void setLocation(String url) {
            Logger.debug(TAG, "Bridge location update: " + url);
            persistLocation(url);
        }

        @JavascriptInterface
        public void setPosition(String url, double seconds, double duration) {
            logPositionUpdate(seconds, duration);
            runOnUiThread(() -> {
                persistPlaybackPosition(url, seconds);
                String normalized = SiteScope.normalizeInAppUrl(url);
                if (SiteScope.isPlaybackUrl(normalized)
                        && Double.isFinite(seconds) && seconds >= 0d) {
                    lastReportedPositionUrl = normalized;
                    lastReportedPositionSeconds = (float) seconds;
                }
                if (Double.isFinite(duration) && duration > 0d) {
                    lastPlaybackDurationMs = (long) (duration * 1000d);
                }
                if (playbackActive
                        && (Float.isNaN(lastServicePositionSeconds)
                        || Math.abs(lastReportedPositionSeconds - lastServicePositionSeconds)
                        >= MEDIA_SESSION_POSITION_SYNC_THRESHOLD_SECONDS)) {
                    lastServicePositionSeconds = lastReportedPositionSeconds;
                    startPlaybackKeepAliveService(Boolean.TRUE);
                }
            });
        }

        @JavascriptInterface
        public void setMetadata(String title, String artist) {
            runOnUiThread(() -> {
                String sanitizedTitle = sanitizeMetadata(title);
                String sanitizedArtist = sanitizeMetadata(artist);
                if (sanitizedTitle.isEmpty()) {
                    return;
                }
                if (sanitizedTitle.equals(currentTrackTitle) && sanitizedArtist.equals(currentTrackArtist)) {
                    return;
                }
                Logger.event(TAG, "Track metadata: " + sanitizedTitle + " - " + sanitizedArtist);
                currentTrackTitle = sanitizedTitle;
                currentTrackArtist = sanitizedArtist;
                if (keepAliveServiceRunning) {
                    startPlaybackKeepAliveService(null);
                }
            });
        }

        @JavascriptInterface
        public boolean shouldAutoResume() {
            return SystemClock.elapsedRealtime() >= suppressAutoResumeUntilElapsedMs;
        }
    }

    /** Position reports arrive continuously, so they are only logged periodically. */
    private void logPositionUpdate(double seconds, double duration) {
        if (!Logger.isEnabled()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastPositionLogAtElapsedMs < POSITION_LOG_INTERVAL_MS) {
            return;
        }
        lastPositionLogAtElapsedMs = now;
        Logger.debug(TAG, "Bridge position update: " + seconds + "s of " + duration + "s");
    }

    private String sanitizeMetadata(String value) {
        if (value == null) {
            return "";
        }
        String normalized = WHITESPACE.matcher(value.trim()).replaceAll(" ");
        return normalized.length() <= MAX_METADATA_LENGTH
                ? normalized : normalized.substring(0, MAX_METADATA_LENGTH);
    }

    private boolean isPlaybackLikelyActive() {
        String normalized = SiteScope.normalizeInAppUrl(webView.getUrl());
        if (!SiteScope.isPlaybackUrl(normalized)) {
            return false;
        }
        if (playbackActive) {
            return true;
        }
        return lastPlaybackSignalAtElapsedMs > 0L
                && SystemClock.elapsedRealtime() - lastPlaybackSignalAtElapsedMs <= PLAYBACK_SIGNAL_GRACE_MS;
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
            Logger.event(TAG, "Page started: " + url);
            updatePlaybackService(false);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            Logger.event(TAG, "Page finished: " + url);
            injectPageScripts(view);
            restorePlaybackPosition(view, url);
            persistLocation(url);
            settingsButton.setVisibility(View.VISIBLE);
            CookieManager.getInstance().flush();
        }

        @RequiresApi(Build.VERSION_CODES.M)
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                WebResourceError error) {
            super.onReceivedError(view, request, error);
            Logger.warn(TAG, "Resource error " + error.getErrorCode() + " for "
                    + request.getUrl() + ": " + error.getDescription(), null);
        }

        @RequiresApi(Build.VERSION_CODES.M)
        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request,
                WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            Logger.warn(TAG, "HTTP error " + errorResponse.getStatusCode() + " for "
                    + request.getUrl(), null);
        }

        private boolean handleUrl(WebView view, String url) {
            String normalized = SiteScope.normalizeInAppUrl(url);
            if (normalized == null) {
                Logger.warn(TAG, "Blocked out-of-scope navigation: " + url, null);
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
            Logger.debug(TAG, "Blocked ad request: " + url);
            return new WebResourceResponse("text/plain", "utf-8",
                    new ByteArrayInputStream(new byte[0]));
        }
    }
}
