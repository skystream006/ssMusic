(function () {
    'use strict';
    if (location.origin !== 'https://music.youtube.com' || window.__ssmusicCompactPlayer) {
        return;
    }

    var labels = window.__ssmusicCompactPlayerLabels || {};
    var ROLE = 'data-ssmusic-compact-role';
    var active = null;
    var timer = null;
    var suspended = false;
    var nativeMinimizing = false;
    var minimizeControls = 'ytmusic-player-page .player-minimize-button,ytmusic-player-page .collapse-button,'
        + 'ytmusic-player-bar .player-minimize-button,ytmusic-player-bar .toggle-player-page-button';
    var presentationControls = minimizeControls
        + ',ytmusic-player-page .player-expand-button,ytmusic-player-bar .player-expand-button';
    var button = document.createElement('button');
    button.id = 'ssmusic-compact-player-toggle';
    button.type = 'button';
    button.hidden = true;
    var controlsStyle = document.createElement('style');
    controlsStyle.textContent = '#ssmusic-compact-player-toggle:not([hidden]){'
        + 'position:fixed;right:12px;z-index:10002;min-height:44px;max-width:calc(100vw - 24px);'
        + 'padding:8px 14px;border:1px solid #aaa;border-radius:22px;background:#202020;'
        + 'color:#fff;font:14px sans-serif;cursor:pointer;pointer-events:auto}'
        + '#ssmusic-compact-player-toggle:focus-visible{outline:3px solid #fff;outline-offset:2px}';
    var compactStyle = document.createElement('style');

    function state(node) {
        return node.getAttribute('player-ui-state');
    }

    function fullscreen(media) {
        return !!(document.fullscreenElement || document.webkitFullscreenElement
            || media && media.webkitDisplayingFullscreen);
    }

    function current() {
        var layout = document.querySelector('ytmusic-app-layout');
        var page = layout && layout.querySelector('ytmusic-player-page');
        var bar = layout && layout.querySelector('ytmusic-player-bar');
        var browse = layout && layout.querySelector('#content');
        var media = page && page.querySelector('video,audio');
        var player = page && page.querySelector('#movie_player');
        if (!layout || !page || !bar || !browse || !media || !player
                || browse.contains(page) || page.contains(browse) || page.contains(bar)
                || !media.isConnected || fullscreen(media)) {
            return null;
        }
        var pageState = state(page);
        var layoutState = state(layout);
        if ((pageState || layoutState) !== 'PLAYER_PAGE_OPEN'
                || pageState && pageState !== 'PLAYER_PAGE_OPEN'
                || layoutState && layoutState !== 'PLAYER_PAGE_OPEN') {
            return null;
        }
        if (page.closest('[hidden],[inert],[aria-hidden="true"]')
                || browse.closest('[inert],[aria-hidden="true"]')) {
            return null;
        }
        return {layout: layout, page: page, bar: bar, browse: browse, media: media,
            player: player, pageState: pageState, layoutState: layoutState,
            rect: page.getBoundingClientRect()};
    }

    function same(candidate) {
        return candidate && ['layout', 'page', 'bar', 'browse', 'media', 'player',
            'pageState', 'layoutState'].every(function (key) {
            return candidate[key] === active[key];
        });
    }

    function mark(node, value) {
        active.marks.push({node: node, value: value, previous: node.getAttribute(ROLE)});
        node.setAttribute(ROLE, value);
    }

    function refreshVideoDisplay() {
        if (!suspended && typeof window.__ssmusicApplyVideoDisplay === 'function') {
            window.__ssmusicApplyVideoDisplay();
        }
    }

    function expand() {
        if (!active) {
            return false;
        }
        compactStyle.remove();
        active.marks.forEach(function (entry) {
            if (entry.node.getAttribute(ROLE) === entry.value) {
                if (entry.previous === null) {
                    entry.node.removeAttribute(ROLE);
                } else {
                    entry.node.setAttribute(ROLE, entry.previous);
                }
            }
        });
        active = null;
        refreshVideoDisplay();
        schedule();
        return true;
    }

    function geometry(candidate) {
        if (!candidate) {
            return null;
        }
        var rect = candidate.rect;
        var barRect = candidate.bar.getBoundingClientRect();
        if (window.innerWidth <= 24 || window.innerHeight <= 0
                || rect.width <= 0 || rect.height <= 0 || barRect.width <= 0 || barRect.height <= 0
                || barRect.top <= 0 || barRect.bottom > window.innerHeight + 1
                || getComputedStyle(candidate.page).display === 'none'
                || getComputedStyle(candidate.page).visibility === 'hidden'
                || getComputedStyle(candidate.bar).visibility === 'hidden') {
            return null;
        }
        var bottom = window.innerHeight - barRect.top + 12;
        var height = active ? active.rect.height : candidate.rect.height;
        var width = active ? active.rect.width : candidate.rect.width;
        var scale = Math.min(1, Math.min(300, window.innerWidth - 24) / width,
            Math.min(window.innerHeight * 0.32, barRect.top - 100) / height);
        if (!Number.isFinite(scale) || scale <= 0 || height * scale < 48) {
            return null;
        }
        return {bottom: bottom, scale: scale, height: height * scale};
    }

    function present(candidate) {
        var size = geometry(candidate);
        if (!size) {
            return false;
        }
        // Scale the existing page, not a clone or a reconstructed media element.
        var css = '[' + ROLE + '="page"]{position:fixed!important;'
            + 'inset:auto 12px ' + size.bottom + 'px auto!important;'
            + 'width:' + active.rect.width + 'px!important;height:' + active.rect.height + 'px!important;'
            + 'min-width:0!important;min-height:0!important;max-width:none!important;max-height:none!important;'
            + 'margin:0!important;transform:scale(' + size.scale + ')!important;'
            + 'transform-origin:bottom right!important;transition:none!important;'
            + 'z-index:10001!important;pointer-events:auto!important}'
            + '[' + ROLE + '="browse"]{display:block!important;visibility:visible!important;'
            + 'opacity:1!important;pointer-events:auto!important;overflow:auto!important;'
            + 'position:relative!important;transform:none!important;'
            + 'height:calc(100vh - ' + (window.innerHeight - candidate.bar.getBoundingClientRect().top)
            + 'px - var(--ytmusic-nav-bar-height,64px))!important}'
            + '[' + ROLE + '="layout"],[' + ROLE + '="body"],[' + ROLE + '="root"]{overflow:auto!important}';
        var geometryChanged = compactStyle.textContent !== css || !compactStyle.isConnected;
        if (compactStyle.textContent !== css) {
            compactStyle.textContent = css;
        }
        if (!compactStyle.isConnected) {
            document.head.appendChild(compactStyle);
        }
        if (geometryChanged) {
            refreshVideoDisplay();
        }
        return true;
    }

    function compact() {
        if (suspended) {
            return false;
        }
        var candidate = current();
        if (active) {
            if (same(candidate)) {
                return true;
            }
            expand();
            return false;
        }
        if (!geometry(candidate)) {
            if (window.ssmusicPlayback && window.ssmusicPlayback.logDiagnostic) {
                window.ssmusicPlayback.logDiagnostic('Compact player unavailable: '
                    + (candidate ? 'unusable player or transport bounds' : 'unsupported structure or player state'));
            }
            return false;
        }
        active = candidate;
        active.marks = [];
        mark(candidate.page, 'page');
        mark(candidate.browse, 'browse');
        mark(candidate.layout, 'layout');
        mark(document.body, 'body');
        mark(document.documentElement, 'root');
        if (!present(candidate)) {
            expand();
            return false;
        }
        update();
        return true;
    }

    function update() {
        clearTimeout(timer);
        timer = null;
        if (suspended || !document.body || !document.head) {
            return;
        }
        var candidate = current();
        if (active && !same(candidate)) {
            expand();
            candidate = current();
        }
        // Hidden/resizing WebViews can briefly have no usable bounds. Keep the last
        // compact CSS until layout recovers, rather than exposing the full player.
        if (active && !present(candidate)) {
            if (!button.hidden) {
                button.hidden = true;
            }
            return;
        }
        if (!controlsStyle.isConnected) {
            document.head.appendChild(controlsStyle);
        }
        if (!button.isConnected) {
            document.body.appendChild(button);
        }
        var size = geometry(candidate);
        var available = !!size;
        if (button.hidden === available) {
            button.hidden = !available;
        }
        if (!available) {
            return;
        }
        var label = active ? labels.expand || 'Expand player' : labels.compact || 'Compact player';
        if (button.textContent !== label) {
            button.textContent = label;
            button.setAttribute('aria-label', label);
            button.title = label;
        }
        var expanded = active ? 'false' : 'true';
        if (button.getAttribute('aria-expanded') !== expanded) {
            button.setAttribute('aria-expanded', expanded);
        }
        var bottom = (size.bottom + (active ? size.height + 8 : 0)) + 'px';
        if (button.style.bottom !== bottom) {
            button.style.bottom = bottom;
        }
    }

    function schedule() {
        if (!suspended && timer === null) {
            timer = setTimeout(update, 50);
        }
    }

    function cancel(event) {
        event.preventDefault();
        event.stopImmediatePropagation();
    }

    function toggle(event) {
        if (active ? expand() : compact()) {
            cancel(event);
            update();
        }
    }
    button.addEventListener('click', toggle);

    function enabled(control) {
        return control.matches('button,[role="button"],yt-icon-button,tp-yt-paper-icon-button')
            && !control.disabled && !control.hasAttribute('disabled')
            && control.getAttribute('aria-disabled') !== 'true';
    }

    function expanded(control) {
        var page = control.closest('ytmusic-player-page') || document.querySelector('ytmusic-player-page');
        var layout = document.querySelector('ytmusic-app-layout');
        return page && (state(page) || layout && state(layout)) === 'PLAYER_PAGE_OPEN';
    }

    function minimize() {
        if (suspended) {
            return 'unavailable: page suspended';
        }
        if (compact()) {
            return 'compact applied';
        }
        var controls = document.querySelectorAll(minimizeControls);
        for (var i = 0; i < controls.length; i++) {
            var control = controls[i];
            var rect = control.getBoundingClientRect();
            if (!enabled(control) || !expanded(control) || rect.width <= 0 || rect.height <= 0
                    || control.closest('[hidden],[inert],[aria-hidden="true"]')
                    || getComputedStyle(control).visibility === 'hidden') {
                continue;
            }
            nativeMinimizing = true;
            try {
                control.click();
            } finally {
                nativeMinimizing = false;
            }
            return 'native minimize control clicked';
        }
        return 'unavailable: no native minimize control';
    }

    // Only dedicated player presentation buttons; transport and bar content keep native behavior.
    document.addEventListener('click', function (event) {
        if (nativeMinimizing || suspended) {
            return;
        }
        var target = event.target instanceof Element ? event.target : null;
        var control = target && target.closest(presentationControls);
        if (!control || !enabled(control)) {
            return;
        }
        if (active || expanded(control)) {
            // Preserve the site's button action when app presentation cannot be applied.
            toggle(event);
        }
    }, true);

    var observer = new MutationObserver(function () {
        if (active && !same(current())) {
            expand();
        }
        schedule();
    });
    function observe() {
        observer.observe(document, {childList: true, subtree: true, attributes: true,
            attributeFilter: ['player-ui-state', 'hidden', 'inert', 'aria-hidden', 'style', 'class']});
    }
    ['yt-navigate-start', 'yt-navigate-finish', 'yt-page-data-updated', 'yt-player-video-changed']
        .forEach(function (name) { document.addEventListener(name, schedule, true); });
    ['fullscreenchange', 'webkitfullscreenchange'].forEach(function (name) {
        document.addEventListener(name, function () {
            expand();
            schedule();
        }, true);
    });
    ['resize', 'popstate', 'hashchange'].forEach(function (name) {
        window.addEventListener(name, schedule, true);
    });
    window.addEventListener('pagehide', function (event) {
        suspended = true;
        observer.disconnect();
        clearTimeout(timer);
        timer = null;
        // A cached document will return with the same player and owned styling.
        if (!event.persisted) {
            expand();
        }
        button.remove();
        controlsStyle.remove();
    }, true);
    window.addEventListener('pageshow', function () {
        suspended = false;
        observe();
        schedule();
    }, true);
    window.__ssmusicCompactPlayer = {compact: compact, minimize: minimize, expand: expand, refresh: schedule};
    observe();
    schedule();
}());
