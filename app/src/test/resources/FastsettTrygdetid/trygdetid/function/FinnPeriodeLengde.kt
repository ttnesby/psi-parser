package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_formel
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_formel_slutt
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_formel_start
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidPeriodeLengde
import no.nav.preg.system.helper.dager
import no.nav.preg.system.helper.minus
import no.nav.preg.system.helper.plus
import no.nav.preg.system.helper.toLocalDate
import java.time.Period
import java.util.*

/**
 * Finner antall år, måneder og dager mellom to datoer.
 */
fun finnPeriodeLengde(fom: Date?, tom: Date?): TrygdetidPeriodeLengde {
    /**
     * Negativ periodelengde resulterer i 0 år og måneder
     */
    if (fom!!.toLocalDate() > tom!!.toLocalDate()) {
        return TrygdetidPeriodeLengde(fom = fom, tom = tom)
    }
    val period: Period = (tom + 1.dager) - fom  // pluss en dag fordi subtractInMonths ikke tar med siste dag

    val retval = TrygdetidPeriodeLengde()
    retval.fom = fom
    retval.tom = tom
    retval.år = period.years
    retval.måneder = period.months
    if (period.days >= 30) {
        retval.dager = 0
        if (retval.måneder == 11) {
            retval.år += 1
            retval.måneder = 0
        } else {
            retval.måneder += 1
        }
    } else {
        retval.dager = period.days
    }
    log_formel_start("Periode lengde")
    log_formel("periode = [$fom, $tom]")
    log_formel("periode = ${retval.år} år ${retval.måneder} mnd ${retval.dager} dager")
    log_formel_slutt()
    return retval
}
