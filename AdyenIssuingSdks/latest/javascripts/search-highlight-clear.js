/*
 * Clear MkDocs Material search-result highlights on first user interaction.
 *
 * Material's `search.highlight` feature wraps every search match on the
 * destination page in `<mark data-md-highlight>…</mark>` after navigation.
 * The theme deliberately leaves them in place — there is
 * no built-in dismiss interaction — so users have no obvious way to remove
 * the highlight short of reloading the page.
 *
 * This script restores the conventional "highlight is transient overlay,
 * dismissed on first interaction" behaviour familiar from browser find-in-
 * page and most documentation sites:
 *
 *   - First click anywhere on the page clears all highlights.
 *   - Pressing Escape clears all highlights (consistent with how Material's
 *     own search dropdown is dismissed).
 *
 * `navigation.instant` is enabled in `mkdocs.yml`, so each page transition
 * is a swap of the article body without a full reload. Material exposes a
 * `document$` Observable that emits the new document on every such swap;
 * we hook into it so the click + keydown listeners are re-attached and any
 * fresh highlights from the new navigation are eligible for dismissal.
 *
 * The helper unwraps each <mark> in place (replacing it with its text
 * children) rather than just toggling a class, so the cleared highlights
 * leave no DOM trace and cannot be re-styled or re-discovered by stray
 * selectors. `parent.normalize()` re-merges the resulting adjacent text
 * nodes so the article body returns to its pre-highlight DOM shape.
 */
(function () {
    function clearHighlights() {
        const marks = document.querySelectorAll("mark[data-md-highlight]");
        if (marks.length === 0) {
            return false;
        }
        marks.forEach(function (mark) {
            const parent = mark.parentNode;
            while (mark.firstChild) {
                parent.insertBefore(mark.firstChild, mark);
            }
            parent.removeChild(mark);
            parent.normalize();
        });
        return true;
    }

    function attachListeners() {
        // Use `once: true` semantics manually: clear on the first qualifying
        // interaction, then no-op for the remainder of the page's lifetime.
        // We deliberately do NOT use the native `{ once: true }` option,
        // because the listener may need to remain registered until a click
        // lands inside the article body (clicks on the header / sidebar /
        // search input itself shouldn't yet count).
        function onClick() {
            if (clearHighlights()) {
                document.removeEventListener("click", onClick, true);
                document.removeEventListener("keydown", onKey, true);
            }
        }

        function onKey(event) {
            if (event.key === "Escape") {
                if (clearHighlights()) {
                    document.removeEventListener("click", onClick, true);
                    document.removeEventListener("keydown", onKey, true);
                }
            }
        }

        document.addEventListener("click", onClick, true);
        document.addEventListener("keydown", onKey, true);
    }

    // Material exposes `document$` (a RxJS BehaviorSubject) that emits the
    // new document on every instant-navigation page swap. Subscribe so we
    // re-attach listeners against the freshly rendered article. Falls back
    // to a single attach on `DOMContentLoaded` if the theme JS is unavailable
    // (e.g. local preview without `navigation.instant`).
    if (typeof window.document$ !== "undefined" && window.document$.subscribe) {
        window.document$.subscribe(function () {
            attachListeners();
        });
    } else if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", attachListeners);
    } else {
        attachListeners();
    }
})();
