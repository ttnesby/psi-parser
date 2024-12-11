package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.UforetypeEnum
import no.nav.pensjon.regler.internal.domain.grunnlag.Uforeperiode

/**
 * Finner om seneste uføreperiode var beregnet med redusert framtidig trygdetid/framtidig poengår.
 * Forutsetter at uføreperiodelisten er sortert i revers med seneste virk først.
 */
fun bestemFttRedusert(innUføreperiodeListe: List<Uforeperiode> = listOf()): Boolean? {
    log_debug("[FUN] bestemFttRedusert")

    for (up in innUføreperiodeListe) {
        if (up.uforeType == UforetypeEnum.UFORE
            || up.uforeType == UforetypeEnum.UF_M_YRKE
        ) {
            return up.redusertAntFppAr != 0
        }
    }
    return null
}
