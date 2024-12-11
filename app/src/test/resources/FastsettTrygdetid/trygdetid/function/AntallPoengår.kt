package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.pensjon.regler.internal.domain.beregning.Poengtall

/**
 * Finn antall år med pensjonspoeng for angitt periode.
 */
fun antallPoengår(
    innPoengrekke: List<Poengtall>,
    innFomÅr: Int,
    innTomÅr: Int
): Int {
    return innPoengrekke.filter { it.ar in innFomÅr..innTomÅr }.count { it.pp > 0.0 }
}
