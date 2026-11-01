package org.tablet.tuner;

/** Pure layout decisions (no Android types) so they can be unit tested on a JVM. All sizes are dp. */
final class Layouts {
    static final int WIDE_MIN_W = 840;       // narrower than this: the single column of a phone
    static final int PIN_MIN_H = 720;        // shorter than this: the left pane scrolls as one piece
    static final int PANE_PAD = 24;          // padding on each side of the right pane
    // ponytail: portrait on a 1000dp wide screen stays single column on purpose; set true to try two panes there too.
    static final boolean WIDE_IN_PORTRAIT = false;

    private Layouts() { }

    /** Two panes: wide enough, and landscape unless WIDE_IN_PORTRAIT. */
    static boolean wide(int wDp, int hDp) { return wDp >= WIDE_MIN_W && (WIDE_IN_PORTRAIT || wDp > hDp); }

    /** Left pane: about a quarter of the width, between 360 and 420. */
    static int leftWidth(int wDp) { return Math.max(360, Math.min(420, wDp * 27 / 100)); }

    /** Usable width of the content in the right pane (left pane, 1 dp divider and the side padding taken off). */
    static int contentWidth(int wDp) { return wDp - leftWidth(wDp) - 1 - 2 * PANE_PAD; }

    /** Columns of setting cards: two once each card is at least about 340 dp wide. */
    static int settingColumns(int contentDp) { return contentDp >= 720 ? 2 : 1; }

    /** Columns of app rows: three from 1000 dp, two from 640 dp. */
    static int appColumns(int contentDp) { return contentDp >= 1000 ? 3 : contentDp >= 640 ? 2 : 1; }

    /** Dial, preset name and live card stay put above the section list only when the screen is tall enough. */
    static boolean pinHeader(int hDp) { return hDp >= PIN_MIN_H; }
}
