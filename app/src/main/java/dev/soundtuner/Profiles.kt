package dev.soundtuner

data class Profile(
    val id: String,
    val name: String,
    val description: String,
    val filters: List<Filter>,
)

object Profiles {
    private fun pk(f: Double, g: Double, q: Double) = Filter(FilterType.PEAK, f, g, q)
    private fun lowShelf(f: Double, g: Double, q: Double) = Filter(FilterType.LOW_SHELF, f, g, q)
    private fun highShelf(f: Double, g: Double, q: Double) = Filter(FilterType.HIGH_SHELF, f, g, q)

    /**
     * Apple EarPods: AutoEq (Super Review measurement) 10-band correction,
     * tamed for real ears - moderate bass, no harsh top end.
     * The -5 dB at 2 kHz removes the shouty peak that makes EarPods sound cheap.
     */
    val EARPODS = Profile(
        "earpods", "Apple EarPods",
        "Fixes the shouty 2 kHz peak, adds bass and air",
        listOf(
            pk(31.0, 5.0, 1.41), pk(62.0, 5.0, 1.41), pk(125.0, 2.0, 1.41),
            pk(250.0, 1.0, 1.41), pk(500.0, 1.0, 1.41), pk(1000.0, -1.0, 1.41),
            pk(2000.0, -5.0, 1.41), pk(4000.0, 2.0, 1.41), pk(8000.0, -1.0, 1.41),
            pk(16000.0, 4.0, 1.41),
        ),
    )

    /** Galaxy Buds Live: high-end in-ear signature (HypetheSonics measurement via AutoEq). */
    val BUDS_HIFI = Profile(
        "buds_hifi", "Galaxy Buds Live · Hi-Fi",
        "Deep sub-bass, clean mids, vocals up front, airy top",
        listOf(
            lowShelf(80.0, 6.0, 0.70), pk(300.0, -1.5, 1.20),
            pk(2372.0, -2.0, 5.77), pk(6600.0, -2.8, 5.76), pk(7654.0, 1.6, 6.00),
            pk(3200.0, 1.5, 1.40), pk(5258.0, 1.5, 2.94), highShelf(12000.0, 2.5, 0.70),
        ),
    )

    /** Galaxy Buds Live: Harman in-ear target with a moderate bass shelf. */
    val BUDS_PRO = Profile(
        "buds_pro", "Galaxy Buds Live · Pro",
        "Harman reference tuning, punchy bass",
        listOf(
            lowShelf(105.0, 4.5, 0.70), pk(393.0, -1.1, 3.31),
            pk(2372.0, -2.0, 5.77), pk(6600.0, -2.8, 5.76), pk(7654.0, 1.6, 6.00),
            pk(5258.0, 2.5, 2.94), highShelf(10000.0, 2.0, 0.70),
        ),
    )

    val FLAT = Profile("flat", "Flat", "No correction - just your sliders", emptyList())

    val ALL = listOf(EARPODS, BUDS_HIFI, BUDS_PRO, FLAT)

    fun byId(id: String?): Profile = ALL.firstOrNull { it.id == id } ?: EARPODS

    fun isBuds(id: String?) = id?.startsWith("buds_") == true
}
