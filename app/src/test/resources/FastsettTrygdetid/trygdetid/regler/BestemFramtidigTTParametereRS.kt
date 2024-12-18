package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.*
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.GrunnlagsrolleEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.TrygdetidGarantitypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.regler.FastsettUttaksdatoForAFPRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.avrundTilMåneder
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.finnPeriodeLengde
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidPeriodeLengde
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidVariable
import no.nav.domain.pensjon.regler.repository.merknad.MerknadEnum
import no.nav.pensjon.regler.internal.domain.Trygdetid
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.preg.system.helper.*
import java.util.*
import kotlin.math.round

/**
 * PENPORT-2222: Hvis det er en uførepensjon skal sistInnmeldtITrygden hentes fra Persongrunnlaget,
 * som kan endres av saksbehandler i skjermbildet Uføregrunnlag.
 */
class BestemFramtidigTTParametereRS(
    private val innParametere: TrygdetidVariable,
    private val innBruker: Persongrunnlag?,
    private val innTrygdetid: Trygdetid?,
    private val innFørsteVirk: Date?
) : AbstractPensjonRuleset<Unit>() {
    private var fødselsdato: Date = innBruker?.fodselsdato!!
    private var uføretidspunkt: Date? = null
    private var sim: Date? = null

    override fun create() {

        regel("UPHentSimFraGrunnlag") {
            HVIS { innBruker?.vilkarsVedtak?.kravlinjeType == (KravlinjeTypeEnum.UP) || innBruker?.vilkarsVedtak?.kravlinjeType == (KravlinjeTypeEnum.UT) }
            OG { innBruker?.sistMedlITrygden != null }
            SÅ {
                sim = innBruker?.sistMedlITrygden
                log_debug("[HIT] BestemFramtidigTTParametereRS.UPHentSimFraGrunnlag: ${innBruker?.sistMedlITrygden}")
            }
            kommentar(
                """PENPORT-2222: Hvis det er en uførepensjon skal sistInnmeldtITrygden hentes fra
            Persongrunnlaget, som kan endres av saksbehandler i skjermbildet Uføregrunnlag."""
            )

        }

        regel("ikkeUPHentSimFraUforehistorikk") {
            HVIS { innBruker?.vilkarsVedtak?.kravlinjeType != KravlinjeTypeEnum.UP }
            OG { innBruker?.vilkarsVedtak?.kravlinjeType != KravlinjeTypeEnum.UT }
            OG { innBruker?.uforeHistorikk != null }
            OG { innBruker?.uforeHistorikk?.sistMedlITrygden != null }
            SÅ {
                sim = innBruker?.uforeHistorikk?.sistMedlITrygden
                log_debug("[HIT] BestemFramtidigTTParametereRS.ikkeUPHentSimFraUforehistorikk: ${innBruker?.uforeHistorikk?.sistMedlITrygden}")
            }
            kommentar(
                """PENPORT-2222: Hvis det er en annen type pensjon, vil det bare finnes en sist
            innmeldt i trygden-dato dersom uførepensjonen var beregnet med en slik dato.
        Informasjon om hvordan uførepensjonen ble beregnet skal være lagret på uførehistorikken."""
            )

        }

        regel("SekstenÅr") {
            HVIS { innParametere.datoFyller16 == null }
            SÅ {
                innParametere.datoFyller16 = fødselsdato + 16.år
            }
            kommentar("Finner dato vedkommende fylte 16 år.")

        }

        regel("FinnUFT") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UP }
            OG { innBruker?.uforegrunnlag != null }
            OG { innBruker?.uforegrunnlag?.uft != null }
            SÅ {
                uføretidspunkt = innBruker?.uforegrunnlag?.uft
            }
            kommentar("Hente uføretidspunkt fra uføregrunnlag")

        }

        regel("FinnUFT1") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UP }
            OG { innBruker?.uforegrunnlag == null }
            OG { innParametere.sisteUFT != null }
            SÅ {
                uføretidspunkt = innParametere.sisteUFT
            }
            kommentar("Hente uføretidspunkt fra uførehistorikk")

        }

        regel("FinnUFT2") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UT }
            OG { innParametere.uftUT != null }
            SÅ {
                uføretidspunkt = innParametere.uftUT
            }
            kommentar("Hente uføretidspunkt fra beregningsvilkår.")

        }

        regel("FTTtilUtgang66År") {
            HVIS { innParametere.ttFramtidigRegnesTil == null }
            OG { innFørsteVirk!!.toLocalDate() >= localDate(1991, 1, 1) }
            SÅ {
                val datoFyller66: Date = fødselsdato + 66.år
                innParametere.ttFramtidigRegnesTil = date(datoFyller66.år, 12, 31)
                log_debug("[HIT] BestemFramtidigTTParametereRS.FTTtilUtgang66År, FTT til ${innParametere.ttFramtidigRegnesTil}")
            }
            kommentar(
                """Etter 1991: Framtidig trygdetid regnes ut det året pensjonisten fyller 66
            år."""
            )

        }

        regel("FTTtil67År") {
            HVIS { innParametere.ttFramtidigRegnesTil == null }
            OG { innFørsteVirk!!.toLocalDate() < localDate(1991, 1, 1) }
            OG { innFørsteVirk!!.toLocalDate() >= localDate(1973, 1, 1) }
            OG {
                fødselsdato.år != 1903
                        || fødselsdato.år != 1904
                        || fødselsdato.år != 1905
            }
            SÅ {
                innParametere.ttFramtidigRegnesTil = fødselsdato + 67.år
                log_debug("[HIT] BestemFramtidigTTParametereRS.FTTtil67År, FTT til ${innParametere.ttFramtidigRegnesTil}")
            }
            kommentar(
                """Før 1991: Framtidig trygdetid regnes til den dagen pensjonisten fyller 67
            år."""
            )

        }

        regel("FTTtil67ÅrUnntak") {
            HVIS { innParametere.ttFramtidigRegnesTil == null }
            OG { innFørsteVirk!!.toLocalDate() < localDate(1991, 1, 1) }
            OG { innFørsteVirk!!.toLocalDate() >= localDate(1973, 1, 1) }
            OG {
                fødselsdato.år == 1903
                        || fødselsdato.år == 1904
                        || fødselsdato.år == 1905
            }
            SÅ {
                innParametere.ttFramtidigRegnesTil = date(1972, 12, 31)
                log_debug("[HIT] BestemFramtidigTTParametereRS.FTTtil67ÅrUnntak, FTT til ${innParametere.ttFramtidigRegnesTil}")
            }
            kommentar(
                """Før 1991: For personer født 1903, 1904 eller 1905 regnes trygdetid fram til
            31.12.1972."""
            )

        }

        regel("FTTtil70År") {
            HVIS { innParametere.ttFramtidigRegnesTil == null }
            OG { innFørsteVirk!!.toLocalDate() < localDate(1973, 1, 1) }
            SÅ {
                innParametere.ttFramtidigRegnesTil = fødselsdato + 70.år
                log_debug("[HIT] BestemFramtidigTTParametereRS.FTTtil70År, FTT til ${innParametere.ttFramtidigRegnesTil}")
            }
            kommentar(
                """Før 1973: Framtidig trygdetid regnes til den dagen pensjonisten fyller 70
            år."""
            )

        }

        regel("Uførepensjon") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UP }
            OG { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { uføretidspunkt != null }
            OG { sim == null }
            SÅ {
                innParametere.ttFramtidigRegnesFra = uføretidspunkt
                log_debug("[HIT] BestemFramtidigTTParametereRS.Uførepensjon, FTT fra ${innParametere.ttFramtidigRegnesFra}")
            }
            kommentar("For uførepensjonister regnes framtidig trygdetid fra uføretidspunktet")

        }

        regel("Uføretrygd") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UT }
            OG { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { uføretidspunkt != null }
            OG { sim == null }
            SÅ {
                innParametere.ttFramtidigRegnesFra = uføretidspunkt
                log_debug("[HIT] BestemFramtidigTTParametereRS.Uføretrygd, FTT fra ${innParametere.ttFramtidigRegnesFra}")
            }
            kommentar("For uføretrygdede regnes framtidig trygdetid fra uføretidspunktet")

        }

        regel("UførepensjonOgSimFørUft") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UP }
            OG { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { uføretidspunkt != null }
            OG { sim != null }
            OG { uføretidspunkt!! >= sim }
            SÅ {
                innParametere.ttFramtidigRegnesFra = uføretidspunkt
                log_debug("[HIT] BestemFramtidigTTParametereRS.UførepensjonOgSimFørUft, FTT fra ${innParametere.ttFramtidigRegnesFra}")
            }
            kommentar("For uførepensjonister regnes framtidig trygdetid fra uføretidspunktet")

        }

        regel("UføretrygdOgSimFørUft") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UT }
            OG { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { uføretidspunkt != null }
            OG { sim != null }
            OG { uføretidspunkt!! >= sim }
            SÅ {
                innParametere.ttFramtidigRegnesFra = uføretidspunkt
                log_debug("[HIT] BestemFramtidigTTParametereRS.UføretrygdOgSimFørUft, FTT fra ${innParametere.ttFramtidigRegnesFra}")
            }
            kommentar("For uføretrygdede regnes framtidig trygdetid fra uføretidspunktet")

        }

        regel("UførepensjonOgSimEtterUft") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UP }
            OG { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { uføretidspunkt != null }
            OG { sim != null }
            OG { uføretidspunkt!! < sim }
            SÅ {
                innParametere.ttFramtidigRegnesFra = sim
                innTrygdetid?.merknadListe?.add(MerknadEnum.BestemFramtidigTTParametereRS__UførepensjonOgSimEtterUft.lagMerknad())
                log_debug("[HIT] BestemFramtidigTTParametereRS.UførepensjonOgSimEtterUft, FTT fra ${innParametere.ttFramtidigRegnesFra}")
            }
            kommentar(
                """Når det gis uførepensjon etter unntaksbestemmelsene i § 12-2 tredje ledd,
            regnes framtidig trygdetid 
        tidligst fra det tidspunktet vedkommende sist ble medlem i trygden."""
            )

        }

        regel("UføretrygdOgSimEtterUft") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UT }
            OG { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { uføretidspunkt != null }
            OG { sim != null }
            OG { uføretidspunkt!! < sim }
            SÅ {
                innParametere.ttFramtidigRegnesFra = sim
                innTrygdetid?.merknadListe?.add(MerknadEnum.BestemFramtidigTTParametereRS__UførepensjonOgSimEtterUft.lagMerknad())
                log_debug("[HIT] BestemFramtidigTTParametereRS.UføretrygdOgSimEtterUft, FTT fra ${innParametere.ttFramtidigRegnesFra}")
            }
            kommentar(
                """Når det gis uføretrygd etter unntaksbestemmelsene i § 12-2 tredje ledd, regnes
            framtidig trygdetid 
        tidligst fra det tidspunktet vedkommende sist ble medlem i trygden."""
            )

        }

        regel("UførepensjonAvdød") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UP }
            OG { innParametere.garantiType == TrygdetidGarantitypeEnum.FT_3_7 }
            OG { uføretidspunkt != null }
            SÅ {
                innParametere.ttFramtidigRegnesFra = innParametere.ttFaktiskRegnesTil!! + 1.dager
                log_debug("[HIT] BestemFramtidigTTParametereRS.UførepensjonAvdød, FTT fra ${innParametere.ttFramtidigRegnesFra}")
            }
            kommentar(
                """Ref. § 3-7 1. ledd: Var den avdøde uførepensjonist eller alderspensjonist,
            benyttes den fastsatte trygdetiden."""
            )

        }

        regel("UføretrygdAvdød") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UT }
            OG { innParametere.garantiType == TrygdetidGarantitypeEnum.FT_3_7 }
            OG { uføretidspunkt != null }
            SÅ {
                innParametere.ttFramtidigRegnesFra = innParametere.ttFaktiskRegnesTil!! + 1.dager
                log_debug("[HIT] BestemFramtidigTTParametereRS.UføretrygdAvdød, FTT fra ${innParametere.ttFramtidigRegnesFra}")
            }
            kommentar(
                """Ref. § 3-7 1. ledd: Var den avdøde uførepensjonist eller alderspensjonist,
            benyttes den fastsatte trygdetiden."""
            )

        }

        regel("Gjenlevendepensjon") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.GJP }
            OG { innBruker?.dodsdato != null }
            SÅ {
                innParametere.ttFramtidigRegnesFra =
                    date(innBruker?.dodsdato?.år!!, innBruker.dodsdato?.måned!!.value, 1)
                log_debug("[HIT] BestemFramtidigTTParametereRS.Gjenlevendepensjon, FTT fra ${innParametere.ttFramtidigRegnesFra}")
            }
            kommentar(
                """For pensjon til gjenlevende regnes framtidig trygdetid fra måneden for
            dødsfallet."""
            )

        }

        regel("Gjenlevenderett") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.GJR }
            OG { innBruker?.dodsdato != null }
            SÅ {
                innParametere.ttFramtidigRegnesFra =
                    date(innBruker?.dodsdato?.år!!, innBruker.dodsdato?.måned!!.value, 1)
                log_debug("[HIT] BestemFramtidigTTParametereRS.Gjenlevenderett, FTT fra ${innParametere.ttFramtidigRegnesFra}")
            }
            kommentar(
                """For pensjon til gjenlevende regnes framtidig trygdetid fra måneden for
            dødsfallet."""
            )

        }

        regel("Gjenlevenderetillegg") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.UT_GJT }
            OG { innBruker?.dodsdato != null }
            SÅ {
                innParametere.ttFramtidigRegnesFra =
                    date(innBruker!!.dodsdato?.år!!, innBruker.dodsdato?.måned!!.value, 1)
                log_debug("[HIT] BestemFramtidigTTParametereRS.Gjenlevenderetillegg, FTT fra ${innParametere.ttFramtidigRegnesFra}")
            }
            kommentar(
                """For pensjon til gjenlevende regnes framtidig trygdetid fra måneden for
            dødsfallet."""
            )

        }

        regel("AFPpensjon") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.AFP }
            OG { (innBruker?.afpHistorikkListe == null || (innBruker.afpHistorikkListe.size == 0)) }
            SÅ {
                innParametere.ttFramtidigRegnesFra = innFørsteVirk
                log_debug("[HIT] BestemFramtidigTTParametereRS.AFPpensjon, FTT fra ${innParametere.ttFramtidigRegnesFra}")
            }
            kommentar("For AFP pensjon regnes framtidig trygdetid fra virkningstidspunktet.")

        }

        regel("AFPpensjonOgAFPHistorikk") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.AFP }
            OG { innBruker?.afpHistorikkListe?.size!! > 0 }
            SÅ {
                innParametere.ttFramtidigRegnesFra =
                    FastsettUttaksdatoForAFPRS(innBruker!!, innBruker.vilkarsVedtak!!).run(this)
                log_debug("[HIT] BestemFramtidigTTParametereRS.AFPpensjonOgAFPHistorikk, FTT fra ${innParametere.ttFramtidigRegnesFra}")
            }
            kommentar("For AFP pensjon regnes framtidig trygdetid fra virkningstidspunktet.")

        }

        regel("Barnepensjon") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.BP }
            OG { innBruker?.dodsdato != null }
            SÅ {
                innParametere.ttFramtidigRegnesFra =
                    date(innBruker?.dodsdato?.år!!, innBruker.dodsdato?.måned!!.value, 1)
                log_debug("[HIT] BestemFramtidigTTParametereRS.Barnepensjon, FTT fra ${innParametere.ttFramtidigRegnesFra}")
            }
            kommentar(
                """Framtidig trygdetid for avdød forelder ved barnepensjon regnes analogt med
            gjenlevendepensjon."""
            )

        }

        regel("Familiepleierpensjon") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.FP }
            SÅ {
                innParametere.ttFramtidigRegnesFra = innFørsteVirk
                log_debug("[HIT] BestemFramtidigTTParametereRS.Familiepleierpensjon, FTT fra ${innParametere.ttFramtidigRegnesFra}")
            }
            kommentar(
                """For familiepleierpensjon regnes framtidig trygdetid fra
            virkningstidspunktet."""
            )

        }

        regel("FomStørreEnnTom") {
            HVIS { innParametere.ttFramtidigRegnesFra!! > innParametere.ttFramtidigRegnesTil }
            SÅ {
                innParametere.ttFramtidigRegnesFra = innParametere.ttFramtidigRegnesTil
                log_debug("[HIT] BestemFramtidigTTParametereRS.FomStørreEnnTom")
            }
            kommentar(
                """Dato framtidig trygdetid regnes fra skal være mindre eller lik dato framtidig
            trygdetid regnes til."""
            )

        }

        regel("OpptjeningFra") {
            HVIS { innParametere.opptjeningRegnesFra == null }
            OG { innParametere.datoFyller16 != null }
            SÅ {
                val nestemåned: Date = innParametere.datoFyller16!! + 1.måneder
                innParametere.opptjeningRegnesFra = date(nestemåned.år, nestemåned.måned.value, 1)
            }
            kommentar("Opptjeningstid regnes fra og med måneden etter fylte 16 år.")

        }

        regel("OpptjeningTil") {
            HVIS { innParametere.opptjeningRegnesTil == null }
            OG { innParametere.ttFramtidigRegnesFra != null }
            OG { innParametere.ttFramtidigRegnesTil != null }
            OG { innParametere.ttFramtidigRegnesFra!! <= innParametere.ttFramtidigRegnesTil }
            SÅ {
                innParametere.opptjeningRegnesTil = sisteDagForrigeMåned(innParametere.ttFramtidigRegnesFra)
            }
            kommentar(
                """Opptjeningstid regnes til og med måneden før stønadstilfellet inntrådte. Men
            ikke lenger enn til dato for alderspensjon."""
            )

        }

        regel("OpptjeningTil1") {
            HVIS { innParametere.opptjeningRegnesTil == null }
            OG { innParametere.ttFramtidigRegnesFra != null }
            OG { innParametere.ttFramtidigRegnesTil != null }
            OG { innParametere.ttFramtidigRegnesFra!! > innParametere.ttFramtidigRegnesTil }
            SÅ {
                innParametere.opptjeningRegnesTil = sisteDagForrigeMåned(innParametere.ttFramtidigRegnesTil)
            }
            kommentar(
                """Opptjeningstid regnes til og med måneden før stønadstilfellet inntrådte. Men
            ikke lenger enn til dato for alderspensjon."""
            )

        }

        regel("Opptjeningsperiode") {
            HVIS { innParametere.opptjeningRegnesFra != null }
            OG { innParametere.opptjeningRegnesTil != null }
            OG { innParametere.opptjeningRegnesFra!! < innParametere.opptjeningRegnesTil }
            SÅ {
                val op: TrygdetidPeriodeLengde =
                    finnPeriodeLengde(innParametere.opptjeningRegnesFra, innParametere.opptjeningRegnesTil)
                innTrygdetid?.opptjeningsperiode = avrundTilMåneder(op, false)
                log_debug("[HIT] BestemFramtidigTTParametereRS.Opptjeningsperiode = fom ${innParametere.opptjeningRegnesFra.toString()} tom ${innParametere.opptjeningRegnesTil.toString()} = ${innTrygdetid?.opptjeningsperiode.toString()}")
            }
            kommentar(
                """Opptjeningstiden er tidsrommet fra og med måneden etter fylte 16 år til og med
            måneden før stønadstilfellet inntrådte."""
            )

        }

        regel("FireFemtedelsKrav") {
            HVIS { innParametere.firefemtedelskrav == null }
            SÅ {
                log_debug("[HIT] BestemFramtidigTTParametereRS.FireFemtedelsKrav")
                innParametere.firefemtedelskrav = round(innTrygdetid?.opptjeningsperiode!! * 4.dbl / 5).toInt()
                log_formel_start("Fire femtedelskrav")
                log_formel("firefemtedelskrav = avrund(opptjeningsperiode * 4/5)")
                log_formel("firefemtedelskrav = avrund(${innTrygdetid.opptjeningsperiode} * 4/5)")
                log_formel("firefemtedelskrav = ${innParametere.firefemtedelskrav}")
                log_formel_slutt()
            }
            kommentar(
                """Beregner firefemtedelskravet for faktisk trygdetid ut fra
            opptjeningsperioden."""
            )
        }
    }
}