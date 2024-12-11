package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_formel
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_formel_slutt
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_formel_start
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidPeriodeLengde

/**
 * Regn om år, måneder og dager til antall måneder. 30 dager regnes som 1 måned.
 * Resterende dager rundes eventuelt opp til en måned ekstra.
 */
fun avrundTilMåneder(innPeriode: TrygdetidPeriodeLengde?, innDagerRundesOpp: Boolean?): Int {
    log_debug("[FUN] avrundTilMåneder, innDagerRundesOpp = $innDagerRundesOpp")

    var retval: Int =
        innPeriode?.år!! * 12 + innPeriode.måneder + (innPeriode.dager / 30) // Deliberate Integer division

    if (innDagerRundesOpp!!) {
        if ((innPeriode.dager % 30) > 0) {
            retval += 1
        }
        log_formel_start("Avrund til måneder, rest dager rundes opp")
        log_formel("mnd = år * 12 + mnd + (dager div 30) + (dager mod 30)")
        log_formel("mnd = ${innPeriode.år} * 12 + ${innPeriode.måneder} + (${innPeriode.dager} div 30) + (${innPeriode.dager} mod 30)")
        log_formel("mnd = $retval")
        log_formel_slutt()
    } else {
        log_formel_start("Avrund til måneder")
        log_formel("mnd = år * 12 + mnd + (dager div 30)")
        log_formel("mnd = ${innPeriode.år} * 12 + ${innPeriode.måneder} + (${innPeriode.dager} div 30)")
        log_formel("mnd = $retval")
        log_formel_slutt()
    }
    return retval
}
