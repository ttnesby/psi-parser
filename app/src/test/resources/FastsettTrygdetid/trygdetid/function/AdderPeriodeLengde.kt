package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidPeriodeLengde

/**
 * Adder innPeriode til innSumPerioder
 */
fun adderPeriodeLengde(
    innSumPerioder: TrygdetidPeriodeLengde,
    innPeriode: TrygdetidPeriodeLengde
) {
    innSumPerioder.år += innPeriode.år
    innSumPerioder.måneder += innPeriode.måneder
    innSumPerioder.dager += innPeriode.dager

    if (innSumPerioder.dager >= 30) {
        innSumPerioder.måneder += innSumPerioder.dager / 30 // Deliberate Integer division
        innSumPerioder.dager = innSumPerioder.dager % 30
    }
    if (innSumPerioder.måneder >= 12) {
        innSumPerioder.år += innSumPerioder.måneder / 12 // Deliberate Integer division
        innSumPerioder.måneder = innSumPerioder.måneder % 12
    }
}