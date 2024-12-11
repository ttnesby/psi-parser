package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser

import no.nav.pensjon.regler.internal.domain.Pakkseddel
import no.nav.pensjon.regler.internal.domain.Trygdetid

class TrygdetidResultat {
    var trygdetid: Trygdetid? = null

    var trygdetidKapittel20: Trygdetid? = null

    var trygdetidAlternativ: Trygdetid? = null

    var pakkseddel: Pakkseddel? = null

    constructor(
        trygdetid: Trygdetid? = null,
        trygdetidKapittel20: Trygdetid? = null,
        trygdetidAlternativ: Trygdetid? = null,
        pakkseddel: Pakkseddel? = null
    ) {
        this.trygdetid = trygdetid
        this.trygdetidKapittel20 = trygdetidKapittel20
        this.trygdetidAlternativ = trygdetidAlternativ
        this.pakkseddel = pakkseddel

    }

    constructor()
}
