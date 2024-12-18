package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser

import java.util.*

/**
 * Hjelpeklasse for å legge sammen trygdetid perioder.
 */
class TrygdetidPeriodeLengde {
    var fom: Date? = null

    var tom: Date? = null

    var år: Int = 0

    var måneder: Int = 0

    var dager: Int = 0

    fun totaltAntallDager(): Int = (år * 12 * 30) + (måneder * 30) + dager

    constructor(
        fom: Date? = null,
        tom: Date? = null,
        år: Int = 0,
        måneder: Int = 0,
        dager: Int = 0
    ) {
        this.fom = fom
        this.tom = tom
        this.år = år
        this.måneder = måneder
        this.dager = dager

    }

    constructor()
}
