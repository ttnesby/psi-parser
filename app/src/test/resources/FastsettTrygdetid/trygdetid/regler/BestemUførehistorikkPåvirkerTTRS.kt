package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.alderÅrMnd
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.finnDatoFor67m
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.finnSisteUforeperiode
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.GrunnlagsrolleEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.ProRataBeregningTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.TrygdetidGarantitypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.regler.FastsettUforeperioderRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.finnTTResultat
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.pensjon.regler.internal.domain.Trygdetid
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.pensjon.regler.internal.domain.grunnlag.Uforeperiode
import no.nav.preg.system.helper.minus
import no.nav.preg.system.helper.måneder
import no.nav.preg.system.helper.år
import java.util.*

/**
 * Bestemme om folketrygdlovens §3-5 6. ledd eller §3-7 skal anvendes. Begge bestemmelser gir en
 * "garanti" om at trygdetiden
 * minst skal svare til trygdetiden ved siste uføretidspunkt.
 */
class BestemUførehistorikkPåvirkerTTRS(
    private val innParametere: TrygdetidParameterType,
    private val innBruker: Persongrunnlag?,
    private val innYtelsetype: KravlinjeTypeEnum?,
    private val innVirk: Date?
) : AbstractPensjonRuleset<Unit>() {
    private var sisteUføreperiode: Uforeperiode? = null

    private var trygdetid: Trygdetid = finnTTResultat(innParametere)

    private var datoFyller67år1mnd: Date? = finnDatoFor67m(innBruker?.fodselsdato)

    private var etterlattepensjon: Boolean = false

    private var uforePeriodeListe: List<Uforeperiode> = listOf()

    override fun create() {

        regel("FinnUføreperiodeListe") {
            HVIS { innBruker?.uforeHistorikk != null }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.FinnUføreperiodeListe")
                uforePeriodeListe = innBruker?.uforeHistorikk?.getSortedUforeperiodeListe(false)!!
            }
        }

        regel("GJPAvdød") {
            HVIS { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.AVDOD }
            OG { innYtelsetype == KravlinjeTypeEnum.GJP }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.GJPAvdød dodsdato = ${innBruker?.dodsdato.toString()}")
                etterlattepensjon = true
            }
            kommentar("Gjenlevendepensjon og avdøde var død før fylte 67 år og 1 mnd.")
        }

        regel("GJRAvdød") {
            HVIS { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.AVDOD }
            OG { innYtelsetype == KravlinjeTypeEnum.GJR }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.GJRAvdød dodsdato = ${innBruker?.dodsdato.toString()}")
                etterlattepensjon = true
            }
            kommentar("Gjenlevenderett og avdøde var død før fylte 67 år og 1 mnd.")
        }

        regel("UT_GJTAvdød") {
            HVIS { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.AVDOD }
            OG { innYtelsetype == KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.UT_GJTAvdød dodsdato = ${innBruker?.dodsdato.toString()}")
                etterlattepensjon = true
            }
            kommentar("Gjenlevenderett og avdøde var død før fylte 67 år og 1 mnd.")
        }

        regel("BPFar") {
            HVIS { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.FAR }
            OG { innBruker?.dodsdato != null }
            OG { innYtelsetype == KravlinjeTypeEnum.BP }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.BPFar dodsdato = ${innBruker?.dodsdato.toString()}")
                etterlattepensjon = true
            }
            kommentar("Barnepensjon og avdød far var død før fylte 67 år og 1 mnd.")
        }

        regel("BPMor") {
            HVIS { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.MOR }
            OG { innBruker?.dodsdato != null }
            OG { innYtelsetype == KravlinjeTypeEnum.BP }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.BPMor dodsdato = ${innBruker?.dodsdato.toString()}")
                etterlattepensjon = true
            }
            kommentar("Barnepensjon og avdød mor var død før fylte 67 år og 1 mnd.")
        }

        regel("FinnSisteUføreperiodeForAvdød") {
            HVIS { etterlattepensjon }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.FinnSisteUføreperiodeForAvdød")
                FastsettUforeperioderRS(
                    innBruker?.dodsdato!!, innBruker.uforeHistorikk, innBruker.uforegrunnlag,
                    innBruker.fodselsdato, innBruker.dodsdato
                ).run(this)
                sisteUføreperiode = finnSisteUforeperiode(uforePeriodeListe)
            }
            kommentar("Finner siste uføreperiode for avdød dersom død før 67 år og 1 mnd.")
        }

        regel("AvdødFør67OgUførVedDød") {
            HVIS { etterlattepensjon }
            OG { sisteUføreperiode != null }
            OG { alderÅrMnd(innBruker?.fodselsdato, innBruker?.dodsdato!!) < 6701 }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.AvdødFør67OgUførVedDød")
                innParametere.variable?.garantiType = TrygdetidGarantitypeEnum.FT_3_7
                innParametere.variable?.sisteUFT = sisteUføreperiode?.uft
                log_debug("[   ]    Siste uft = ${innParametere.variable?.sisteUFT.toString()}")
            }
            kommentar(
                """Ved beregning av etterlattepensjon (GJP,GJR,BP) hvor avdøde hadde uførepensjon
            ved sin død skal folketrygdlovens §3-7 anvendes.
        Trygdetiden som er fastsatt ved dødstidspunktet skal vurderes mot trygdetiden ved siste
            uføretidspunkt og den største velges."""
            )
        }

        regel("SøkerOver67") {
            HVIS { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { innYtelsetype == KravlinjeTypeEnum.AP }
            OG { alderÅrMnd(innBruker?.fodselsdato, innVirk!!) >= 6701 }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.SøkerOver67")
                FastsettUforeperioderRS(
                    innVirk!!, innBruker?.uforeHistorikk, innBruker?.uforegrunnlag,
                    innBruker?.fodselsdato, innBruker?.dodsdato
                ).run(this)

                sisteUføreperiode = finnSisteUforeperiode(uforePeriodeListe)
            }
            kommentar(
                """Finner siste uføreperiode for søker dersom alder ved virk er minst 67 år og 1
            mnd. 
        PENPORT1687: Uføretidspunkt kan være likt for to perioder. Bruker derfor
            finnSisteUforeperiode istedenfor."""
            )
        }

        regel("SøkerOver67OvergangAPfraUP") {
            HVIS { "SøkerOver67".harTruffet() }
            OG { sisteUføreperiode != null }
            OG { (sisteUføreperiode?.ufgTom == null || sisteUføreperiode?.ufgTom!! >= datoFyller67år1mnd!! - 2.måneder) }
            OG { sisteUføreperiode?.proRataBeregningType == null }
            OG { trygdetid.tt < 40 }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.SøkerOver67OvergangAPfraUP")
                innParametere.variable?.garantiType = TrygdetidGarantitypeEnum.FT_3_5
                innParametere.variable?.sisteUFT = sisteUføreperiode?.uft
                log_debug("[   ]    Siste uft = ${innParametere.variable?.sisteUFT.toString()}")
            }
            kommentar(
                """Ved beregning av AP hvor bruker er minst 67 år og 1 mnd og det er overgang fra
            uførepensjon skal folketrygdlovens §3-5 8. ledd anvendes.
        Trygdetiden som er fastsatt for alderspensjonen skal vurderes mot trygdetiden ved siste
            uføretidspunkt og den største velges.
        Denne vurdering av hvilken trygdetid som er størst er bare nødvendig dersom trygdetid for AP
            er mindre enn 40 år."""
            )
        }

        regel("SøkerOver67OvergangAPfraVinnendeProrataUP") {
            HVIS { "SøkerOver67".harTruffet() }
            OG { sisteUføreperiode != null }
            OG { (sisteUføreperiode?.ufgTom == null || sisteUføreperiode?.ufgTom!! >= datoFyller67år1mnd!! - 2.måneder) }
            OG { sisteUføreperiode?.proRataBeregningType != null }
            OG { sisteUføreperiode?.proRataBeregningType == ProRataBeregningTypeEnum.EOS_VANT }
            OG { trygdetid.tt < 40 }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.SøkerOver67OvergangAPfraVinnendeProrataUP")
                innParametere.variable?.garantiType = TrygdetidGarantitypeEnum.FT_3_5
                innParametere.variable?.sisteUFT = sisteUføreperiode?.uft
                innParametere.variable?.prorataBeregningType = ProRataBeregningTypeEnum.EOS_VANT
                log_debug("[   ]    Siste uft = ${innParametere.variable?.sisteUFT.toString()}")
            }
            kommentar(
                """Ved beregning av AP hvor bruker er minst 67 år og 1 mnd og det er overgang fra
            vinnende prorata uførepensjon skal folketrygdlovens §3-5 8. ledd anvendes.
        Trygdetiden som er fastsatt for alderspensjonen skal vurderes mot trygdetiden ved siste
            uføretidspunkt og den største velges.
        Denne vurdering av hvilken trygdetid som er størst er bare nødvendig dersom trygdetid for AP
            er mindre enn 40 år."""
            )
        }

        regel("SøkerOver67OvergangAPfraKunProrataUP") {
            HVIS { "SøkerOver67".harTruffet() }
            OG { sisteUføreperiode != null }
            OG { (sisteUføreperiode?.ufgTom == null || sisteUføreperiode?.ufgTom!! >= datoFyller67år1mnd!! - 2.måneder) }
            OG { sisteUføreperiode?.proRataBeregningType != null }
            OG { sisteUføreperiode?.proRataBeregningType == ProRataBeregningTypeEnum.KUN_EOS }
            OG { trygdetid.tt < 40 }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.SøkerOver67OvergangAPfraKunProrataUP")
                innParametere.variable?.garantiType = TrygdetidGarantitypeEnum.FT_3_5
                innParametere.variable?.sisteUFT = sisteUføreperiode?.uft
                innParametere.variable?.prorataBeregningType = ProRataBeregningTypeEnum.KUN_EOS
                log_debug("[   ]    Siste uft = ${innParametere.variable?.sisteUFT.toString()}")
            }
            kommentar(
                """Ved beregning av AP hvor bruker er minst 67 år og 1 mnd og det er overgang fra
            prorata uførepensjon skal folketrygdlovens §3-5 8. ledd anvendes.
        Trygdetiden som er fastsatt for alderspensjonen skal vurderes mot trygdetiden ved siste
            uføretidspunkt og den største velges.
        Denne vurdering av hvilken trygdetid som er størst er bare nødvendig dersom trygdetid for AP
            er mindre enn 40 år."""
            )
        }

        regel("AvdødOver67OvergangAPfraUP") {
            HVIS { etterlattepensjon }
            OG { alderÅrMnd(innBruker?.fodselsdato, innBruker?.dodsdato!!) >= 6701 }
            OG { sisteUføreperiode != null }
            OG { (sisteUføreperiode?.ufgTom == null || sisteUføreperiode?.ufgTom!! >= datoFyller67år1mnd!! - 2.måneder) }
            OG { sisteUføreperiode?.proRataBeregningType == null }
            OG { trygdetid.tt < 40 }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.AvdødOver67OvergangAPfraUP")
                innParametere.variable?.garantiType = TrygdetidGarantitypeEnum.FT_3_5
                innParametere.variable?.sisteUFT = sisteUføreperiode?.uft
                log_debug("[   ]    Siste uft = ${innParametere.variable?.sisteUFT.toString()}")
            }
        }

        regel("AvdødOver67OvergangAPfraVinnendeProrataUP") {
            HVIS { etterlattepensjon }
            OG { alderÅrMnd(innBruker?.fodselsdato, innBruker?.dodsdato!!) >= 6701 }
            OG { sisteUføreperiode != null }
            OG { (sisteUføreperiode?.ufgTom == null || sisteUføreperiode?.ufgTom!! >= datoFyller67år1mnd!! - 2.måneder) }
            OG { sisteUføreperiode?.proRataBeregningType != null }
            OG { sisteUføreperiode?.proRataBeregningType == ProRataBeregningTypeEnum.EOS_VANT }
            OG { trygdetid.tt < 40 }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.AvdødOver67OvergangAPfraVinnendeProrataUP")
                innParametere.variable?.garantiType = TrygdetidGarantitypeEnum.FT_3_5
                innParametere.variable?.sisteUFT = sisteUføreperiode?.uft
                innParametere.variable?.prorataBeregningType = ProRataBeregningTypeEnum.EOS_VANT
                log_debug("[   ]    Siste uft = ${innParametere.variable?.sisteUFT.toString()}")
            }
        }

        regel("AvdødOver67OvergangAPfraKunProrataUP") {
            HVIS { etterlattepensjon }
            OG { alderÅrMnd(innBruker?.fodselsdato, innBruker?.dodsdato!!) >= 6701 }
            OG { sisteUføreperiode != null }
            OG { (sisteUføreperiode?.ufgTom == null || sisteUføreperiode?.ufgTom!! >= datoFyller67år1mnd!! - 2.måneder) }
            OG { sisteUføreperiode?.proRataBeregningType != null }
            OG { sisteUføreperiode?.proRataBeregningType == ProRataBeregningTypeEnum.KUN_EOS }
            OG { trygdetid.tt < 40 }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.AvdødOver67OvergangAPfraKunProrataUP")
                innParametere.variable?.garantiType = TrygdetidGarantitypeEnum.FT_3_5
                innParametere.variable?.sisteUFT = sisteUføreperiode?.uft
                innParametere.variable?.prorataBeregningType = ProRataBeregningTypeEnum.KUN_EOS
                log_debug("[   ]    Siste uft = ${innParametere.variable?.sisteUFT.toString()}")
            }
        }

        regel("OvergangFraUP") {
            HVIS { sisteUføreperiode != null }
            OG { sisteUføreperiode?.virk?.år!! < 2015 }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.OvergangFraUP")
                innParametere.variable?.ytelseType = KravlinjeTypeEnum.UP
            }
            kommentar(
                """Hvis virkningsdato på siste uføreperiode er før 2015 så er det overgang til AP
            fra UP."""
            )
        }

        regel("OvergangFraUT") {
            HVIS { sisteUføreperiode != null }
            OG { sisteUføreperiode?.virk?.år!! >= 2015 }
            SÅ {
                log_debug("[HIT] BestemUførehistorikkPåvirkerTTRS.OvergangFraUT")
                innParametere.variable?.ytelseType = KravlinjeTypeEnum.UT
                innParametere.variable?.uftUT = sisteUføreperiode?.uft
            }
            kommentar(
                """Hvis virkningsdato på siste uføreperiode er 2015 eller senere så er det
            overgang til AP fra UT."""
            )
        }
    }
}