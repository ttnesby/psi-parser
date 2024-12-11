package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.UforetypeEnum
import no.nav.pensjon.regler.internal.domain.grunnlag.Uforeperiode
import no.nav.preg.system.helper.date
import java.util.*

/**
 * Finner den uføreperiode med den seneste virkningsdato før 1-jan-2015 og returnerer
 * uføretidspunktet til denne periode som konverteringsuføretidspunktet.
 */
fun finnKonverteringUføretidspunkt(innUføreperiodeListe: MutableList<Uforeperiode> = mutableListOf()): Date? {
    log_debug("[FUN] finnKonverteringUføretidspunkt")

    var senestePeriode: Uforeperiode? = null

    for (it in innUføreperiodeListe) {
        if (it.virk!! < date(2015, 1, 1)
            && it.uforeType in listOf(UforetypeEnum.UFORE, UforetypeEnum.UF_M_YRKE)
        ) {
            if (senestePeriode == null) {
                senestePeriode = it
            } else if (it.virk!! > senestePeriode.virk) {
                senestePeriode = it
            }
        }
    }

    return if (senestePeriode != null) {
        log_debug("[HIT] KonvUFT = ${senestePeriode.uft.toString()}")
        senestePeriode.uft
    } else {
        null
    }
}
