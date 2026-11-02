package nl.streamfix.ui

/**
 * Welke rij krijgt de focus na een sprong van een schermvol in het
 * zenderoverzicht?
 *
 * Links en rechts springen daar met een hele pagina tegelijk, zodat je in
 * een lijst van honderden zenders niet eindeloos omlaag hoeft te tikken.
 *
 * Los van Compose, want de randgevallen zitten in het rekenen en niet in het
 * tekenen: springen voorbij het einde, springen voor het begin, een lijst die
 * korter is dan een pagina, en een lege lijst.
 */

/**
 * Nieuwe index na een sprong, of null als er niets te springen valt.
 *
 * Komt de sprong voorbij het einde, dan landt hij op de laatste zender in
 * plaats van nergens. Dat is wat een kijker verwacht: nog een keer drukken
 * brengt je naar de onderkant, niet in het niets. Hetzelfde aan het begin.
 *
 * Staat de focus al op de laatste zender en druk je nog eens rechts, dan
 * komt er null uit. Dan hoeft er niets te gebeuren, en belangrijker: dan
 * wordt de focus niet opnieuw aangevraagd voor een rij die hem al heeft.
 */
fun pickPageTarget(
    currentIndex: Int,
    itemCount: Int,
    step: Int,
    forward: Boolean,
): Int? {
    if (itemCount <= 0 || step <= 0) return null
    val vanaf = currentIndex.coerceIn(0, itemCount - 1)
    val doel = if (forward) vanaf + step else vanaf - step
    val begrensd = doel.coerceIn(0, itemCount - 1)
    return begrensd.takeIf { it != vanaf }
}
