package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidGrunnlag
import no.nav.pensjon.regler.internal.domain.TTPeriode
import no.nav.preg.system.helper.sortedOrEmptyList

fun settTTperiodeListe(
    innTrygdetidGrunnlag: TrygdetidGrunnlag?,
    innKopiliste: MutableList<TTPeriode>
) {
    log_debug("[FUN] settTTperiodeListe")
    innTrygdetidGrunnlag?.trygdetidsperioder = innKopiliste.sortedOrEmptyList().toMutableList()
}
