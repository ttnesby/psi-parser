package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.pensjon.regler.internal.domain.TTUtlandKonvensjon
import no.nav.pensjon.regler.internal.domain.Trygdetid

fun lagTrygdetidNordisk(innTrygdetid: Trygdetid) {
    if (innTrygdetid.ttUtlandKonvensjon == null) {
        innTrygdetid.ttUtlandKonvensjon = TTUtlandKonvensjon()
    }
}
