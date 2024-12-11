package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.sisteDagForrigeMåned
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.GrunnlagsrolleEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.RegelverkTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.TrygdetidGarantitypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.regler.FastsettUttaksdatoForAFPRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidVariable
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.preg.system.helper.format
import no.nav.preg.system.helper.min
import no.nav.preg.system.helper.toDate
import no.nav.preg.system.helper.toLocalDate
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.*

/**
 * Regelsett bestemmer parameter.ttFaktiskRegnesTil som er dato faktisk trygdetid regnes til.
 *
 * PK-8736: Utvidet med regler for uføretrygd.
 */
class BestemFaktiskTTRegnesTilRS(
    private val innParametere: TrygdetidVariable,
    private val innBruker: Persongrunnlag,
    private val innVirk: LocalDate,
    private val innFørsteVirk: LocalDate
) : AbstractPensjonRuleset<Unit>() {
    private var uføretidspunkt: Date? = null

    override fun create() {

        regel("FinnUFT") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UP }
            OG { innBruker.uforegrunnlag != null }
            OG { innBruker.uforegrunnlag?.uft != null }
            SÅ {
                uføretidspunkt = innBruker.uforegrunnlag?.uft
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.FinnUFT, uføretidspunkt = ${uføretidspunkt.toString()}")
            }
            kommentar("Hente uføretidspunkt fra uføregrunnlag")

        }

        regel("FinnUFT1") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UP }
            OG { innBruker.uforegrunnlag == null }
            OG { innParametere.sisteUFT != null }
            SÅ {
                uføretidspunkt = innParametere.sisteUFT
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.FinnUFT1, uføretidspunkt = ${uføretidspunkt.toString()}")
            }
            kommentar("Hente uføretidspunkt fra uførehistorikk")

        }

        regel("FinnUFT2") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UT }
            OG { innParametere.uftUT != null }
            SÅ {
                uføretidspunkt = innParametere.uftUT
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.FinnUFT2, uføretidspunkt = ${uføretidspunkt.toString()}")
            }
            kommentar("Hente uføretidspunkt fra beregningsvilkår.")

        }

        regel("UførVed16") {
            HVIS { uføretidspunkt != null }
            OG { (uføretidspunkt?.format("MMM-yyyy") == innParametere.ttFaktiskRegnesFra?.format("MMM-yyyy") || uføretidspunkt!! < innParametere.ttFaktiskRegnesFra) }
            SÅ {
            }
            kommentar("Uføretidspunkt er samme måned fyller 16 eller tidligere.")

        }

        regel("Uførepensjon_UFTetterMndFyller16") {
            HVIS { innParametere.ytelseType != null }
            OG { innParametere.ytelseType == KravlinjeTypeEnum.UP }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { uføretidspunkt != null }
            OG { "UførVed16".harIkkeTruffet() }
            SÅ {
                innParametere.ttFaktiskBeregnes = true
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(uføretidspunkt)
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.Uførepensjon_UFTetterMndFyller16, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For uførepensjonister regnes faktisk trygdetid fram til og med måned før
            uføretidspunktet.
        Regelen håndterer normaltilfellet hvor UFT inntreffer etter måned fylt 16 år."""
            )

        }

        regel("Uføretrygd_UFTetterMndFyller16") {
            HVIS { innParametere.ytelseType != null }
            OG { innParametere.ytelseType == KravlinjeTypeEnum.UT }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { uføretidspunkt != null }
            OG { "UførVed16".harIkkeTruffet() }
            SÅ {
                innParametere.ttFaktiskBeregnes = true
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(uføretidspunkt)
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.Uføretrygd_UFTetterMndFyller16, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For uføretrygdede regnes faktisk trygdetid fram til og med måned før
            uføretidspunktet.
        Regelen håndterer normaltilfellet hvor UFT inntreffer etter måned fylt 16 år."""
            )

        }

        regel("Uførepensjon_UFTiEllerFørMndFyller16") {
            HVIS { innParametere.ytelseType != null }
            OG { innParametere.ytelseType == KravlinjeTypeEnum.UP }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { uføretidspunkt != null }
            OG { "UførVed16".harTruffet() }
            SÅ {
                innParametere.ttFaktiskBeregnes = false
                innParametere.ttFaktiskRegnesTil = innParametere.ttFaktiskRegnesFra
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.Uførepensjon_UFTiEllerFørMndFyller16, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """Dersom UFT fastsettes måneden bruker fyller 16 år beregnes ingen faktisk
            trygdetid.
        I utgangspunktet gjelder dette uførepensjonister som er født ufør."""
            )

        }

        regel("Uføretrygd_UFTiEllerFørMndFyller16") {
            HVIS { innParametere.ytelseType != null }
            OG { innParametere.ytelseType == KravlinjeTypeEnum.UT }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { uføretidspunkt != null }
            OG { "UførVed16".harTruffet() }
            SÅ {
                innParametere.ttFaktiskBeregnes = false
                innParametere.ttFaktiskRegnesTil = innParametere.ttFaktiskRegnesFra
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.Uføretrygd_UFTiEllerFørMndFyller16, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """Dersom UFT fastsettes måneden bruker fyller 16 år beregnes ingen faktisk
            trygdetid.
        I utgangspunktet gjelder dette uføretrygdede som er født ufør."""
            )

        }

        regel("UførepensjonAvdød") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UP }
            OG { innParametere.garantiType == TrygdetidGarantitypeEnum.FT_3_7 || innParametere.garantiType == TrygdetidGarantitypeEnum.FT_3_5 }
            OG { uføretidspunkt != null }
            SÅ {
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(uføretidspunkt)
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.UførepensjonAvdød, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """Ref. § 3-7 1. ledd: Var den avdøde uførepensjonist eller alderspensjonist,
            benyttes den fastsatte trygdetiden."""
            )

        }

        regel("UføretrygdAvdød") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UT }
            OG { innParametere.garantiType == TrygdetidGarantitypeEnum.FT_3_7 || innParametere.garantiType == TrygdetidGarantitypeEnum.FT_3_5 }
            OG { uføretidspunkt != null }
            SÅ {
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(uføretidspunkt)
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.UføretrygdAvdød, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """Ref. § 3-7 1. ledd: Var den avdøde uførepensjonist eller alderspensjonist,
            benyttes den fastsatte trygdetiden."""
            )

        }

        regel("GjenlevendepensjonAvdød") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.GJP }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.AVDOD }
            OG { innBruker.dodsdato != null }
            OG { innBruker.dodsdato!!.toLocalDate() < innParametere.ttFaktiskMaksGrense!!.toLocalDate() }
            SÅ {
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(innBruker.dodsdato)
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.GjenlevendepensjonAvdød, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For GJP regnes faktisk trygdetid fram til og med måned før dødsdatoen,
            forutsatt dette er før overgang alderspensjon."""
            )

        }

        regel("GjenlevendepensjonSøker") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.GJP }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            SÅ {
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(innFørsteVirk).toDate()
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.GjenlevendepensjonSøker, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For gjenlevende regnes faktisk trygdetid frem til og med måned før
            pensjonstilfellet inntreffer."""
            )
        }

        regel("GjenlevenderettAvdød") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.GJR }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.AVDOD }
            OG { innBruker.dodsdato != null }
            OG { innBruker.dodsdato!!.toLocalDate() < innParametere.ttFaktiskMaksGrense!!.toLocalDate() }
            SÅ {
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(innBruker.dodsdato)
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.GjenlevenderettAvdød, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For GJR regnes faktisk trygdetid fram til og med måned før dødsdatoen,
            forutsatt dette er før overgang alderspensjon."""
            )
        }

        regel("GjenlevenderettAvdødEtterOvergangAP_gammelRegelverk") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.GJR }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.AVDOD }
            OG { innBruker.dodsdato != null }
            OG { innBruker.dodsdato!!.toLocalDate() >= innParametere.ttFaktiskMaksGrense?.toLocalDate() }
            OG { innParametere.regelverkType == RegelverkTypeEnum.G_REG }
            SÅ {
                innParametere.ttFaktiskRegnesTil = innParametere.ttFaktiskMaksGrense
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.GjenlevenderettAvdødEtterOvergangAP_gammelRegelverk, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For GJR regnes faktisk trygdetid fram til og med måned før dødsdatoen, etter overgang til alderspensjon gjelder"""
            )
        }

        regel("GjenlevenderettAvdødEtterOvergangAP_virkMinus2") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.GJR }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.AVDOD }
            OG { innBruker.dodsdato != null }
            OG { innBruker.dodsdato!!.toLocalDate() >= innParametere.ttFaktiskMaksGrense?.toLocalDate() }
            OG { innParametere.regelverkType != RegelverkTypeEnum.G_REG }
            SÅ {
                val sisteDagVirkMinus2 = innVirk.minusYears(2).with(TemporalAdjusters.lastDayOfYear()).toDate()
                innParametere.ttFaktiskRegnesTil = listOf<Date>(sisteDagVirkMinus2, innParametere.ttFaktiskMaksGrense!!).minOf { it }
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.GjenlevenderettAvdødEtterOvergangAP_virkMinus2, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For GJR regnes faktisk trygdetid fram til og med måned før dødsdatoen, forutsatt
            dette er før overgang alderspensjon."""
            )
        }

        regel("GjenlevendetilleggAvdød") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UT_GJT }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.AVDOD }
            OG { innBruker.dodsdato != null }
            OG { innBruker.dodsdato!!.toLocalDate() < innParametere.ttFaktiskMaksGrense!!.toLocalDate() }
            SÅ {
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(innBruker.dodsdato)
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.GjenlevendetilleggAvdød, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For GJR regnes faktisk trygdetid fram til og med måned før dødsdatoen,
            forutsatt dette er før overgang alderspensjon."""
            )

        }

        regel("GjenlevendetilleggAvdødEtterOvergangAP") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UT_GJT }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.AVDOD }
            OG { innBruker.dodsdato != null }
            OG { innBruker.dodsdato!!.toLocalDate() >= innParametere.ttFaktiskMaksGrense!!.toLocalDate() }
            SÅ {
                innParametere.ttFaktiskRegnesTil = innParametere.ttFaktiskMaksGrense
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.GjenlevendetilleggAvdødEtterOvergangAP, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For UT_GJT regnes faktisk trygdetid fram til og med måned før dødsdatoen,
            forutsatt dette er før overgang alderspensjon."""
            )

        }

        regel("GjenlevenderettSøker") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.GJR }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            SÅ {
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(innFørsteVirk).toDate()
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.GjenlevenderettSøker, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For gjenlevende regnes faktisk trygdetid frem til og med måned før
            pensjonstilfellet inntreffer."""
            )

        }

        regel("AFPpensjon") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.AFP }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            SÅ {
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(innFørsteVirk).toDate()
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.AFPpensjon, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For AFP regnes faktisk trygdetid frem til og med måned før pensjonstilfellet
            inntreffer."""
            )

        }

        regel("AFPpensjonOgAFPHistorikk") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.AFP }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { innBruker.afpHistorikkListe.size > 0 }
            SÅ {
                val uttaksdato: Date =
                    FastsettUttaksdatoForAFPRS(innBruker, innBruker.vilkarsVedtak!!).run(this)
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(uttaksdato)
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.AFPpensjonOgAFPHistorikk, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For AFP regnes faktisk trygdetid frem til og med måned før pensjonstilfellet
            inntreffer."""
            )

        }

        regel("Familiepleier") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.FP }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            SÅ {
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(innFørsteVirk).toDate()
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.Familiepleier, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """Trygdetid til pensjon/overgangsstønad til etterlatt familiepleier:
        Trygdetiden beregnes som for uføre ved at uføretidspunktet blir erstattet av tidspunktet for
            pleieforholdets opphør."""
            )

        }

        regel("BarnepensjonSøkerEldreEnn16") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.BP }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { innFørsteVirk > innParametere.datoFyller16!!.toLocalDate() }
            SÅ {
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(innFørsteVirk).toDate()
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.BarnepensjonSøkerEldreEnn16, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For BP regnes faktisk trygdetid frem til og med måned før pensjonstilfellet
            inntreffer."""
            )

        }

        regel("BarnepensjonSøkerYngreEnn16") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.BP }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { innFørsteVirk <= innParametere.datoFyller16?.toLocalDate() }
            SÅ {
                innParametere.ttFaktiskRegnesTil = innParametere.datoFyller16
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.BarnepensjonSøkerYngreEnn16, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For BP regnes faktisk trygdetid frem til og med måned før pensjonstilfellet
            inntreffer."""
            )

        }

        regel("BarnepensjonAvdødFar") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.BP }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.FAR }
            OG { innBruker.dodsdato != null }
            OG { innBruker.dodsdato!!.toLocalDate() < innParametere.ttFaktiskMaksGrense!!.toLocalDate() }
            SÅ {
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(innBruker.dodsdato)
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.BarnepensjonAvdødFar, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For BP og avdød far regnes faktisk trygdetid frem til og med måned før
            dødsdatoen, forutsatt dette er før overgang alderspensjon."""
            )

        }

        regel("BarnepensjonAvdødFarEtterOvergangAP") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.BP }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.FAR }
            OG { innBruker.dodsdato != null }
            OG { innBruker.dodsdato!!.toLocalDate() >= innParametere.ttFaktiskMaksGrense?.toLocalDate() }
            SÅ {
                innParametere.ttFaktiskRegnesTil = innParametere.ttFaktiskMaksGrense
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.BarnepensjonAvdødFarEtterOvergangAP, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For BP og avdød far regnes faktisk trygdetid frem til og med måned før
            dødsdatoen, forutsatt dette er før overgang alderspensjon."""
            )

        }

        regel("BarnepensjonFar") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.BP }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.FAR }
            OG { innBruker.dodsdato == null }
            SÅ {
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(innFørsteVirk).toDate()
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.BarnepensjonFar, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For BP og far regnes faktisk trygdetid frem til og med måned før
            pensjonstilfellet inntreffer."""
            )

        }

        regel("BarnepensjonAvdødMor") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.BP }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.MOR }
            OG { innBruker.dodsdato != null }
            OG { innBruker.dodsdato!!.toLocalDate() < innParametere.ttFaktiskMaksGrense!!.toLocalDate() }
            SÅ {
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(innBruker.dodsdato)
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.BarnepensjonAvdødMor, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For BP regnes faktisk trygdetid frem til og med måned før dødsdatoen,
            forutsatt dette er før overgang alderspensjon."""
            )

        }

        regel("BarnepensjonAvdødMorEtterOvergangAP") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.BP }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.MOR }
            OG { innBruker.dodsdato != null }
            OG { innBruker.dodsdato!!.toLocalDate() >= innParametere.ttFaktiskMaksGrense!!.toLocalDate() }
            SÅ {
                innParametere.ttFaktiskRegnesTil = innParametere.ttFaktiskMaksGrense
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.BarnepensjonAvdødMorEtterOvergangAP, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For BP regnes faktisk trygdetid frem til og med måned før dødsdatoen,
            forutsatt dette er før overgang alderspensjon."""
            )

        }

        regel("BarnepensjonMor") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.BP }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.MOR }
            OG { innBruker.dodsdato == null }
            SÅ {
                innParametere.ttFaktiskRegnesTil = sisteDagForrigeMåned(innFørsteVirk).toDate()
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.BarnepensjonMor, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For BP regnes faktisk trygdetid frem til og med måned før pensjonstilfellet
            inntreffer."""
            )
        }

        regel("AndreYtelser") {
            HVIS { innParametere.ytelseType != KravlinjeTypeEnum.AFP }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.AP }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.BP }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.FP }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.GJP }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.UP }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.UT }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.UT_GJT }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.GJR }
            SÅ {
                innParametere.ttFaktiskRegnesTil = innParametere.ttFaktiskMaksGrense
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.AndreYtelser, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar("Trygdetid hvis annet enn hovedytelse regnes tilsvarende som alderspensjon.")
        }

        regel("GjenlevendepensjonAvdødEtterOvergangAP") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.GJP }
            OG { innBruker.grunnlagsrolle == GrunnlagsrolleEnum.AVDOD }
            OG { innBruker.dodsdato != null }
            OG { innBruker.dodsdato!!.toLocalDate() >= innParametere.ttFaktiskMaksGrense!!.toLocalDate() }
            SÅ {
                innParametere.ttFaktiskRegnesTil = innParametere.ttFaktiskMaksGrense
                log_debug("[HIT] BestemFaktiskTTRegnesTilRS.GjenlevendepensjonAvdødEtterOvergangAP, TT til ${innParametere.ttFaktiskRegnesTil}")
            }
            kommentar(
                """For GJP regnes faktisk trygdetid fram til og med måned før dødsdatoen,
            forutsatt dette er før overgang alderspensjon."""
            )

        }
    }
}