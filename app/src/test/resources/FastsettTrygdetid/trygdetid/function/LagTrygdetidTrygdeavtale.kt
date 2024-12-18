package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.AvtaleLandEnum
import no.nav.pensjon.regler.internal.domain.TTUtlandTrygdeavtale
import no.nav.pensjon.regler.internal.domain.Trygdetid

fun lagTrygdetidTrygdeavtale(
    innTrygdetid: Trygdetid?,
    innAvtaleLand: AvtaleLandEnum?
): TTUtlandTrygdeavtale {
    if (innTrygdetid?.ttUtlandTrygdeavtaler != null && innTrygdetid.ttUtlandTrygdeavtaler.isNotEmpty()) {
        for (it in innTrygdetid.ttUtlandTrygdeavtaler) {
            if (it.avtaleland == innAvtaleLand) {
                return it
            }
        }
    }

    val retval = TTUtlandTrygdeavtale(
        avtaleland = innAvtaleLand
    )
    innTrygdetid?.ttUtlandTrygdeavtaler?.add(retval)
    return retval
}
