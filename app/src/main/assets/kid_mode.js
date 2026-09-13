(function () {
    'use strict';
    if (location.origin !== 'https://music.youtube.com' || window.__ssmusicKidModeInstalled) {
        return;
    }
    window.__ssmusicKidModeInstalled = true;

    var LIBRARY = 'https://music.youtube.com/library';
    var STORAGE_KEY = 'ssmusic.kid.playlist.v1';
    var selected = null;
    var scheduled = false;
    var changingAutoplay = false;
    var nativePlay = HTMLMediaElement.prototype.play;
    var SONG_ROWS = 'ytmusic-playlist-shelf-renderer ytmusic-responsive-list-item-renderer';
    var HIDDEN = 'ytmusic-settings-button,#mini-guide,ytmusic-search-box,ytmusic-menu-renderer,#automix';
    var LOGO = 'ytmusic-logo,.ytmusic-logo,ytmusic-nav-bar .logo,ytmusic-nav-bar #logo';

    function listId(value) {
        return typeof value === 'string' && /^[A-Za-z0-9_-]+$/.test(value) && !/^RD/i.test(value);
    }

    function videoId(value) {
        return typeof value === 'string' && /^[A-Za-z0-9_-]{11}$/.test(value);
    }

    function url(value) {
        try {
            return new URL(value, location.href);
        } catch (ignored) {
            return null;
        }
    }

    function member(id) {
        return !!selected && selected.ids.has(id);
    }

    // Only a previously visited playlist detail page may authorize a reload of /watch.
    try {
        var saved = JSON.parse(sessionStorage.getItem(STORAGE_KEY));
        if (saved && listId(saved.list) && Array.isArray(saved.ids)
                && saved.ids.length && saved.ids.every(videoId)) {
            selected = {list: saved.list, ids: new Set(saved.ids)};
        }
    } catch (ignored) {}

    function remember() {
        try {
            if (selected) {
                sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
                    list: selected.list, ids: Array.from(selected.ids)
                }));
            } else {
                sessionStorage.removeItem(STORAGE_KEY);
            }
        } catch (ignored) {}
    }

    function allowed(target) {
        if (!target || target.origin !== 'https://music.youtube.com'
                || target.username || target.password) {
            return false;
        }
        if (/^\/library(?:\/|$)/.test(target.pathname)) {
            return true;
        }
        var list = target.searchParams.get('list');
        if (!listId(list) || target.searchParams.getAll('list').length !== 1) {
            return false;
        }
        if (target.pathname === '/playlist') {
            return true;
        }
        return target.pathname === '/watch' && !!selected && selected.list === list
            && target.searchParams.getAll('v').length === 1
            && member(target.searchParams.get('v'));
    }

    function home(target) {
        return target && target.origin === 'https://music.youtube.com' && target.pathname === '/';
    }

    function data(node) {
        return node && (node.data || node.__data && node.__data.data) || {};
    }

    function watchEndpoints(row) {
        var result = [];
        var value = data(row);
        function add(endpoint) {
            if (endpoint && endpoint.watchEndpoint) {
                result.push(endpoint.watchEndpoint);
            }
        }
        add(value.navigationEndpoint);
        add(value.onTap);
        // Song titles expose their endpoint here on Polymer playlist rows.
        (value.flexColumns || []).forEach(function (column) {
            var text = column.musicResponsiveListItemFlexColumnRenderer;
            (text && text.text && text.text.runs || []).forEach(function (run) {
                add(run.navigationEndpoint);
            });
        });
        row.querySelectorAll('a[href]').forEach(function (anchor) {
            var target = url(anchor.href);
            if (target && target.origin === location.origin && target.pathname === '/watch') {
                result.push({
                    videoId: target.searchParams.get('v'),
                    playlistId: target.searchParams.get('list')
                });
            }
        });
        return result;
    }

    function capturePlaylist() {
        var page = url(location.href);
        if (!page || page.pathname !== '/playlist' || !allowed(page)) {
            return;
        }
        var list = page.searchParams.get('list');
        if (selected && selected.list !== list) {
            selected = null;
            remember();
        }
        var ids = new Set();
        document.querySelectorAll(SONG_ROWS).forEach(function (row) {
            // Never learn membership from the player queue, suggestions, or automix.
            if (row.closest('ytmusic-player-queue,#automix,[hidden],[aria-hidden="true"]')) {
                return;
            }
            var shelf = row.closest('ytmusic-playlist-shelf-renderer');
            var shelfList = data(shelf).playlistId;
            var endpoints = watchEndpoints(row);
            endpoints.forEach(function (endpoint) {
                if (videoId(endpoint.videoId)
                        && (endpoint.playlistId || shelfList) === list) {
                    ids.add(endpoint.videoId);
                }
            });
            var item = data(row).playlistItemData;
            if (shelfList === list && item && videoId(item.videoId)) {
                ids.add(item.videoId);
            }
        });
        if (ids.size) {
            // Retain already verified rows as the playlist virtualizes or loads more pages.
            if (!selected) {
                selected = {list: list, ids: new Set()};
            }
            var previousSize = selected.ids.size;
            ids.forEach(function (id) { selected.ids.add(id); });
            if (previousSize !== selected.ids.size) {
                remember();
            }
        }
    }

    function player() {
        return document.querySelector('#movie_player');
    }

    function canPlay() {
        if (!selected || !allowed(url(location.href))) {
            return false;
        }
        var page = url(location.href);
        if (page.pathname === '/playlist' && page.searchParams.get('list') !== selected.list) {
            return false;
        }
        try {
            var api = player();
            var track = api && api.getVideoData && api.getVideoData();
            var list = api && api.getPlaylistId && api.getPlaylistId();
            // The player, not a stale watch URL, identifies the media actually being played.
            // Two different member IDs are fine during a legitimate next-track transition.
            return !!track && member(track.video_id) && list === selected.list;
        } catch (ignored) {
            return false;
        }
    }

    function stopPlayback() {
        document.querySelectorAll('video,audio').forEach(function (media) {
            media.autoplay = false;
            if (!media.paused) {
                media.pause();
            }
        });
    }

    HTMLMediaElement.prototype.play = function () {
        if (!canPlay()) {
            this.pause();
            return Promise.reject(new DOMException('Kid mode: unverified playlist track', 'NotAllowedError'));
        }
        return nativePlay.apply(this, arguments);
    };

    function enforcePlayback() {
        if (!canPlay()) {
            stopPlayback();
        }
    }

    ['play', 'playing', 'timeupdate', 'loadedmetadata', 'durationchange', 'emptied'].forEach(function (name) {
        document.addEventListener(name, enforcePlayback, true);
    });

    function canSkip(direction) {
        if (!canPlay()) {
            return false;
        }
        try {
            var api = player();
            var queue = api.getPlaylist();
            var index = api.getPlaylistIndex();
            return Array.isArray(queue) && Number.isInteger(index)
                && member(queue[index + direction]);
        } catch (ignored) {
            return false;
        }
    }

    function hide(node) {
        if (node.style.getPropertyValue('display') !== 'none') {
            node.style.setProperty('display', 'none', 'important');
        }
    }

    function disableAutoplay() {
        document.querySelectorAll('#automix').forEach(function (section) {
            section.querySelectorAll('button,[role="switch"],tp-yt-paper-toggle-button,yt-toggle-button-renderer')
                .forEach(function (toggle) {
                    var on = toggle.checked === true || toggle.getAttribute('aria-checked') === 'true'
                        || toggle.getAttribute('aria-pressed') === 'true';
                    if (on && !changingAutoplay) {
                        changingAutoplay = true;
                        try {
                            toggle.disabled = false;
                            toggle.click();
                        } finally {
                            changingAutoplay = false;
                        }
                    }
                    toggle.checked = false;
                    toggle.disabled = true;
                    if (toggle.hasAttribute('checked')) {
                        toggle.removeAttribute('checked');
                    }
                    if (toggle.getAttribute('aria-checked') !== 'false') {
                        toggle.setAttribute('aria-checked', 'false');
                    }
                    if (toggle.getAttribute('aria-pressed') === 'true') {
                        toggle.setAttribute('aria-pressed', 'false');
                    }
                });
            hide(section);
        });
    }

    function apply() {
        scheduled = false;
        capturePlaylist();
        document.querySelectorAll(HIDDEN).forEach(hide);
        document.querySelectorAll('ytmusic-nav-bar button,ytmusic-nav-bar yt-icon-button,ytmusic-nav-bar tp-yt-paper-icon-button')
            .forEach(function (button) {
                if (!button.closest(LOGO) && !button.querySelector(LOGO)) {
                    hide(button);
                    button.disabled = true;
                }
            });
        document.querySelectorAll('ytmusic-logo a,a.logo,a.ytmusic-logo,a[href="/"],a[href="https://music.youtube.com/"]')
            .forEach(function (anchor) {
                if (anchor.href !== LIBRARY) {
                    anchor.href = LIBRARY;
                }
            });
        document.querySelectorAll('#tabsContainer [role="tab"],#tabsContainer tp-yt-paper-tab,#tabsContainer .tab-header')
            .forEach(function (tab) {
                var value = data(tab).tabRenderer || data(tab);
                if (/^related$/i.test((tab.textContent || '').trim())
                        || value.title === 'Related' || tab.getAttribute('tab-id') === 'RELATED') {
                    hide(tab);
                }
            });
        disableAutoplay();
        document.querySelectorAll('video,audio').forEach(function (media) {
            media.autoplay = false;
        });
        enforcePlayback();
        if (!allowed(url(location.href))) {
            location.replace(LIBRARY);
        }
    }

    function schedule() {
        if (!scheduled) {
            scheduled = true;
            setTimeout(apply, 0);
        }
    }

    function cancel(event) {
        event.preventDefault();
        event.stopImmediatePropagation();
    }

    document.addEventListener('click', function (event) {
        var target = event.target instanceof Element ? event.target : event.target.parentElement;
        if (!target || changingAutoplay) {
            return;
        }
        if (target.closest(LOGO)) {
            cancel(event);
            location.assign(LIBRARY);
            return;
        }
        if (target.closest(HIDDEN)) {
            cancel(event);
            return;
        }
        capturePlaylist();
        var next = target.closest('.next-button');
        var previous = target.closest('.previous-button');
        if ((next || previous) && !canSkip(next ? 1 : -1)) {
            cancel(event);
            return;
        }
        var anchor = target.closest('a[href]');
        if (anchor) {
            var destination = url(anchor.href);
            if (home(destination)) {
                cancel(event);
                location.assign(LIBRARY);
            } else if (!allowed(destination)) {
                cancel(event);
            }
        }
        for (var node = target; node && node !== document.documentElement; node = node.parentElement) {
            var endpoint = data(node).navigationEndpoint || data(node).onTap;
            var watch = endpoint && endpoint.watchEndpoint;
            if (watch && (!member(watch.videoId)
                    || watch.playlistId && watch.playlistId !== selected.list)) {
                cancel(event);
                return;
            }
        }
    }, true);

    ['pushState', 'replaceState'].forEach(function (name) {
        var original = history[name];
        history[name] = function (state, title, destination) {
            if (destination != null && !allowed(url(destination))) {
                stopPlayback();
                original.call(this, null, '', LIBRARY);
                location.replace(LIBRARY);
                return;
            }
            var result = original.apply(this, arguments);
            enforcePlayback();
            schedule();
            return result;
        };
    });
    ['popstate', 'hashchange'].forEach(function (name) {
        window.addEventListener(name, function () { enforcePlayback(); schedule(); }, true);
    });
    ['yt-navigate-start', 'yt-navigate-finish', 'yt-page-data-updated', 'yt-player-video-changed']
        .forEach(function (name) {
            document.addEventListener(name, function () { enforcePlayback(); schedule(); }, true);
        });
    new MutationObserver(schedule).observe(document, {
        childList: true, subtree: true, attributes: true,
        attributeFilter: ['href', 'hidden', 'aria-hidden', 'checked', 'aria-checked', 'aria-pressed', 'selected']
    });
    schedule();
}());
