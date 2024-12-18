package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.pensjon.regler.internal.domain.Trygdetid

/**
 * Finn hvilken trygdetid som skal beregnes: trygdetid eller trygdetidKapittel20.
 */
fun finnTTResultat(innParam: TrygdetidParameterType?): Trygdetid {
    return if (innParam?.variable?.kapittel20!!) {
        innParam.resultat?.trygdetidKapittel20!!
    } else {
        innParam.resultat?.trygdetid!!
    }
}
