package com.skystream.ssmusic;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
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
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;
import androidx.webkit.ScriptHandler;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
    private static final String KEY_SHOW_VIDEO_THUMBNAIL = "show_video_thumbnail";
    private static final String KEY_STATS_FOR_NERDS = "stats_for_nerds";
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
    private static final float MEDIA_SESSION_POSITION_SYNC_THRESHOLD_SECONDS = 5f;
    private static final int MAX_METADATA_LENGTH = 200;
    private static final int MAX_THUMBNAIL_URL_LENGTH = 2000;
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
                    + "function hideUpgrade(){"
                    + "var entries=document.querySelectorAll('ytmusic-guide-entry-renderer,ytmusic-mini-guide-entry-renderer');"
                    + "for(var i=0;i<entries.length;i++){"
                    + "var entry=entries[i];"
                    + "var label=entry.querySelector('.title,yt-formatted-string');"
                    + "var upgrade=entry.querySelector('a[href*=\"music_premium\"]')"
                    + "||entry.querySelector('[aria-label=\"Upgrade\"],[title=\"Upgrade\"]')"
                    + "||(label&&label.textContent.trim()==='Upgrade');"
                    + "if(upgrade&&entry.style.getPropertyValue('display')!=='none'){"
                    + "entry.style.setProperty('display','none','important');"
                    + "}"
                    + "}"
                    + "}"
                    + "function apply(){"
                    + "if(document.getElementById(id)){return true;}"
                    + "var parent=document.head||document.documentElement;"
                    + "if(!parent){return false;}"
                    + "var style=document.createElement('style');"
                    + "style.id=id;"
                    + "style.textContent=css;"
                    + "parent.appendChild(style);"
                    + "hideUpgrade();"
                    + "new MutationObserver(hideUpgrade).observe(document.documentElement,"
                    + "{childList:true,subtree:true,characterData:true,attributes:true,"
                    + "attributeFilter:['href','aria-label','title']});"
                    + "return true;"
                    + "}"
                    + "if(!apply()){setTimeout(apply,50);}"
                    + "})()";

    static final String OPEN_APP_HIDING_SCRIPT =
            "(function(){"
                    + "var NAV='ytmusic-nav-bar,ytmusic-guide-renderer,ytmusic-mini-guide-renderer';"
                    + "var CONTROLS='ytmusic-guide-entry-renderer,ytmusic-mini-guide-entry-renderer,"
                    + "yt-button-renderer,yt-button-shape,a,button,[role=\"button\"],.app-install-link';"
                    + "function isOpenApp(node){"
                    + "return node.classList.contains('app-install-link')"
                    + "||!!node.querySelector('.app-install-link')"
                    + "||/^open app$/i.test((node.getAttribute('aria-label')||'').trim())"
                    + "||/^open app$/i.test((node.textContent||'').replace(/\\s+/g,' ').trim());"
                    + "}"
                    + "function hide(){"
                    + "var navs=document.querySelectorAll(NAV);"
                    + "for(var i=0;i<navs.length;i++){"
                    + "var controls=navs[i].querySelectorAll(CONTROLS);"
                    + "for(var j=0;j<controls.length;j++){"
                    + "var node=controls[j];"
                    + "if(!isOpenApp(node)){continue;}"
                    + "var entry=node.closest('ytmusic-guide-entry-renderer,ytmusic-mini-guide-entry-renderer');"
                    + "(entry||node).style.setProperty('display','none','important');"
                    + "}"
                    + "}"
                    + "}"
                    + "hide();"
                    + "if(!window.__ssmusicOpenAppObserver&&document.documentElement){"
                    + "window.__ssmusicOpenAppObserver=new MutationObserver(hide);"
                    + "window.__ssmusicOpenAppObserver.observe(document.documentElement,"
                    + "{childList:true,subtree:true,characterData:true,attributes:true,"
                    + "attributeFilter:['class','aria-label']});"
                    + "}"
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

    /**
     * Same-origin path the WebView requests for the bundled app logo. Requests for it never reach
     * the network, they are answered from the app resources by
     * {@link MusicWebViewClient#shouldInterceptRequest}.
     */
    static final String APP_LOGO_PATH = "/ssmusic_app_logo.png";

    /**
     * Swaps the YouTube Music wordmark on the page for the bundled app logo.
     *
     * <p>YouTube Music renders its wordmark differently across surfaces (a plain {@code <img>}, an
     * inline SVG, or a shadow-DOM subtree inside {@code ytmusic-logo}), so instead of editing the
     * existing markup this inserts a real {@code <img>} overlay sized with inline styles that do
     * not depend on the container having an intrinsic box, into both the light DOM and, when
     * present, the shadow root. Existing content is only faded out with {@code opacity:0} so it
     * stays hit-testable and taps still reach the home link it carries.
     */
    static final String APP_LOGO_SCRIPT =
            "(function(){"
                    + "var CLASS='ssmusic-app-logo';"
                    + "var OVERLAY_CLASS='ssmusic-app-logo-overlay';"
                    + "var CONTAINERS='ytmusic-logo,.ytmusic-logo,ytmusic-nav-bar .logo,"
                    + "#logo-icon,yt-icon#logo-icon,a#logo';"
                    + "var IMAGES='img[src*=\"music_logo\"],img[src*=\"yt_logo\"],"
                    + "img[src*=\"youtube_logo\"],img[src*=\"ytm_logo\"]';"
                    + "var LOGO_SRC=location.origin+'" + APP_LOGO_PATH + "';"
                    + "var MIN_SIZE_PX=24;"
                    + "function asArray(list){return Array.prototype.slice.call(list);}"
                    + "function hideChildren(root){"
                    + "var children=asArray(root.children);"
                    + "for(var i=0;i<children.length;i++){"
                    + "if(children[i].classList&&children[i].classList.contains(OVERLAY_CLASS)){"
                    + "continue;"
                    + "}"
                    + "if(children[i].style){"
                    + "children[i].style.setProperty('opacity','0','important');"
                    + "}"
                    + "}"
                    + "}"
                    + "function placeOverlay(root){"
                    + "var img=root.querySelector('img.'+OVERLAY_CLASS);"
                    + "if(!img){"
                    + "img=document.createElement('img');"
                    + "img.className=OVERLAY_CLASS;"
                    + "img.alt='ssMusic';"
                    + "root.appendChild(img);"
                    + "}"
                    + "img.style.setProperty('position','absolute','important');"
                    + "img.style.setProperty('top','0','important');"
                    + "img.style.setProperty('left','0','important');"
                    + "img.style.setProperty('width','100%','important');"
                    + "img.style.setProperty('height','100%','important');"
                    + "img.style.setProperty('object-fit','contain','important');"
                    + "img.style.setProperty('object-position','left center','important');"
                    + "img.style.setProperty('opacity','1','important');"
                    + "img.style.setProperty('pointer-events','none','important');"
                    + "if(img.src!==LOGO_SRC){img.src=LOGO_SRC;}"
                    + "}"
                    // Custom elements are often left at display:contents or a zero-size host, which
                    // paints nothing at all, so the container is forced to establish its own box
                    // for the absolutely positioned overlay to fill.
                    + "function ensureBox(node){"
                    + "var computed=window.getComputedStyle(node);"
                    + "if(computed.position==='static'){"
                    + "node.style.setProperty('position','relative','important');"
                    + "}"
                    + "if(computed.display==='contents'||computed.display==='inline'){"
                    + "node.style.setProperty('display','inline-block','important');"
                    + "}"
                    + "if(node.offsetWidth<MIN_SIZE_PX){"
                    + "node.style.setProperty('min-width',MIN_SIZE_PX+'px','important');"
                    + "}"
                    + "if(node.offsetHeight<MIN_SIZE_PX){"
                    + "node.style.setProperty('min-height',MIN_SIZE_PX+'px','important');"
                    + "}"
                    + "}"
                    + "function paintContainer(node){"
                    + "if(!node||!node.style){return;}"
                    + "if(node.parentElement&&node.parentElement.closest"
                    + "&&node.parentElement.closest(CONTAINERS)){return;}"
                    + "node.classList.add(CLASS);"
                    + "ensureBox(node);"
                    + "hideChildren(node);"
                    + "placeOverlay(node);"
                    + "if(node.shadowRoot){"
                    + "hideChildren(node.shadowRoot);"
                    + "placeOverlay(node.shadowRoot);"
                    + "}"
                    + "}"
                    // Fallback for stray wordmark images outside the known containers, where a
                    // plain src swap is enough.
                    + "function replaceImage(image){"
                    + "if(image.classList&&image.classList.contains(CLASS)"
                    + "&&image.src===LOGO_SRC){return;}"
                    + "image.classList.add(CLASS);"
                    + "image.alt='ssMusic';"
                    + "image.src=LOGO_SRC;"
                    + "image.style.setProperty('object-fit','contain','important');"
                    + "}"
                    + "function watch(target){"
                    + "new MutationObserver(function(mutations){"
                    + "for(var i=0;i<mutations.length;i++){"
                    + "for(var j=0;j<mutations[i].addedNodes.length;j++){"
                    + "var node=mutations[i].addedNodes[j];"
                    + "if(node.nodeType===1){applyLogos(node);}"
                    + "}"
                    + "}"
                    + "}).observe(target,{childList:true,subtree:true});"
                    + "}"
                    // Regular querySelectorAll calls cannot see across a shadow boundary, so shadow
                    // roots are located and searched (and watched for mutations) explicitly.
                    + "function observeShadowRoots(root){"
                    + "if(!root||!root.querySelectorAll){return;}"
                    + "var all=asArray(root.querySelectorAll('*'));"
                    + "for(var i=0;i<all.length;i++){"
                    + "var shadow=all[i].shadowRoot;"
                    + "if(shadow&&!shadow.__ssmusicLogoObserved){"
                    + "shadow.__ssmusicLogoObserved=true;"
                    + "applyLogos(shadow);"
                    + "watch(shadow);"
                    + "}"
                    + "}"
                    + "}"
                    + "function applyLogos(root){"
                    + "if(!root){return;}"
                    + "var containers=root.querySelectorAll?asArray(root.querySelectorAll(CONTAINERS)):[];"
                    + "if(root.matches&&root.matches(CONTAINERS)){containers.push(root);}"
                    + "for(var i=0;i<containers.length;i++){paintContainer(containers[i]);}"
                    + "var images=root.querySelectorAll?asArray(root.querySelectorAll(IMAGES)):[];"
                    + "if(root.matches&&root.matches(IMAGES)){images.push(root);}"
                    + "for(var j=0;j<images.length;j++){replaceImage(images[j]);}"
                    + "observeShadowRoots(root);"
                    + "}"
                    + "applyLogos(document);"
                    + "if(window.__ssmusicAppLogoInstalled){return;}"
                    + "window.__ssmusicAppLogoInstalled=true;"
                    + "document.addEventListener('yt-navigate-finish',function(){"
                    + "applyLogos(document);"
                    + "},true);"
                    + "watch(document.documentElement);"
                    // Bounded polling covers the initial load, where components attach their
                    // shadow DOM late; afterwards the mutation observer and yt-navigate-finish
                    // listener keep the swap applied without a permanent timer.
                    + "var ticks=0;"
                    + "var timer=setInterval(function(){"
                    + "applyLogos(document);"
                    + "if(++ticks>=15){clearInterval(timer);}"
                    + "},1000);"
                    + "})()";

    /**
     * Delays (in milliseconds) at which {@link #APP_LOGO_SCRIPT} is re-injected after a page
     * finishes loading. YouTube Music's nav bar components can attach their shadow DOM and lay
     * themselves out well after the WebView considers the page finished, so a single injection at
     * that point can run before the logo container exists or has a size.
     */
    private static final long[] APP_LOGO_REINJECT_DELAYS_MS = {300L, 1000L, 2500L, 5000L};

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
                    + "document.addEventListener('visibilitychange',stop,true);"
                    + "document.addEventListener('webkitvisibilitychange',stop,true);"
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
                    + "var thumbnail='';"
                    + "var selectors=['img.image','#thumbnail img','.thumbnail img',"
                    + "'ytmusic-thumbnail-renderer img','.song-media-window img'];"
                    + "for(var j=0;j<selectors.length&&!thumbnail;j++){"
                    + "var image=(player||document).querySelector(selectors[j]);"
                    + "if(image){thumbnail=image.currentSrc||image.src||'';}"
                    + "}"
                    + "window.ssmusicPlayback.setMetadata(title,artist,thumbnail);"
                    + "window.ssmusicPlayback.setPlaying(playing);"
                    + "}"
                    + "}"
                    // Several triggers below (history patches, media events) can all fire within
                    // the same tick, especially right as the app leaves foreground. Coalescing
                    // them avoids flooding the native bridge with a burst of redundant calls at
                    // exactly the moment the process is most fragile.
                    + "var reportTimer=null;"
                    + "var reportBurstCount=0;"
                    // 120ms is comfortably longer than a single synchronous event-dispatch tick
                    // (so genuinely-simultaneous triggers coalesce), yet short enough that the
                    // reported state still feels immediate to anything observing the bridge.
                    + "var REPORT_DEBOUNCE_MS=120;"
                    + "function safeReport(){"
                    + "try{report();}catch(e){"
                    + "if(window.ssmusicPlayback&&window.ssmusicPlayback.logDiagnostic){"
                    + "window.ssmusicPlayback.logDiagnostic('report() threw: '+(e&&e.message?e.message:e));"
                    + "}"
                    + "}"
                    + "}"
                    + "function flushReport(){"
                    + "reportTimer=null;"
                    + "var burst=reportBurstCount;"
                    + "reportBurstCount=0;"
                    + "if(burst>1&&window.ssmusicPlayback&&window.ssmusicPlayback.logDiagnostic){"
                    + "window.ssmusicPlayback.logDiagnostic('Coalesced '+burst+' playback report triggers');"
                    + "}"
                    + "safeReport();"
                    + "}"
                    + "function scheduleReport(){"
                    + "reportBurstCount++;"
                    + "if(reportTimer){return;}"
                    + "reportTimer=setTimeout(flushReport,REPORT_DEBOUNCE_MS);"
                    + "}"
                    + "window.__ssmusicForceReport=function(){"
                    + "if(reportTimer){clearTimeout(reportTimer);reportTimer=null;reportBurstCount=0;}"
                    + "safeReport();"
                    + "};"
                    + "document.addEventListener('play',scheduleReport,true);"
                    + "document.addEventListener('pause',scheduleReport,true);"
                    + "document.addEventListener('ended',scheduleReport,true);"
                    + "window.addEventListener('pagehide',scheduleReport);"
                    + "window.addEventListener('popstate',scheduleReport);"
                    + "window.addEventListener('hashchange',scheduleReport);"
                    + "var pushState=history.pushState;"
                    + "history.pushState=function(){var result=pushState.apply(this,arguments);scheduleReport();return result;};"
                    + "var replaceState=history.replaceState;"
                    + "history.replaceState=function(){var result=replaceState.apply(this,arguments);scheduleReport();return result;};"
                    + "setInterval(scheduleReport,5000);"
                    + "report();"
                    + "})()";

    /**
     * Injects an in-page control for YouTube Music videos. When enabled the script makes the video
     * element fully transparent and draws the current song thumbnail over the video area, renders a
     * horizontally centered toggle button near the top of the player, and exposes
     * {@code __ssmusicSetVideoThumbnailDefault} for native settings changes.
     */
    static String videoDisplayScript(boolean showThumbnailByDefault, String showThumbnailLabel,
            String showVideoLabel, String thumbnailAltText) {
        return "(function(){"
                + "var DEFAULT=" + showThumbnailByDefault + ";"
                + "var SHOW_THUMBNAIL_LABEL=" + jsStringLiteral(showThumbnailLabel) + ";"
                + "var SHOW_VIDEO_LABEL=" + jsStringLiteral(showVideoLabel) + ";"
                + "var THUMBNAIL_ALT=" + jsStringLiteral(thumbnailAltText) + ";"
                + "var ROOT_ID='ssmusic-video-display-root';"
                + "var COVER_ID='ssmusic-video-thumbnail-cover';"
                + "var STYLE_ID='ssmusic-video-display-style';"
                + "var MEDIA_SELECTOR='ytmusic-player-page #movie_player,ytmusic-player-page #song-image,"
                + "ytmusic-player-page #song-media-window,ytmusic-player-page .song-media-window,"
                + "ytmusic-player-page #player,ytmusic-player-page .player,ytmusic-player-page ytmusic-player';"
                + "var scheduled=false;"
                + "var observer=null;"
                + "var observed=null;"
                + "var hidden=null;"
                + "var hiddenOpacity='';"
                + "var hiddenPriority='';"
                + "var showing=typeof window.__ssmusicVideoThumbnailDefault==='boolean'"
                + "?window.__ssmusicVideoThumbnailDefault:DEFAULT;"
                + "window.__ssmusicVideoThumbnailDefault=showing;"
                + "function css(){"
                + "if(document.getElementById(STYLE_ID)){return;}"
                + "var style=document.createElement('style');"
                + "style.id=STYLE_ID;"
                + "style.textContent='#'+ROOT_ID+'{position:absolute!important;top:8px!important;left:50%!important;right:auto!important;transform:translateX(-50%)!important;z-index:2147483647!important}'"
                + "+' #'+ROOT_ID+' button{display:inline-flex!important;align-items:center!important;justify-content:center!important;"
                + "border:1px solid rgba(255,255,255,.28)!important;border-radius:999px!important;padding:10px 18px!important;"
                + "background:rgba(0,0,0,.78)!important;color:#fff!important;font:600 14px/1 sans-serif!important;letter-spacing:.2px!important;"
                + "cursor:pointer!important;box-shadow:0 2px 8px rgba(0,0,0,.45)!important}'"
                + "+' #'+COVER_ID+'{position:absolute!important;object-fit:contain!important;background:#000!important;"
                + "pointer-events:none!important;z-index:2147483646!important}';"
                + "(document.head||document.documentElement).appendChild(style);"
                + "}"
                + "function player(){return document.querySelector('ytmusic-player-page #player,ytmusic-player-page .player,ytmusic-player,ytmusic-player-page')||document.body;}"
                + "function video(){"
                + "var nodes=document.querySelectorAll('video');"
                + "for(var i=0;i<nodes.length;i++){"
                + "var node=nodes[i];var rect=node.getBoundingClientRect();"
                + "if((node.videoWidth>0&&node.videoHeight>0)||(rect.width>120&&rect.height>80)){return node;}"
                + "}"
                + "return null;"
                + "}"
                + "function thumbnail(){"
                + "var selectors=['ytmusic-player-bar img.image','ytmusic-player-bar #thumbnail img',"
                + "'ytmusic-player-bar .thumbnail img','ytmusic-player-bar ytmusic-thumbnail-renderer img',"
                + "'.song-media-window img','ytmusic-player-page img.image','ytmusic-player-page ytmusic-thumbnail-renderer img'];"
                + "for(var i=0;i<selectors.length;i++){"
                + "var image=document.querySelector(selectors[i]);"
                + "var src=image&&(image.currentSrc||image.src);"
                + "if(src&&src.indexOf('data:')!==0){return src;}"
                + "}"
                + "return '';"
                + "}"
                + "function ensureRoot(host){"
                + "var root=document.getElementById(ROOT_ID);"
                + "if(!root){root=document.createElement('div');root.id=ROOT_ID;root.appendChild(document.createElement('button'));}"
                + "if(root.parentNode!==host){host.appendChild(root);}"
                + "return root;"
                + "}"
                + "function observe(){"
                + "var target=player()||document.body||document.documentElement;"
                + "if(observer&&observed===target){return;}"
                + "if(observer){observer.disconnect();}"
                + "observed=target;"
                + "if(!observer){observer=new MutationObserver(function(){scheduleApply();});}"
                + "observer.observe(observed,{childList:true,subtree:true});"
                + "}"
                + "function save(value){"
                + "showing=value;window.__ssmusicVideoThumbnailDefault=value;"
                + "if(window.ssmusicPlayback&&window.ssmusicPlayback.setVideoThumbnailDefault){"
                + "window.ssmusicPlayback.setVideoThumbnailDefault(value);"
                + "}"
                + "apply();"
                + "}"
                + "function restore(node){"
                + "if(hiddenOpacity!==''){node.style.setProperty('opacity',hiddenOpacity,hiddenPriority);}"
                + "else{node.style.removeProperty('opacity');}"
                + "}"
                + "function reveal(){"
                + "if(hidden){restore(hidden);hidden=null;hiddenOpacity='';hiddenPriority='';}"
                + "}"
                + "function conceal(node){"
                + "if(hidden===node){return;}"
                + "reveal();"
                + "hidden=node;"
                + "hiddenOpacity=node.style.getPropertyValue('opacity');"
                + "hiddenPriority=node.style.getPropertyPriority('opacity');"
                + "node.style.setProperty('opacity','0','important');"
                + "}"
                + "function place(cover,node,host){"
                + "var videoRect=node.getBoundingClientRect();"
                + "var hostRect=host.getBoundingClientRect();"
                + "if(videoRect.width>0&&videoRect.height>0){"
                + "cover.style.setProperty('left',(videoRect.left-hostRect.left)+'px','important');"
                + "cover.style.setProperty('top',(videoRect.top-hostRect.top)+'px','important');"
                + "cover.style.setProperty('width',videoRect.width+'px','important');"
                + "cover.style.setProperty('height',videoRect.height+'px','important');"
                + "}else{"
                + "cover.style.setProperty('left','0','important');"
                + "cover.style.setProperty('top','0','important');"
                + "cover.style.setProperty('width','100%','important');"
                + "cover.style.setProperty('height','100%','important');"
                + "}"
                + "}"
                + "function apply(){"
                + "scheduled=false;"
                + "if(observer){observer.disconnect();}"
                + "css();"
                + "var node=video();"
                + "var cover=document.getElementById(COVER_ID);"
                + "var root=document.getElementById(ROOT_ID);"
                + "if(!node){reveal();if(cover){cover.remove();}if(root){root.remove();}observe();return;}"
                + "var host=player();"
                + "if(getComputedStyle(host).position==='static'){host.style.setProperty('position','relative','important');}"
                + "root=ensureRoot(host);"
                + "var button=root.querySelector('button');"
                + "button.type='button';"
                + "button.textContent=showing?SHOW_VIDEO_LABEL:SHOW_THUMBNAIL_LABEL;"
                + "button.setAttribute('aria-pressed',showing?'true':'false');"
                + "button.onclick=function(){save(!showing);};"
                + "var src=thumbnail();"
                + "if(showing&&src){"
                + "if(!cover){cover=document.createElement('img');cover.id=COVER_ID;cover.alt=THUMBNAIL_ALT;}"
                + "if(cover.src!==src){cover.src=src;}"
                + "var videoHost=node.parentElement||host;"
                + "if(getComputedStyle(videoHost).position==='static'){videoHost.style.setProperty('position','relative','important');}"
                + "if(cover.parentNode!==videoHost){videoHost.appendChild(cover);}"
                + "cover.onload=function(){place(cover,node,videoHost);};"
                + "place(cover,node,videoHost);"
                + "conceal(node);"
                + "}else{reveal();if(cover){cover.remove();}}"
                + "observe();"
                + "}"
                + "function scheduleApply(){if(scheduled){return;}scheduled=true;setTimeout(apply,100);}"
                + "function mediaPage(node){"
                + "var page=node&&node.closest('ytmusic-player-page');"
                + "var layout=document.querySelector('ytmusic-app-layout');"
                + "var state=page&&(page.getAttribute('player-ui-state')||(layout&&layout.getAttribute('player-ui-state')));"
                + "return page&&node.isConnected&&(state==='PLAYER_PAGE_OPEN'||state==='FULLSCREEN')"
                + "&&getComputedStyle(page).display!=='none'"
                + "&&getComputedStyle(page).visibility!=='hidden'?page:null;"
                + "}"
                + "function swipeControl(action){"
                + "var selector=action==='down'"
                + "?'ytmusic-player-page .player-minimize-button,ytmusic-player-bar .player-minimize-button,"
                + "ytmusic-player-page .collapse-button,ytmusic-player-bar .toggle-player-page-button,"
                + "ytmusic-player-page [aria-label=\"Minimize player\"],ytmusic-player-page [title=\"Minimize player\"]'"
                + ":action==='up'?'ytmusic-player-page .tab-header.ytmusic-player-page'"
                + ":action==='left'"
                + "?'ytmusic-player-bar .previous-button,ytmusic-player-controls .previous-button'"
                + ":'ytmusic-player-bar .next-button,ytmusic-player-controls .next-button';"
                + "var controls=document.querySelectorAll(selector);"
                + "for(var i=0;i<controls.length&&(action!=='up'||i===0);i++){"
                + "var control=controls[i];var rect=control.getBoundingClientRect();"
                + "if(!control.disabled&&!control.hasAttribute('disabled')&&control.getAttribute('aria-disabled')!=='true'"
                + "&&rect.width>0&&rect.height>0&&getComputedStyle(control).visibility!=='hidden'){return control;}"
                + "}"
                + "return null;"
                + "}"
                + "function installSwipes(){"
                + "var swipe=null;var navigating=false;"
                + "function clearSwipe(){swipe=null;}"
                + "document.addEventListener('yt-navigate-start',function(){navigating=true;clearSwipe();},true);"
                + "document.addEventListener('yt-navigate-finish',function(){navigating=false;clearSwipe();},true);"
                + "window.addEventListener('popstate',clearSwipe,true);"
                + "window.addEventListener('hashchange',clearSwipe,true);"
                + "window.addEventListener('pagehide',clearSwipe,true);"
                + "window.__ssmusicNativeSwipeStart=function(id,fx,fy){"
                + "swipe=null;"
                + "if(location.origin!=='https://music.youtube.com'){return 'rejected: untrusted origin';}"
                + "if(navigating){return 'rejected: navigation in progress';}"
                + "if(!Number.isFinite(fx)||!Number.isFinite(fy)||fx<0||fx>1||fy<0||fy>1){"
                + "return 'rejected: outside viewport';}"
                // Android pixels are normalized to the visible viewport, not display density.
                + "var viewport=window.visualViewport;"
                + "var x=(viewport?viewport.offsetLeft:0)+fx*(viewport?viewport.width:window.innerWidth);"
                + "var y=(viewport?viewport.offsetTop:0)+fy*(viewport?viewport.height:window.innerHeight);"
                + "var target=document.elementFromPoint(x,y);"
                + "if(!target||!target.closest){return 'rejected: no hit target';}"
                + "if(target.closest('button,a,input,select,textarea,[contenteditable=\"true\"],[role=\"slider\"],"
                + "[role=\"tab\"],[role=\"menuitem\"],tp-yt-paper-icon-button,yt-icon-button,"
                + ".ytp-chrome-bottom,.ytp-chrome-top,.ytp-popup,#'+ROOT_ID)){return 'rejected: nested control';}"
                + "var node=target.closest(MEDIA_SELECTOR);var page=mediaPage(node);"
                + "if(!page||!page.contains(target)){return 'rejected: no expanded media surface';}"
                // The media surface itself can be a button, but nested controls must retain touches.
                + "for(var control=target;control&&control!==node;control=control.parentElement){"
                + "if(control.getAttribute('role')==='button'){return 'rejected: nested button';}"
                + "}"
                + "var rect=node.getBoundingClientRect();"
                + "if(rect.width<=0||rect.height<=0||x<rect.left||x>rect.right||y<rect.top||y>rect.bottom){"
                + "return 'rejected: outside media bounds';}"
                + "swipe={id:id,node:node,url:location.href};return 'accepted';"
                + "};"
                + "window.__ssmusicNativeSwipeEnd=function(id,action){"
                + "var gesture=swipe;swipe=null;"
                + "if(location.origin!=='https://music.youtube.com'||navigating||!gesture||gesture.id!==id"
                + "||gesture.url!==location.href||!mediaPage(gesture.node)){return 'rejected: stale media gesture';}"
                + "if(['down','up','left','right'].indexOf(action)<0){return 'rejected: unknown direction';}"
                + "var control=swipeControl(action);if(control){control.click();}"
                + "var command=action==='down'?'minimize':action==='up'?'up next':action==='left'?'previous':'next';"
                + "return 'Media player swipe '+action+': '+command"
                + "+(control?' control clicked':' control unavailable');"
                + "};"
                + "}"
                + "window.__ssmusicApplyVideoDisplay=apply;"
                + "window.__ssmusicSetVideoThumbnailDefault=function(value){showing=!!value;window.__ssmusicVideoThumbnailDefault=showing;apply();};"
                + "if(!window.__ssmusicVideoDisplayInstalled){"
                + "window.__ssmusicVideoDisplayInstalled=true;"
                + "installSwipes();"
                + "document.addEventListener('play',scheduleApply,true);"
                + "document.addEventListener('loadedmetadata',scheduleApply,true);"
                + "document.addEventListener('yt-navigate-finish',function(){setTimeout(scheduleApply,300);},true);"
                + "window.addEventListener('resize',scheduleApply,true);"
                + "}"
                + "apply();"
                + "})()";
    }

    /**
     * Escapes a Java string for safe insertion into single-quoted JavaScript string literals,
     * including quotes, backslashes, newlines, and Unicode line/paragraph separators.
     */
    static String jsStringLiteral(String value) {
        String safeValue = value == null ? "" : value;
        StringBuilder result = new StringBuilder("'");
        for (int i = 0; i < safeValue.length(); i++) {
            char c = safeValue.charAt(i);
            if (c == '\\' || c == '\'') {
                result.append('\\').append(c);
            } else if (c == '\n') {
                result.append("\\n");
            } else if (c == '\r') {
                result.append("\\r");
            } else if (c == '\u2028') {
                result.append("\\u2028");
            } else if (c == '\u2029') {
                result.append("\\u2029");
            } else if (c < 0x20) {
                result.append(String.format(Locale.US, "\\u%04x", (int) c));
            } else {
                result.append(c);
            }
        }
        return result.append('\'').toString();
    }

    private final Handler logoInjectionHandler = new Handler(Looper.getMainLooper());

    private byte[] appLogoBytes;

    private PlaybackWebView webView;
    private ImageButton settingsButton;
    private ImageButton kidModeHomeButton;
    private View statsOverlay;
    private StatsMonitor statsMonitor;
    private AppUpdater appUpdater;
    private SharedPreferences preferences;
    private final ExecutorService passwordExecutor = Executors.newSingleThreadExecutor();
    private ScriptHandler kidModeScriptHandler;
    private String kidModeScript;
    private boolean clearHistoryAfterLoad;
    private PermissionRequest pendingPermissionRequest;
    private volatile boolean playbackActive;
    private boolean usingDefaultUserAgent;
    private boolean playbackBridgeEnabled;
    private boolean mediaCommandReceiverRegistered;
    private boolean keepAliveServiceRunning;
    private long keepAliveStartToken;
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
    private String currentTrackThumbnailUrl;
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
        statsOverlay = findViewById(R.id.stats_overlay);
        statsMonitor = new StatsMonitor(this, findViewById(R.id.stats_values));
        appUpdater = new AppUpdater(this);
        setStatsForNerdsEnabled(preferences.getBoolean(KEY_STATS_FOR_NERDS, false));
        settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            Logger.event(TAG, "Settings panel opened");
            showPreferences();
        });
        kidModeHomeButton = findViewById(R.id.kid_mode_home_button);
        kidModeHomeButton.setOnClickListener(v -> loadUrl(KidModeNavigation.LIBRARY_URL));
        configureWebView();
        registerMediaCommandReceiver();
        settingsButton.setVisibility(View.VISIBLE);
        requestAppPermissions();

        String target = urlFromIntent(getIntent());
        if (isKidModeEnabled()) {
            loadUrl(KidModeNavigation.LIBRARY_URL);
        } else if (savedInstanceState != null && target == null) {
            if (webView.restoreState(savedInstanceState) == null) {
                loadUrl(Preferences.restoreUrl(preferences.getString(KEY_LAST_URL, null)));
            }
        } else {
            loadUrl(target == null
                    ? Preferences.restoreUrl(preferences.getString(KEY_LAST_URL, null))
                    : target);
        }
        if (savedInstanceState == null) {
            appUpdater.checkForUpdates(false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        statsMonitor.onStart();
        Logger.event(TAG, "App entering foreground, playback active: " + playbackActive);
    }

    @Override
    protected void onResume() {
        super.onResume();
        appUpdater.onResume();
        Logger.debug(TAG, "App resumed and interactive");
    }

    @Override
    protected void onPause() {
        appUpdater.onPause();
        super.onPause();
        Logger.debug(TAG, "App paused and no longer interactive");
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
        statsMonitor.onStop();
        Logger.event(TAG, "App leaving foreground, playback active: " + playbackActive
                + ", finishing: " + isFinishing() + ", changing configuration: "
                + isChangingConfigurations());
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
        logoInjectionHandler.removeCallbacksAndMessages(null);
        statsMonitor.destroy();
        appUpdater.destroy();
        passwordExecutor.shutdown();
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
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        // Correlates memory-pressure events with any playback drop reported around the same
        // time, since the OS can reclaim resources from a backgrounded process under pressure.
        Logger.event(TAG, "onTrimMemory, level: " + level + ", playback active: " + playbackActive);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Logger.warn(TAG, "onLowMemory, playback active: " + playbackActive, null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String target = urlFromIntent(intent);
        Logger.event(TAG, "onNewIntent, target: " + target);
        if (target != null) {
            loadUrl(isKidModeEnabled() ? KidModeNavigation.LIBRARY_URL : target);
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
        webView.setMediaSwipesEnabled(true);
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
        updateKidModeScript();
    }

    private boolean isKidModeEnabled() {
        return preferences.contains(KidModePassword.PREFERENCE);
    }

    private String kidModeScript() {
        if (kidModeScript == null) {
            try (InputStream input = getAssets().open("kid_mode.js");
                    ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                kidModeScript = new String(output.toByteArray(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Kid mode script unavailable", e);
            }
        }
        return kidModeScript;
    }

    private void updateKidModeScript() {
        kidModeHomeButton.setVisibility(isKidModeEnabled() ? View.VISIBLE : View.GONE);
        if (kidModeScriptHandler != null) {
            kidModeScriptHandler.remove();
            kidModeScriptHandler = null;
        }
        if (isKidModeEnabled()
                && WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            kidModeScriptHandler = WebViewCompat.addDocumentStartJavaScript(
                    webView, kidModeScript(), new HashSet<>(Arrays.asList("https://music.youtube.com")));
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

    private boolean isShowVideoThumbnailDefault() {
        return preferences.getBoolean(KEY_SHOW_VIDEO_THUMBNAIL, false);
    }

    private void showPreferences() {
        View content = getLayoutInflater().inflate(R.layout.dialog_preferences, null);
        View header = getLayoutInflater().inflate(R.layout.preferences_header, null);
        TextView version = header.findViewById(R.id.app_version);
        version.setText(getString(R.string.app_version_format, BuildConfig.VERSION_NAME));
        header.findViewById(R.id.check_updates_button)
                .setOnClickListener(v -> appUpdater.checkForUpdates(true));

        Spinner themeSpinner = content.findViewById(R.id.theme_spinner);
        int theme = preferences.getInt(KEY_THEME, Preferences.THEME_SYSTEM);
        final int[] themeValues = {
                Preferences.THEME_SYSTEM, Preferences.THEME_LIGHT, Preferences.THEME_DARK
        };
        themeSpinner.setSelection(theme == Preferences.THEME_LIGHT ? 1
                : theme == Preferences.THEME_DARK ? 2 : 0);
        themeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int value = themeValues[position];
                if (value == preferences.getInt(KEY_THEME, Preferences.THEME_SYSTEM)) {
                    return;
                }
                Logger.event(TAG, "Theme preference changed to " + value);
                preferences.edit().putInt(KEY_THEME, value).apply();
                applyTheme(value);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Spinner siteModeSpinner = content.findViewById(R.id.site_mode_spinner);
        siteModeSpinner.setSelection(isDesktopMode() ? 1 : 0);
        siteModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean desktopMode = position == 1;
                if (desktopMode == isDesktopMode()) {
                    return;
                }
                Logger.event(TAG, "Site mode preference changed, desktop: " + desktopMode);
                preferences.edit().putBoolean(KEY_DESKTOP_MODE, desktopMode).apply();
                applyUserAgentForUrl(webView.getUrl());
                webView.reload();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        TextView advancedButton = content.findViewById(R.id.advanced_button);
        View advancedSettings = content.findViewById(R.id.advanced_settings);
        advancedButton.setContentDescription(getString(R.string.advanced_expand_accessibility));
        advancedButton.setOnClickListener(v -> {
            boolean expanded = advancedSettings.getVisibility() != View.VISIBLE;
            advancedSettings.setVisibility(expanded ? View.VISIBLE : View.GONE);
            advancedButton.setText(expanded
                    ? R.string.advanced_expanded : R.string.advanced_collapsed);
            advancedButton.setContentDescription(getString(expanded
                    ? R.string.advanced_collapse_accessibility
                    : R.string.advanced_expand_accessibility));
        });

        Switch kidModeSwitch = content.findViewById(R.id.kid_mode_switch);
        kidModeSwitch.setChecked(isKidModeEnabled());
        kidModeSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (checked == isKidModeEnabled()) {
                return;
            }
            // The displayed value remains the persisted state until password validation succeeds.
            kidModeSwitch.setChecked(isKidModeEnabled());
            showKidModePasswordDialog(kidModeSwitch);
        });

        Switch videoThumbnailSwitch = content.findViewById(R.id.video_thumbnail_switch);
        videoThumbnailSwitch.setChecked(isShowVideoThumbnailDefault());
        videoThumbnailSwitch.setOnCheckedChangeListener((button, checked) -> {
            Logger.event(TAG, "Video thumbnail default changed: " + checked);
            setShowVideoThumbnailDefault(checked, true);
        });

        Switch loggingSwitch = content.findViewById(R.id.logging_switch);
        loggingSwitch.setChecked(Logger.isEnabled());
        loggingSwitch.setOnCheckedChangeListener(
                (button, checked) -> Logger.setEnabled(MainActivity.this, checked));
        content.findViewById(R.id.share_log_button).setOnClickListener(v -> shareLog());
        content.findViewById(R.id.view_log_button).setOnClickListener(v -> LogViewer.show(this));
        content.findViewById(R.id.clear_log_button).setOnClickListener(v -> clearLog());
        content.findViewById(R.id.open_supported_links_button)
                .setOnClickListener(v -> openSupportedLinksSettings());

        Switch statsSwitch = content.findViewById(R.id.stats_for_nerds_switch);
        statsSwitch.setChecked(preferences.getBoolean(KEY_STATS_FOR_NERDS, false));
        statsSwitch.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(KEY_STATS_FOR_NERDS, checked).apply();
            setStatsForNerdsEnabled(checked);
        });

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCustomTitle(header)
                .setView(scrollView)
                .create();
        content.findViewById(R.id.back_button).setOnClickListener(v -> {
            dialog.dismiss();
            goHistory(false);
        });
        content.findViewById(R.id.forward_button).setOnClickListener(v -> {
            dialog.dismiss();
            goHistory(true);
        });
        content.findViewById(R.id.refresh_button).setOnClickListener(v -> {
            dialog.dismiss();
            Logger.event(TAG, "Reload requested from settings");
            webView.reload();
        });
        content.findViewById(R.id.home_button).setOnClickListener(v -> {
            dialog.dismiss();
            loadUrl(Preferences.homeUrl());
        });
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

    private void openSupportedLinksSettings() {
        Uri packageUri = Uri.fromParts("package", getPackageName(), null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                startActivity(new Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, packageUri));
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                Logger.error(TAG, "Unable to open supported-link settings; trying app info", e);
            }
        }
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri));
        } catch (ActivityNotFoundException | SecurityException e) {
            Logger.error(TAG, "Unable to open app info for supported links", e);
            Toast.makeText(this, R.string.supported_links_settings_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void showKidModePasswordDialog(Switch toggle) {
        boolean enabling = !isKidModeEnabled();
        if (enabling && !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            Toast.makeText(this, R.string.kid_mode_webview_required, Toast.LENGTH_LONG).show();
            return;
        }
        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        fields.setPadding(padding, 0, padding, 0);
        EditText password = passwordField(R.string.kid_mode_password);
        EditText confirmation = passwordField(R.string.kid_mode_confirm_password);
        fields.addView(password);
        if (enabling) {
            fields.addView(confirmation);
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(fields);
        ProgressBar loading = new ProgressBar(this);
        loading.setIndeterminate(true);
        loading.setVisibility(View.GONE);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        loadingParams.gravity = Gravity.CENTER_HORIZONTAL;
        loadingParams.setMargins(padding, padding, padding, padding);
        content.addView(loading, loadingParams);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(enabling ? R.string.kid_mode_enable : R.string.kid_mode_unlock)
                .setMessage(enabling ? R.string.kid_mode_setup_message : R.string.kid_mode_unlock_message)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.setOnDismissListener(d -> {
            password.getText().clear();
            confirmation.getText().clear();
        });
        dialog.setOnShowListener(d -> {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (password.length() == 0) {
                    password.setError(getString(R.string.kid_mode_empty_password));
                    return;
                }
                if (enabling && !TextUtils.equals(password.getText(), confirmation.getText())) {
                    confirmation.setError(getString(R.string.kid_mode_password_mismatch));
                    return;
                }
                char[] secret = new char[password.length()];
                password.getText().getChars(0, password.length(), secret, 0);
                String stored = preferences.getString(KidModePassword.PREFERENCE, null);
                password.getText().clear();
                confirmation.getText().clear();
                InputMethodManager keyboard = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (keyboard != null) {
                    keyboard.hideSoftInputFromWindow(password.getWindowToken(), 0);
                }
                password.clearFocus();
                confirmation.clearFocus();
                setKidModePasswordBusy(dialog, fields, loading, enabling, true);
                passwordExecutor.execute(() -> {
                    String verifier = null;
                    int error = 0;
                    try {
                        if (enabling) {
                            verifier = KidModePassword.create(secret);
                        } else if (!KidModePassword.matches(secret, stored)) {
                            error = R.string.kid_mode_wrong_password;
                        }
                    } catch (GeneralSecurityException e) {
                        error = R.string.kid_mode_save_failed;
                    } finally {
                        Arrays.fill(secret, '\0');
                    }
                    String result = verifier;
                    int failure = error;
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed() || !dialog.isShowing()) {
                            return;
                        }
                        if (failure != 0) {
                            setKidModePasswordBusy(dialog, fields, loading, enabling, false);
                            password.setError(getString(failure));
                            return;
                        }
                        SharedPreferences.Editor editor = preferences.edit();
                        if (enabling) {
                            editor.putString(KidModePassword.PREFERENCE, result);
                        } else {
                            editor.remove(KidModePassword.PREFERENCE);
                        }
                        if (!editor.commit()) {
                            // SharedPreferences updates memory even when the disk write fails.
                            if (stored == null) {
                                preferences.edit().remove(KidModePassword.PREFERENCE).commit();
                            } else {
                                preferences.edit().putString(KidModePassword.PREFERENCE, stored).commit();
                            }
                            setKidModePasswordBusy(dialog, fields, loading, enabling, false);
                            password.setError(getString(R.string.kid_mode_save_failed));
                            return;
                        }
                        updateKidModeScript();
                        toggle.setChecked(isKidModeEnabled());
                        webView.stopLoading();
                        webView.clearHistory();
                        clearHistoryAfterLoad = true;
                        webView.evaluateJavascript("(function(){"
                                + "document.querySelectorAll('audio,video').forEach(function(media){media.pause();});"
                                + "if(window.__ssmusicResetKidPlaylist){window.__ssmusicResetKidPlaylist();}"
                                + "else{try{sessionStorage.removeItem('ssmusic.kid.playlist.v1');}catch(e){}}"
                                + "})();", ignored -> {
                                    if (!isFinishing() && !isDestroyed()) {
                                        loadUrl(enabling ? KidModeNavigation.LIBRARY_URL : Preferences.homeUrl());
                                    }
                                });
                        dialog.dismiss();
                    });
                });
            });
        });
        dialog.show();
    }

    private void setKidModePasswordBusy(AlertDialog dialog, View fields, View loading,
                                       boolean enabling, boolean busy) {
        fields.setVisibility(busy ? View.GONE : View.VISIBLE);
        loading.setVisibility(busy ? View.VISIBLE : View.GONE);
        dialog.setMessage(getString(busy
                ? (enabling ? R.string.kid_mode_enabling : R.string.kid_mode_unlocking)
                : (enabling ? R.string.kid_mode_setup_message : R.string.kid_mode_unlock_message)));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!busy);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(!busy);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(busy ? View.GONE : View.VISIBLE);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setVisibility(busy ? View.GONE : View.VISIBLE);
        dialog.setCancelable(!busy);
        dialog.setCanceledOnTouchOutside(!busy);
    }

    private EditText passwordField(int hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setSingleLine(true);
        field.setSaveEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        return field;
    }

    private void setStatsForNerdsEnabled(boolean enabled) {
        statsOverlay.setVisibility(enabled ? View.VISIBLE : View.GONE);
        statsMonitor.setEnabled(enabled);
    }

    /**
     * Persists the video display default. {@code updatePage} is false when the page already owns
     * the new state, which avoids echoing a page button click back through the JavaScript bridge.
     */
    private void setShowVideoThumbnailDefault(boolean showThumbnail, boolean updatePage) {
        preferences.edit().putBoolean(KEY_SHOW_VIDEO_THUMBNAIL, showThumbnail).apply();
        if (updatePage && playbackBridgeEnabled) {
            String script = "(function(){"
                    + "if(window.__ssmusicSetVideoThumbnailDefault){"
                    + "window.__ssmusicSetVideoThumbnailDefault(" + showThumbnail + ");"
                    + "}else{"
                    + "window.__ssmusicVideoThumbnailDefault=" + showThumbnail + ";"
                    + "}"
                    + "})();";
            webView.evaluateJavascript(script, null);
        }
    }

    private void shareLog() {
        File logFile = Logger.logFile(this);
        if (logFile == null || !Logger.hasContent(this)) {
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
        if (isKidModeEnabled()) {
            int current = list.getCurrentIndex();
            for (int i = current + (forward ? 1 : -1);
                    i >= 0 && i < list.getSize(); i += forward ? 1 : -1) {
                if (KidModeNavigation.isAllowed(list.getItemAtIndex(i).getUrl())) {
                    return i - current;
                }
            }
            return 0;
        }
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
        if (isKidModeEnabled()) {
            view.evaluateJavascript(kidModeScript(), null);
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            view.evaluateJavascript(BACKGROUND_PLAYBACK_SCRIPT, null);
        }
        view.evaluateJavascript(AD_HIDING_SCRIPT, null);
        view.evaluateJavascript(OPEN_APP_HIDING_SCRIPT, null);
        view.evaluateJavascript(AD_JSON_PRUNE_SCRIPT, null);
        view.evaluateJavascript(APP_LOGO_SCRIPT, null);
        view.evaluateJavascript(videoDisplayScript(isShowVideoThumbnailDefault(),
                getString(R.string.show_thumbnail_button),
                getString(R.string.show_video_button),
                getString(R.string.video_thumbnail_alt)), null);
        scheduleAppLogoReinjection(view);
    }

    /**
     * Re-runs {@link #APP_LOGO_SCRIPT} at a few short delays after a page finishes loading. See
     * {@link #APP_LOGO_REINJECT_DELAYS_MS} for why a single injection is not always enough.
     */
    private void scheduleAppLogoReinjection(WebView view) {
        WeakReference<WebView> viewRef = new WeakReference<>(view);
        for (long delayMs : APP_LOGO_REINJECT_DELAYS_MS) {
            logoInjectionHandler.postDelayed(() -> {
                WebView target = viewRef.get();
                if (target != null && SiteScope.isPlaybackUrl(target.getUrl())) {
                    Logger.debug(TAG, "Reinjecting app logo after " + delayMs + "ms");
                    target.evaluateJavascript(APP_LOGO_SCRIPT, null);
                }
            }, delayMs);
        }
    }

    /**
     * Reads the bundled logo once and keeps the encoded PNG bytes around, so every request for
     * {@link #APP_LOGO_PATH} is answered from memory instead of re-reading the resource.
     *
     * @return the PNG bytes of the bundled logo, or null when the resource cannot be read
     */
    private synchronized byte[] appLogoBytes() {
        if (appLogoBytes == null) {
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            try (InputStream logo = getResources().openRawResource(R.drawable.app_logo)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = logo.read(buffer)) >= 0) {
                    encoded.write(buffer, 0, read);
                }
            } catch (Resources.NotFoundException | IOException e) {
                Logger.warn(TAG, "App logo resource unavailable", e);
                return null;
            }
            appLogoBytes = encoded.toByteArray();
        }
        return appLogoBytes;
    }

    /**
     * @param url a request URL seen by the WebView
     * @return true when the request is the WebView asking for the bundled app logo
     */
    static boolean isAppLogoRequest(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }
        int pathStart = lower.indexOf('/', lower.indexOf("://") + 3);
        if (pathStart < 0) {
            return false;
        }
        int pathEnd = lower.length();
        for (int i = pathStart; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == '?' || c == '#') {
                pathEnd = i;
                break;
            }
        }
        return lower.substring(pathStart, pathEnd).equals(APP_LOGO_PATH);
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
        if (isKidModeEnabled()) {
            if (kidModeScriptHandler == null) {
                Toast.makeText(this, R.string.kid_mode_webview_required, Toast.LENGTH_LONG).show();
                url = "about:blank";
            } else if (!KidModeNavigation.isAllowed(url)) {
                url = KidModeNavigation.LIBRARY_URL;
            }
        }
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
        if (currentTrackThumbnailUrl != null && !currentTrackThumbnailUrl.isEmpty()) {
            serviceIntent.putExtra(PlaybackKeepAliveService.EXTRA_SYNC_THUMBNAIL_URL,
                    currentTrackThumbnailUrl);
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
        public void setMetadata(String title, String artist, String thumbnailUrl) {
            runOnUiThread(() -> {
                String sanitizedTitle = sanitizeMetadata(title);
                String sanitizedArtist = sanitizeMetadata(artist);
                String sanitizedThumbnailUrl = sanitizeThumbnailUrl(thumbnailUrl);
                if (sanitizedTitle.isEmpty()) {
                    return;
                }
                if (sanitizedTitle.equals(currentTrackTitle)
                        && sanitizedArtist.equals(currentTrackArtist)
                        && java.util.Objects.equals(sanitizedThumbnailUrl, currentTrackThumbnailUrl)) {
                    return;
                }
                Logger.event(TAG, "Track metadata: " + sanitizedTitle + " - " + sanitizedArtist);
                currentTrackTitle = sanitizedTitle;
                currentTrackArtist = sanitizedArtist;
                currentTrackThumbnailUrl = sanitizedThumbnailUrl;
                if (keepAliveServiceRunning) {
                    startPlaybackKeepAliveService(null);
                }
            });
        }

        @JavascriptInterface
        public void logDiagnostic(String message) {
            Logger.debug(TAG, "Bridge diagnostic: " + message);
        }

        @JavascriptInterface
        public void setVideoThumbnailDefault(boolean showThumbnail) {
            runOnUiThread(() -> {
                Logger.event(TAG, "Video thumbnail default set from page: " + showThumbnail);
                setShowVideoThumbnailDefault(showThumbnail, false);
            });
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

    private String sanitizeThumbnailUrl(String value) {
        return Urls.sanitizeHttpsThumbnailUrl(value, MAX_THUMBNAIL_URL_LENGTH);
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
            return interceptRequest(request.getUrl().toString());
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            return interceptRequest(url);
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            webView.cancelMediaSwipe("navigation");
            Logger.event(TAG, "Page started: " + url);
            logoInjectionHandler.removeCallbacksAndMessages(null);
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
            if (clearHistoryAfterLoad) {
                view.clearHistory();
                clearHistoryAfterLoad = false;
            }
        }

        @TargetApi(Build.VERSION_CODES.M)
        @Override
        public void onReceivedError(WebView view, WebResourceRequest request,
                WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Logger.warn(TAG, "Resource error " + error.getErrorCode() + " for "
                        + request.getUrl() + ": " + error.getDescription(), null);
            }
        }

        @TargetApi(Build.VERSION_CODES.M)
        @Override
        public void onReceivedHttpError(WebView view, WebResourceRequest request,
                WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Logger.warn(TAG, "HTTP error " + errorResponse.getStatusCode() + " for "
                        + request.getUrl(), null);
            }
        }

        private boolean handleUrl(WebView view, String url) {
            String normalized = SiteScope.normalizeInAppUrl(url);
            if (isKidModeEnabled()
                    && (kidModeScriptHandler == null || !KidModeNavigation.isAllowed(normalized))) {
                loadUrl(KidModeNavigation.LIBRARY_URL);
                return true;
            }
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

        private WebResourceResponse interceptRequest(String url) {
            if (isAppLogoRequest(url)) {
                Logger.debug(TAG, "Serving app logo: " + url);
                return appLogoResponse();
            }
            return blockedResponse(url);
        }

        private WebResourceResponse appLogoResponse() {
            byte[] logo = appLogoBytes();
            if (logo == null) {
                return null;
            }
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-cache");
            return new WebResourceResponse("image/png", null, 200, "OK", headers,
                    new ByteArrayInputStream(logo));
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
