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
                || getComputedStyle(page).display === 'none'
                || getComputedStyle(page).visibility === 'hidden') {
            return null;
        }
        var rect = page.getBoundingClientRect();
        var barRect = bar.getBoundingClientRect();
        if (rect.width <= 0 || rect.height <= 0 || barRect.width <= 0 || barRect.height <= 0
                || barRect.top <= 0 || barRect.bottom > window.innerHeight + 1
                || getComputedStyle(bar).visibility === 'hidden'
                || browse.closest('[inert],[aria-hidden="true"]')) {
            return null;
        }
        return {layout: layout, page: page, bar: bar, browse: browse, media: media,
            player: player, pageState: pageState, layoutState: layoutState, rect: rect};
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
        schedule();
        return true;
    }

    function geometry(candidate) {
        var barRect = candidate.bar.getBoundingClientRect();
        var bottom = window.innerHeight - barRect.top + 12;
        var height = active ? active.rect.height : candidate.rect.height;
        var width = active ? active.rect.width : candidate.rect.width;
        var scale = Math.min(1, Math.min(300, window.innerWidth - 24) / width,
            Math.min(window.innerHeight * 0.32, barRect.top - 100) / height);
        return {bottom: bottom, scale: scale, height: height * scale};
    }

    function present(candidate) {
        var size = geometry(candidate);
        if (size.scale <= 0 || size.height < 48) {
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
        if (compactStyle.textContent !== css) {
            compactStyle.textContent = css;
        }
        if (!compactStyle.isConnected) {
            document.head.appendChild(compactStyle);
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
        if (!candidate || geometry(candidate).height < 48) {
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
        if (active && (!same(candidate) || !present(candidate))) {
            expand();
            candidate = current();
        }
        if (!controlsStyle.isConnected) {
            document.head.appendChild(controlsStyle);
        }
        if (!button.isConnected) {
            document.body.appendChild(button);
        }
        var available = !!candidate && geometry(candidate).height >= 48;
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
        var size = geometry(candidate);
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
        cancel(event);
        if (active) {
            expand();
            update();
        } else {
            compact();
        }
    }
    button.addEventListener('click', toggle);

    // Only dedicated player presentation buttons; transport and bar content keep native behavior.
    document.addEventListener('click', function (event) {
        var target = event.target instanceof Element ? event.target : null;
        var control = target && target.closest(
            'ytmusic-player-page .player-minimize-button,ytmusic-player-page .collapse-button,'
            + 'ytmusic-player-page .player-expand-button,ytmusic-player-bar .player-minimize-button,'
            + 'ytmusic-player-bar .player-expand-button,ytmusic-player-bar .toggle-player-page-button');
        if (!control || !control.matches('button,[role="button"],yt-icon-button,tp-yt-paper-icon-button')
                || control.disabled || control.hasAttribute('disabled')
                || control.getAttribute('aria-disabled') === 'true') {
            return;
        }
        var page = control.closest('ytmusic-player-page') || document.querySelector('ytmusic-player-page');
        var layout = document.querySelector('ytmusic-app-layout');
        var expanded = page && (state(page) || layout && state(layout)) === 'PLAYER_PAGE_OPEN';
        if (active || expanded) {
            // Never fall through to native minimize when this presentation is unsupported.
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
    window.addEventListener('pagehide', function () {
        suspended = true;
        observer.disconnect();
        clearTimeout(timer);
        timer = null;
        expand();
        button.remove();
        controlsStyle.remove();
    }, true);
    window.addEventListener('pageshow', function () {
        suspended = false;
        observe();
        schedule();
    }, true);
    window.__ssmusicCompactPlayer = {compact: compact, expand: expand};
    observe();
    schedule();
}());
