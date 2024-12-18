package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.pensjon.regler.internal.domain.beregning2011.BeregningsvilkarPeriode
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.preg.system.helper.localDate
import no.nav.preg.system.helper.toLocalDate
import java.util.*

/**
 * Hjelperegel for å finne første og siste beregningsvilkårsperiode
 */
class BestemUføretidspunktUTRS(
    private val innParametere: TrygdetidParameterType?
) : AbstractPensjonRuleset<Unit>() {
    private var uftFørste: Date? = null
    private var uftSiste: Date? = null
    private var bvpFørste: BeregningsvilkarPeriode? = null
    private var bvpSiste: BeregningsvilkarPeriode? = null

    override fun create() {

        regel("FinnFørsteBeregningsvilkårPeriode") {
            HVIS { innParametere?.grunnlag != null }
            OG { innParametere?.grunnlag?.beregningsvilkarsPeriodeListe?.size!! > 0 }
            SÅ {
                log_debug("[HIT] BestemUføretidspunktUTRS.FinnFørsteBeregningsvilkårPeriode")
                bvpFørste = innParametere?.grunnlag?.beregningsvilkarsPeriodeListe!![0]
            }
            kommentar("Hjelperegel for å finne første og siste beregningsvilkårsperiode")
        }

        regel("FinnSisteBeregningsvilkårPeriode") {
            HVIS { innParametere?.grunnlag != null }
            OG { innParametere?.grunnlag?.beregningsvilkarsPeriodeListe?.size!! > 1 }
            SÅ {
                log_debug("[HIT] BestemUføretidspunktUTRS.FinnSisteBeregningsvilkårPeriode")
                bvpSiste =
                    innParametere?.grunnlag?.beregningsvilkarsPeriodeListe!!.last()
            }
            kommentar("Hjelperegel for å finne siste beregningsvilkårsperiode")
        }

        regel("FinnFørsteUføretidspunkt") {
            HVIS { bvpFørste != null }
            OG { bvpFørste?.uforetidspunkt != null }
            OG { bvpFørste?.uforetidspunkt?.uforetidspunkt != null }
            SÅ {
                uftFørste = bvpFørste?.uforetidspunkt?.uforetidspunkt
                log_debug("[HIT] BestemUføretidspunktUTRS.FinnFørsteUføretidspunkt, uftFørste = ${uftFørste.toString()}")
            }
            kommentar("Hjelperegel for å finne første uføretidspunkt fra beregningsvilkårperioder.")
        }

        regel("FinnSisteUføretidspunktFlerePerioder") {
            HVIS { bvpSiste != null }
            OG { bvpSiste?.uforetidspunkt != null }
            OG { bvpSiste?.uforetidspunkt?.uforetidspunkt != null }
            SÅ {
                uftSiste = bvpSiste?.uforetidspunkt?.uforetidspunkt
            }
            kommentar("Hjelperegel for å finne siste uføretidspunkt fra beregningsvilkårperioder.")
        }

        regel("KopierSistMedlTrygdenFlerePerioder") {
            HVIS { "FinnSisteUføretidspunktFlerePerioder".harTruffet() }
            OG { bvpSiste?.uforetidspunkt?.sistMedlTrygden != null }
            SÅ {
                log_debug("[HIT] BestemUføretidspunktUTRS.KopierSistMedlTrygdenFlerePerioder")
                innParametere?.grunnlag?.bruker?.sistMedlITrygden = bvpSiste?.uforetidspunkt?.sistMedlTrygden
                log_debug("[   ]    sistMedlTrygden = ${innParametere?.grunnlag?.bruker?.sistMedlITrygden.toString()}")
            }
            kommentar(
                """Hjelperegel for å kopiere sistMedlTrygden fra Beregningsvilkår til
            persongrunnlag hvis flere perioder."""
            )
        }

        regel("FinnSisteUføretidspunktEnPeriode") {
            HVIS { bvpSiste == null }
            OG { bvpFørste != null }
            OG { bvpFørste?.uforetidspunkt != null }
            OG { bvpFørste?.uforetidspunkt?.uforetidspunkt != null }
            SÅ {
                uftSiste = bvpFørste?.uforetidspunkt?.uforetidspunkt
            }
            kommentar(
                """Hjelperegel for å finne siste uføretidspunkt fra beregningsvilkårperioder.
        Denne regel treffer dersom det bare finnes en beregningsvilkårperiode i listen."""
            )
        }

        regel("KopierSistMedlTrygdenEnPeriode") {
            HVIS { "FinnSisteUføretidspunktEnPeriode".harTruffet() }
            OG { bvpFørste?.uforetidspunkt?.sistMedlTrygden != null }
            SÅ {
                log_debug("[HIT] BestemUføretidspunktUTRS.KopierSistMedlTrygdenEnPeriode")
                innParametere?.grunnlag?.bruker?.sistMedlITrygden = bvpFørste?.uforetidspunkt?.sistMedlTrygden
                log_debug("[   ]    sistMedlTrygden = ${innParametere?.grunnlag?.bruker?.sistMedlITrygden.toString()}")
            }
            kommentar(
                """Hjelperegel for å kopiere sistMedlTrygden fra Beregningsvilkår til
            persongrunnlag hvis en periode."""
            )
        }

        regel("UføretidspunktUT") {
            HVIS { uftSiste != null }
            SÅ {
                innParametere?.variable?.uftUT = uftSiste
                log_debug("[HIT] BestemUføretidspunktUTRS.UføretidspunktUT, uftUT = ${innParametere?.variable?.uftUT.toString()}")
            }
            kommentar(
                """Bestemmer uføretidspunktet til beregning av trygdetid for uføretrygd som ikke
            er konvertert."""
            )
        }

        regel("KonvertertTilUT") {
            HVIS { innParametere?.grunnlag?.bruker?.overgangsInfoUPtilUT != null }
            OG { uftFørste != null }
            OG { uftFørste!!.toLocalDate() == localDate(2015, 1, 1) }
            SÅ {
                log_debug("[HIT] InitTrygdetidVariableRS.KonvertertTilUT")
                innParametere?.variable?.konvertertTilUT = true
            }
            kommentar(
                """Hvis uføretidspunkt for første beregningsvilkårsperiode er 1.1.2015 og det
            finnes overgangsinfo UP til UT
        så settes variabel konvertertTilUT til true."""
            )
        }
    }
}