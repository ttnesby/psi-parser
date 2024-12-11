package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.AvtaleLandEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.LandkodeEnum
import no.nav.pensjon.regler.internal.domain.TTPeriode
import no.nav.preg.system.helper.*
import no.nav.system.rule.dsl.pattern.DoublePattern
import no.nav.system.rule.dsl.pattern.SinglePattern
import no.nav.system.rule.dsl.pattern.createPattern
import java.util.*

/**
 * Regelsettet håndterer overlapp mellom norske og utenlandske trygdetidsperioder.
 */
class BehandleOverlappendeNorUtlPerioderRS(
    private val innTTPeriodeListe: MutableList<TTPeriode>
) : AbstractPensjonRuleset<MutableList<TTPeriode>>() {
    private var utPerioderListe: MutableList<TTPeriode> = mutableListOf()
    private var nyDato: Date? = null
    private val noenNorskPeriode: SinglePattern<TTPeriode> = mutableListOf<TTPeriode>().createPattern()
    private val noenUtenlandskPeriode: SinglePattern<TTPeriode> = mutableListOf<TTPeriode>().createPattern()
    private var noenNorskeOgUtenlandskePeriode: DoublePattern<TTPeriode, TTPeriode> =
        noenNorskPeriode.combineWithPattern(noenUtenlandskPeriode) { _, _ -> true }

    override fun create() {
        regel("FinnNorskePerioder") {
            HVIS { noenNorskPeriode.isEmpty() }
            SÅ {
                for (it in innTTPeriodeListe) {
                    if (it.land == LandkodeEnum.NOR) {
                        noenNorskPeriode.add(TTPeriode(it))
                    }
                }
            }
            kommentar("""Initialiser periodeliste med norske perioder. Periodene kopieres fordi fom og tom kan endres i dette regelsett.""")

        }

        regel("FinnUtenlandskePerioder") {
            HVIS { noenUtenlandskPeriode.isEmpty() }
            SÅ {
                for (it in innTTPeriodeListe) {
                    if (it.land != LandkodeEnum.NOR) {
                        noenUtenlandskPeriode.add(TTPeriode(it))
                    }
                }
            }
            kommentar("""Initialiser periodeliste med utenlandske perioder. Periodene kopieres fordi fom og tom kan endres i dette regelsett.""")
        }

        regel("DelvisOverlappStarten",  noenNorskeOgUtenlandskePeriode) { (norsk, utenlandsk) ->
            HVIS { utenlandsk.tom != null }
            OG { norsk.fom!!.toLocalDate() > utenlandsk.fom!!.toLocalDate() }
            OG { norsk.fom!!.toLocalDate() <= utenlandsk.tom!!.toLocalDate() }
            SÅ {
                log_debug("[HIT] BehandleOverlappendeNorUtlPerioderRS.DelvisOverlappStarten")
                log_debug("[   ]    Norsk periode: fom:${norsk.fom} tom: ${norsk.tom}")
                log_debug("[   ]    Utenlandsk periode (${utenlandsk.land}): fom: ${utenlandsk.fom} tom: ${utenlandsk.tom}")
                nyDato = norsk.fom!! - 1.dager
                utenlandsk.tom = nyDato
                log_debug("[   ]    Setter ny tom-dato på utenlandsk periode til $nyDato")
            }
        }

        regel("DelvisOverlappSlutten",noenNorskeOgUtenlandskePeriode) { (norsk, utenlandsk) ->
            HVIS { norsk.fom!! < utenlandsk.fom }
            OG { norsk.tom != null }
            OG { utenlandsk.tom != null }
            OG { norsk.tom!!.toLocalDate() < utenlandsk.tom!!.toLocalDate() }
            OG { norsk.tom!!.toLocalDate() >= utenlandsk.fom!!.toLocalDate() }
            SÅ {
                log_debug("[HIT] BehandleOverlappendeNorUtlPerioderRS.DelvisOverlappSlutten")
                log_debug("[   ]    Norsk periode: fom:${norsk.fom} tom: ${norsk.tom}")
                log_debug("[   ]    Utenlandsk periode (${utenlandsk.land}): fom: ${utenlandsk.fom} tom: ${utenlandsk.tom}")
                nyDato = norsk.tom!! + 1.dager
                utenlandsk.fom = nyDato
                log_debug("[   ]    Setter ny fom-dato på utenlandsk periode til $nyDato")
            }
        }

        regel("DelvisOverlappSluttenÅpenPeriode",  noenNorskeOgUtenlandskePeriode) { (norsk, utenlandsk) ->
            HVIS { norsk.fom!!.toLocalDate() < utenlandsk.fom!!.toLocalDate() }
            OG { utenlandsk.tom == null }
            OG { norsk.tom != null }
            OG { norsk.tom!!.toLocalDate() >= utenlandsk.fom!!.toLocalDate() }
            SÅ {
                log_debug("[HIT] BehandleOverlappendeNorUtlPerioderRS.DelvisOverlappSluttenÅpenPeriode")
                log_debug("[   ]    Norsk periode: fom:${norsk.fom} tom: ${norsk.tom}")
                log_debug("[   ]    Utenlandsk periode (${utenlandsk.land}): fom: ${utenlandsk.fom} tom: ${utenlandsk.tom}")
                nyDato = norsk.tom!! + 1.dager
                utenlandsk.fom = nyDato
                log_debug("[   ]    Setter ny fom-dato på utenlandsk periode til $nyDato")
            }
        }

        regel("FulltOverlapp",  noenNorskeOgUtenlandskePeriode) { (norsk, utenlandsk) ->
            HVIS { norsk.tom != null }
            OG { (utenlandsk.tom != null) }
            OG { norsk.fom!!.toLocalDate() <= utenlandsk.fom!!.toLocalDate() }
            OG { norsk.tom!!.toLocalDate() >= utenlandsk.tom!!.toLocalDate() }
            SÅ {
                log_debug("[HIT] BehandleOverlappendeNorUtlPerioderRS.FulltOverlapp")
                log_debug("[   ]    Norsk periode: fom:${norsk.fom} tom: ${norsk.tom}")
                log_debug("[   ]    Slettet utenlandsk periode (${utenlandsk.land}): fom: ${utenlandsk.fom} tom: ${utenlandsk.tom}")

                noenUtenlandskPeriode.remove(utenlandsk)
            }
        }

        regel("FulltOverlappÅpenPeriode",  noenNorskeOgUtenlandskePeriode) { (norsk, utenlandsk) ->
            HVIS { norsk.tom == null }
            OG { utenlandsk.tom != null }
            OG { norsk.fom!!.toLocalDate() <= utenlandsk.fom!!.toLocalDate() }
            SÅ {
                log_debug("[HIT] BehandleOverlappendeNorUtlPerioderRS.FulltOverlappÅpenPeriode")
                log_debug("[   ]    Norsk periode: fom:${norsk.fom} tom: ${norsk.tom}")
                log_debug("[   ]    Slettet utenlandsk periode (${utenlandsk.land}): fom: ${utenlandsk.fom} tom: ${utenlandsk.tom}")
                noenUtenlandskPeriode.remove(utenlandsk)
            }
        }

        regel("ReturRegel") {
            HVIS { true }
            SÅ {
                for (it in noenUtenlandskPeriode) {
                    if (it.land != LandkodeEnum.NOR) {
                        noenNorskPeriode.add(TTPeriode(it))
                    }
                }
                log_debug("[HIT] BehandleOverlappendeNorUtlPerioderRS.ReturRegel")
                utPerioderListe = noenNorskPeriode.sortedOrEmptyList().toMutableList()
                for (it in utPerioderListe) {
                    log_debug("[   ]    Periode ${it.land} (${it.fom}, ${it.tom})")
                }
                RETURNER(utPerioderListe)
            }
            kommentar("Legg til perioder utenfor Norge og returner listen.")

        }
    }

}
