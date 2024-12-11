package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType

/**
 * Tilbakestill variable etter beregning av trygdetid siste UFT
 */
fun tilbakestillBeregnTrygdetidSisteUFT(innParametere: TrygdetidParameterType) {
    innParametere.variable?.ytelseType = innParametere.grunnlag?.ytelseType
    innParametere.variable?.sisteUFT = null
    innParametere.variable?.garantiType = null
}
