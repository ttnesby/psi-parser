package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.pensjon.regler.internal.domain.TTUtlandEOS
import no.nav.pensjon.regler.internal.domain.Trygdetid

fun lagTrygdetidEOS(innTrygdetid: Trygdetid) {
    if (innTrygdetid.ttUtlandEos == null) {
        innTrygdetid.ttUtlandEos = TTUtlandEOS()
    }
}
