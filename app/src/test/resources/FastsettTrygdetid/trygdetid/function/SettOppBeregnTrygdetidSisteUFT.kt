package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidVariable
import no.nav.pensjon.regler.internal.domain.Trygdetid

fun settOppBeregnTrygdetidSisteUFT(
    innParametere: TrygdetidParameterType,
    innTrygdetid: Trygdetid?
) {
    log_debug("[FUN] settOppBeregnTrygdetidSisteUFT")

    // Ny trygdetid for siste uføretidspunkt skal beregnes
    val ttSisteUFT = Trygdetid(regelverkType = innTrygdetid?.regelverkType)
    settTTResultat(innParametere, ttSisteUFT)

    // Nye variable
    val variable = TrygdetidVariable(
        sisteUFT = innParametere.variable?.sisteUFT,
        uftUT = innParametere.variable?.uftUT,
        avtaletype = innParametere.variable?.avtaletype,
        regelverkType = innParametere.variable?.regelverkType,
        ttFaktiskBeregnes = true,
        kapittel20 = innParametere.variable?.kapittel20,
        garantiType = innParametere.variable?.garantiType,
        prorataBeregningType = innParametere.variable?.prorataBeregningType,
        ytelseType = innParametere.variable?.ytelseType
    )

    innParametere.variable = variable
}
