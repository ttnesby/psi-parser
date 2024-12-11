package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.konsoliderNorskePerioder
import no.nav.pensjon.regler.internal.domain.TTPeriode
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset

/**
 * Overlapp mellom norske trygdetidsperioder tillates.
 * Dette regelsett fjerner norske perioder som overlapper hverandre og slår sammen norske perioder
 * som står inntil hverandre.
 * Dette skal sikre at ikke overlappende tidsrom i Norge skal regnes med mer enn en gang.
 * PENPORT-1084: PoengiUtÅr fra den første av to sammenslåtte perioder skal ikke arves til den siste
 * av de to.
 * En sammenslått periode er allerede forlenget til 31.12 dersom den skal, så poengIUtÅr settes til
 * false ved sammenslåing.
 */
class KonsoliderNorskePerioderRS(
    private val innTTPeriodeListe: MutableList<TTPeriode>
) : AbstractPensjonRuleset<MutableList<TTPeriode>>() {
    override fun create() {
        regel("KonsoliderFunksjonTest") {
            HVIS { true }
            SÅ {
                log_debug("[HIT] KonsoliderNorskePerioderRS.KonsoliderFunksjonTest")
                RETURNER(konsoliderNorskePerioder(innTTPeriodeListe, this))
            }
        }
    }
}
