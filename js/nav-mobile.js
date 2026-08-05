/*
 * Navigation for the mobile-only <select> menus that duplicate the sidebar navigation.
 *
 * The options are generated at build time, but their value is still read back from the DOM
 * before being used as a navigation target, so the destination is validated first: assigning
 * an unchecked value to window.location would allow a "javascript:" URL to run in the context
 * of the page. Kept out of the markup (rather than in an inline onchange attribute) so that a
 * Content-Security-Policy without 'unsafe-inline' remains possible.
 */
(function () {
    'use strict';

    var ALLOWED_PROTOCOLS = ['http:', 'https:'];

    // Resolves a possibly relative URL against the current document, using an anchor element
    // so that the parsing is the browser's own and works on every supported browser.
    function resolve(value) {
        var anchor = document.createElement('a');
        anchor.href = value;
        return anchor;
    }

    function navigate(value) {
        if (!value) return;

        var target = resolve(value);
        for (var i = 0; i < ALLOWED_PROTOCOLS.length; i++) {
            if (target.protocol === ALLOWED_PROTOCOLS[i]) {
                window.location.assign(target.href);
                return;
            }
        }
        // anything else (javascript:, data:, ...) is not a navigable destination here
    }

    function onChange() {
        navigate(this.value);
    }

    function init() {
        var selects = document.querySelectorAll('.docs-nav-mobile select');
        for (var i = 0; i < selects.length; i++)
            selects[i].addEventListener('change', onChange);
    }

    if (document.readyState === 'loading')
        document.addEventListener('DOMContentLoaded', init);
    else
        init();
})();
