package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.pensjon.regler.internal.domain.Trygdetid

/**
 * Sett trygdetid resultat: trygdetid eller trygdetidKapittel20.
 */
fun settTTResultat(
    innParam: TrygdetidParameterType,
    innTrygdetid: Trygdetid
): Trygdetid {
    if (innParam.variable?.kapittel20!!) {
        innParam.resultat?.trygdetidKapittel20 = innTrygdetid
    } else {
        innParam.resultat?.trygdetid = innTrygdetid
    }
    return innTrygdetid
}

