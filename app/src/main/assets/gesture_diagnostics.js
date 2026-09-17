(function () {
    'use strict';
    if (location.origin !== 'https://music.youtube.com' || window !== window.top
            || window.__ssmusicGestureDiagnosticsInstalled) {
        return;
    }
    window.__ssmusicGestureDiagnosticsInstalled = true;
    var sequence = 0;
    var pending = 0;
    var bubbled = new WeakSet();

    function enabled() {
        return window.__ssmusicGestureLoggingEnabled === true
            && location.origin === 'https://music.youtube.com';
    }

    // Only structural state: never page text, IDs/classes, URLs, or form values.
    function describe(node) {
        if (!(node instanceof Element)) {
            return 'none';
        }
        var style = getComputedStyle(node);
        var rect = node.getBoundingClientRect();
        return node.localName.slice(0, 64)
            + '{hidden=' + node.hasAttribute('hidden')
            + ',inert=' + node.hasAttribute('inert')
            + ',ariaHidden=' + (node.getAttribute('aria-hidden') === 'true')
            + ',disabled=' + (node.hasAttribute('disabled') || node.getAttribute('aria-disabled') === 'true')
            + ',display=' + style.display + ',visibility=' + style.visibility
            + ',pointerEvents=' + style.pointerEvents + ',opacity=' + style.opacity
            + ',rect=' + [rect.x, rect.y, rect.width, rect.height].map(Math.round).join(',') + '}';
    }

    function ancestors(node) {
        var result = [];
        for (var i = 0; node instanceof Element && i < 4; i++, node = node.parentElement) {
            result.push(describe(node));
        }
        return result.join(' > ');
    }

    function hitTest(x, y) {
        var nodes = document.elementsFromPoint ? document.elementsFromPoint(x, y)
            : [document.elementFromPoint(x, y)];
        return 'point=' + Math.round(x) + ',' + Math.round(y)
            + ' stack=' + nodes.slice(0, 4).map(describe).join(' > ')
            + ' ancestors=' + ancestors(nodes[0]);
    }

    window.__ssmusicGestureHitTest = function (fx, fy) {
        if (!enabled()) {
            return 'logging disabled';
        }
        if (!Number.isFinite(fx) || !Number.isFinite(fy) || fx < 0 || fx > 1 || fy < 0 || fy > 1) {
            return 'outside viewport';
        }
        var viewport = window.visualViewport;
        return hitTest((viewport ? viewport.offsetLeft : 0) + fx * (viewport ? viewport.width : innerWidth),
            (viewport ? viewport.offsetTop : 0) + fy * (viewport ? viewport.height : innerHeight));
    };

    function capture(event) {
        if (!enabled() || !event.isTrusted || pending >= 16) {
            return;
        }
        var bridge = window.ssmusicPlayback;
        if (!bridge || typeof bridge.logDiagnostic !== 'function') {
            return;
        }
        var id = ++sequence;
        var point = event.changedTouches ? event.changedTouches[0] : event;
        var details = 'Page gesture #' + id + ' ' + event.type
            + ' cancelable=' + event.cancelable + ' target=' + ancestors(event.target);
        if (point && Number.isFinite(point.clientX) && Number.isFinite(point.clientY)) {
            details += ' ' + hitTest(point.clientX, point.clientY);
        }
        pending++;
        // Inspect cancellation after dispatch; never cancel, consume, or synthesize an event.
        setTimeout(function () {
            pending--;
            if (enabled()) {
                bridge.logDiagnostic(details + ' defaultPrevented=' + event.defaultPrevented
                    + ' reachedWindowBubble=' + bubbled.has(event));
            }
        }, 0);
    }

    ['pointerdown', 'pointerup', 'pointercancel', 'touchstart', 'touchend', 'touchcancel', 'click']
        .forEach(function (type) {
            window.addEventListener(type, capture, {capture: true, passive: true});
            window.addEventListener(type, function (event) {
                if (enabled() && event.isTrusted) {
                    bubbled.add(event);
                }
            }, {passive: true});
        });
}());
