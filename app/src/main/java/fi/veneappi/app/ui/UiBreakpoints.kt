package fi.veneappi.app.ui

/**
 * Layout thresholds for phones, foldables, tablets, and free-form windows.
 * Width values are in **dp** (density-independent).
 */
object UiBreakpoints {
    /** Material “medium” width: show [NavigationRail] instead of bottom bar. */
    const val NAVIGATION_RAIL_MIN_WIDTH_DP = 600

    /** Map beside weather / route map beside route weather strip. */
    const val TWO_PANE_MIN_WIDTH_DP = 680

    /**
     * Minimum **allocated** width for the stacked dense three-source weather column.
     * Below this (e.g. narrow side column in split layout), use scroll + larger cards.
     */
    /** Dense three-stack weather column from this width (lower = more layouts stay “one screen”). */
    const val WEATHER_PANE_DENSE_MIN_WIDTH_DP = 420

    /** Cap readable width for extended wind table on very wide tablets. */
    const val EXTENDED_WIND_TABLE_MAX_WIDTH_DP = 960

    /** Cap paywall text column width on large screens. */
    const val PAYWALL_MAX_WIDTH_DP = 560
}
