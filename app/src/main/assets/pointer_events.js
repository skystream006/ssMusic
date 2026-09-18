(function () {
    'use strict';
    if (location.origin !== 'https://music.youtube.com' || window !== window.top
            || window.__ssmusicPointerEventsInstalled) {
        return;
    }
    window.__ssmusicPointerEventsInstalled = true;

    var layout = 'html > body > ytmusic-app > ytmusic-app-layout';
    var containers = 'html,html > body,html > body > ytmusic-app,' + layout
        + ',' + layout + ' > #layout'
        + ',' + layout + ' > #content'
        + ',' + layout + ' > #layout > #content'
        + ',' + layout + ' > ytmusic-nav-bar'
        + ',' + layout + ' > #nav-bar'
        + ',' + layout + ' > #nav-bar > ytmusic-nav-bar'
        + ',' + layout + ' > #layout > #nav-bar';
    var restricted = '[inert],[hidden],[disabled],[aria-hidden="true"],[aria-disabled="true"]';
    var dialogs = 'dialog[open],[role="dialog"],[role="alertdialog"],[aria-modal="true"],'
        + 'tp-yt-paper-dialog,tp-yt-iron-dropdown,tp-yt-iron-overlay-backdrop';
    var owned = new Map();
    var timer = null;
    var suspended = false;

    function visible(node, includeTransparent) {
        for (var ancestor = node; ancestor; ancestor = ancestor.parentElement) {
            var style = getComputedStyle(ancestor);
            if (style.display === 'none' || style.visibility !== 'visible'
                    || !includeTransparent && style.opacity === '0') {
                return false;
            }
        }
        var rect = node.getBoundingClientRect();
        return rect.width > 0 && rect.height > 0;
    }

    function blocked() {
        if (window.__ssmusicKidModeInstalled || document.fullscreenElement
                || document.webkitFullscreenElement
                || document.querySelector('ytmusic-app-layout[player-ui-state="PLAYER_PAGE_OPEN"],'
                    + 'ytmusic-player-page[player-ui-state="PLAYER_PAGE_OPEN"]')) {
            return true;
        }
        // An opening modal can still be transparent while it already blocks the page.
        return Array.from(document.querySelectorAll(dialogs)).some(function (node) {
            return visible(node, true);
        })
            || Array.from(document.querySelectorAll('video')).some(function (media) {
                return media.webkitDisplayingFullscreen;
            });
    }

    function restore() {
        owned.forEach(function (previous, node) {
            // Do not overwrite a newer inline value supplied by the page.
            if (node.style.getPropertyValue('pointer-events') === 'auto'
                    && node.style.getPropertyPriority('pointer-events') === 'important') {
                if (previous.value) {
                    node.style.setProperty('pointer-events', previous.value, previous.priority);
                } else {
                    node.style.removeProperty('pointer-events');
                }
            }
        });
        owned.clear();
    }

    function eligible(node) {
        if (!node.isConnected || node.closest(restricted) || !visible(node)) {
            return false;
        }
        // Ancestors are visited first. Never cross a still-disabled, unhandled wrapper.
        for (var ancestor = node.parentElement; ancestor; ancestor = ancestor.parentElement) {
            if (getComputedStyle(ancestor).pointerEvents === 'none') {
                return false;
            }
        }
        return getComputedStyle(node).pointerEvents === 'none';
    }

    function apply() {
        clearTimeout(timer);
        timer = null;
        observer.disconnect();
        try {
            // Recheck the site's cascade without our overrides, including inherited values.
            restore();
            if (!suspended && !blocked()) {
                document.querySelectorAll(containers).forEach(function (node) {
                    if (eligible(node)) {
                        owned.set(node, {
                            value: node.style.getPropertyValue('pointer-events'),
                            priority: node.style.getPropertyPriority('pointer-events')
                        });
                        node.style.setProperty('pointer-events', 'auto', 'important');
                    }
                });
            }
        } finally {
            if (!suspended) {
                observe();
            }
        }
    }

    function schedule() {
        if (!suspended && timer === null) {
            timer = setTimeout(apply, 0);
        }
    }

    var observer = new MutationObserver(schedule);
    function observe() {
        observer.observe(document, {
            childList: true, subtree: true, attributes: true,
            attributeFilter: ['style', 'class', 'id', 'hidden', 'inert', 'disabled',
                'aria-hidden', 'aria-disabled', 'aria-modal', 'role', 'open', 'opened',
                'player-ui-state']
        });
    }

    ['yt-navigate-finish', 'yt-page-data-updated', 'fullscreenchange', 'webkitfullscreenchange',
        'webkitbeginfullscreen', 'webkitendfullscreen', 'transitionend', 'animationend', 'load']
        .forEach(function (name) {
            document.addEventListener(name, schedule, true);
        });
    window.addEventListener('resize', schedule);
    window.addEventListener('pagehide', function () {
        suspended = true;
        clearTimeout(timer);
        timer = null;
        observer.disconnect();
        restore();
    });
    window.addEventListener('pageshow', function () {
        suspended = false;
        apply();
    });
    apply();
}());
