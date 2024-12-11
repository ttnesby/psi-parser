package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType

/**
 * Styrer beregning av TT for overgangskull AP2016.
 */
fun settTTKapittel20(innParam: TrygdetidParameterType?, innKapittel20: Boolean?) {
    innParam?.variable?.kapittel20 = innKapittel20
}
