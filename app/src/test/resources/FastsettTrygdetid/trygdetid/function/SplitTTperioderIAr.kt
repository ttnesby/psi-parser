package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.LandkodeEnum
import no.nav.pensjon.regler.internal.domain.TTPeriode
import no.nav.preg.system.helper.date
import no.nav.preg.system.helper.år

fun splitTTperioderIAr(innTTperiodearray: List<TTPeriode> = listOf()): MutableList<TTPeriode> {
    val utTTperiodearray: MutableList<TTPeriode> = mutableListOf()
    var tempTTperiode: TTPeriode

    var arteller: Int
    var sluttar: Int

    log_debug("[FUN] splitTTperioderIAr")

    for (ttPeriode in innTTperiodearray) {
        log_debug("[   ] Periode: ${ttPeriode.fom}, tom: ${ttPeriode.tom}, land: ${ttPeriode.land}")
        //Kun norske periode splittes og legges i ut-listen
        if (ttPeriode.land != LandkodeEnum.NOR) {
            log_debug("[   ] Perioden er ikke norsk, og skal derfor ikke bidra med oppsplittede perioder")
        } else {
            // Skal ikke splittes
            if (ttPeriode.fom?.år == ttPeriode.tom?.år) {

                log_debug("[   ] Samme år fom: ${ttPeriode.fom}, tom: ${ttPeriode.tom}")
                utTTperiodearray.add(ttPeriode)
                // Skal splittes
            } else {
                arteller = ttPeriode.fom?.år!!
                sluttar = ttPeriode.tom?.år!!
                // Første året (start av hele perioden til 31.12 samme år)
                tempTTperiode = TTPeriode(ttPeriode)
                tempTTperiode.tom = date(arteller, 12, 31)
                utTTperiodearray.add(TTPeriode(tempTTperiode))
                log_debug("[   ] Første året fom: ${tempTTperiode.fom}, tom: ${tempTTperiode.tom}")
                arteller += 1
                // mellomliggende år
                while (arteller < sluttar) {
                    tempTTperiode = TTPeriode(ttPeriode)
                    tempTTperiode.fom = date(arteller, 1, 1)
                    tempTTperiode.tom = date(arteller, 12, 31)
                    utTTperiodearray.add(TTPeriode(tempTTperiode))
                    log_debug("[   ] Mellomliggende år fom: ${tempTTperiode.fom}, tom: ${tempTTperiode.tom}")
                    arteller += 1
                }
                // Siste året (1.1 sisteår til tom for hele perioden)
                tempTTperiode.fom = date(sluttar, 1, 1)
                tempTTperiode.tom = ttPeriode.tom
                utTTperiodearray.add(TTPeriode(tempTTperiode))
                log_debug("[   ] Siste året fom: ${tempTTperiode.fom}, tom: ${tempTTperiode.tom}")

            }
        }
    }
    return utTTperiodearray
}
