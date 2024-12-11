package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.alderÅrMnd
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.AFPtypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.FppGarantiKodeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.GrunnlagsrolleEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidVariable
import no.nav.pensjon.regler.internal.domain.grunnlag.AfpHistorikk
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.pensjon.regler.internal.domain.grunnlag.Uforeperiode
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.preg.system.helper.*
import java.util.*

class BestemFaktiskTTRegnesTilAP2011RS(
    private val innParametere: TrygdetidVariable?,
    private val innBruker: Persongrunnlag?,
    private val innVirk: Date?,
    private val innFørsteVirk: Date?
) : AbstractPensjonRuleset<Unit>() {
    private var utgangÅrFyller66: Date = date(innBruker?.fodselsdato!!.år + 66, 12, 31)
    private var utgangÅrFyller67: Date = date(innBruker?.fodselsdato!!.år + 67, 12, 31)
    private var utgangÅrFyller70: Date = date(innBruker?.fodselsdato!!.år + 70, 12, 31)
    private var alderVedVirk: Int = alderÅrMnd(innBruker?.fodselsdato, innVirk!!)
    private var datoFyller67år1mnd: Date = innBruker?.fodselsdato!! + 67.år + 1.måneder
    private var virkMinus2: Date = date(innVirk?.år!! - 2, 12, 31)
    private var virkMinus3: Date = date(innBruker!!.sisteGyldigeOpptjeningsAr, 12, 31)
    private var uforeperioder: List<Uforeperiode> = listOf()
    private var afpHistorikk: List<AfpHistorikk> = listOf()
    private var avvikHovedregel: Boolean = false

    override fun create() {

        regel("FinnUforeperioder") {
            HVIS { innBruker?.uforeHistorikk != null }
            OG { innBruker?.uforeHistorikk?.uforeperiodeListe!!.isNotEmpty() }
            SÅ {
                uforeperioder = innBruker?.uforeHistorikk?.uforeperiodeListe!!
            }
            kommentar("Finn uføreperioder")
        }

        regel("FinnAfpHistorikk") {
            HVIS { innBruker != null }
            HVIS { innBruker?.afpHistorikkListe!!.isNotEmpty() }
            SÅ {
                afpHistorikk = innBruker?.afpHistorikkListe!!
            }
            kommentar("Finn AFP historikk")
        }

        regel("APfraAFP") {
            HVIS { alderVedVirk >= 6701 }
            OG {
                afpHistorikk.minst(1) {
                    (
                            it.virkTom == null || it.virkTom!!.toLocalDate() >= (datoFyller67år1mnd - 2.måneder).toLocalDate())
                            && (it.afpFpp > 0.00)
                            && it.afpOrdning == AFPtypeEnum.LONHO
                            || it.afpOrdning == AFPtypeEnum.NAVO
                            || it.afpOrdning == AFPtypeEnum.FINANS
                            || it.afpOrdning == AFPtypeEnum.KONV_K
                }
            }
            SÅ {
                log_debug("[HIT] BestemFaktiskTTRegnesTilAP2011RS.APfraAFP")
                avvikHovedregel = true
            }
            kommentar(
                """Ref. CR 188535. Hvis det er overgang til AP fra AFP og bruker er minst 67 år
            og 1 mnd så avvikes hovedregelen (virk-2/-3)."""
            )

        }

        regel("APfraUP") {
            HVIS { alderVedVirk >= 6701 }
            OG {
                uforeperioder.minst(1) {
                    (
                            it.ufgTom == null || it.ufgTom!!.toLocalDate() >= (datoFyller67år1mnd - 2.måneder).toLocalDate())
                            && (it.fpp > 0.00
                            || (it.fppGarantiKode != null && it.fppGarantiKode != FppGarantiKodeEnum.UNG_UF_FOR_67))
                            && (it.redusertAntFppAr == 0
                            || (it.redusertAntFppAr > 0
                            && it.uft != null
                            && (it.uft)?.år!! + it.redusertAntFppAr - 1 >= (innBruker?.fodselsdato)?.år!! + 66))
                }
            }
            SÅ {
                log_debug("[HIT] BestemFaktiskTTRegnesTilAP2011RS.APfraUP")
                avvikHovedregel = true
            }
            kommentar(
                """Ref. CR 188535. Hvis det er overgang til AP fra UP og bruker er minst 67 år og
            1 mnd så avvikes hovedregelen (virk-2/-3)."""
            )
        }

        regel("OpptjeningVirkMinus2FinnesIkke") {
            HVIS { innBruker?.sisteGyldigeOpptjeningsAr!! < innVirk?.år!! - 2 }
            SÅ {
                log_debug("[HIT] BestemFaktiskTTRegnesTilAP2011RS.OpptjeningVirkMinus2FinnesIkke ${innBruker?.sisteGyldigeOpptjeningsAr}")
            }
            kommentar("Det finnes ikke opptjening i året virk-2")
        }

        regel("OpptjeningVirkMinus2Finnes") {
            HVIS { innBruker?.sisteGyldigeOpptjeningsAr!! >= innVirk?.år!! - 2 }
            SÅ {
                log_debug("[HIT] BestemFaktiskTTRegnesTilAP2011RS.OpptjeningVirkMinus2Finnes")
            }
            kommentar("Det finnes opptjening i året virk-2")
        }

        regel("VirkMinus2FørUtgang66") {
            HVIS { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { !avvikHovedregel }
            OG { "OpptjeningVirkMinus2Finnes".harTruffet() }
            OG { virkMinus2.toLocalDate() < utgangÅrFyller66.toLocalDate() }
            SÅ {
                innParametere?.ttFaktiskRegnesTil = virkMinus2
                log_debug("[HIT] BestemFaktiskTTRegnesTilAP2011RS.AP2011VirkMinus2FørUtgang66, TT til ${innParametere?.ttFaktiskRegnesTil}")
            }
            kommentar(
                """Hvis opptjening virk-2 og virk-2 før utgang år fyller 66
        så regnes faktisk trygdetid til virk-2."""
            )

        }

        regel("VirkMinus2EtterUtgang66") {
            HVIS { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { !avvikHovedregel }
            OG { "OpptjeningVirkMinus2Finnes".harTruffet() }
            OG { virkMinus2.toLocalDate() >= utgangÅrFyller66.toLocalDate() }
            SÅ {
                innParametere?.ttFaktiskRegnesTil = utgangÅrFyller66
                log_debug("[HIT] BestemFaktiskTTRegnesTilAP2011RS.AP2011VirkMinus2EtterUtgang66, TT til ${innParametere?.ttFaktiskRegnesTil}")
            }
            kommentar(
                """Hvis opptjening virk-2 og virk-2 etter utgang år fyller 66
        så regnes faktisk trygdetid til utgang år fyller 66."""
            )

        }

        regel("VirkMinus3FørUtgang66") {
            HVIS { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { !avvikHovedregel }
            OG { "OpptjeningVirkMinus2FinnesIkke".harTruffet() }
            OG { virkMinus3.toLocalDate() < utgangÅrFyller66.toLocalDate() }
            SÅ {
                innParametere?.ttFaktiskRegnesTil = virkMinus3
                log_debug("[HIT] BestemFaktiskTTRegnesTilAP2011RS.AP2011VirkMinus3FørUtgang66, TT til ${innParametere?.ttFaktiskRegnesTil}")
            }
            kommentar(
                """Hvis ingen opptjening virk-2 og virk-3 før utgang år fyller 66 
        så regnes faktisk trygdetid til virk-3."""
            )

        }

        regel("VirkMinus3EtterUtgang66") {
            HVIS { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { !avvikHovedregel }
            OG { "OpptjeningVirkMinus2FinnesIkke".harTruffet() }
            OG { virkMinus3.toLocalDate() >= utgangÅrFyller66.toLocalDate() }
            SÅ {
                innParametere?.ttFaktiskRegnesTil = utgangÅrFyller66
                log_debug("[HIT] BestemFaktiskTTRegnesTilAP2011RS.AP2011VirkMinus3EtterUtgang66, TT til ${innParametere?.ttFaktiskRegnesTil}")
            }
            kommentar(
                """Hvis ingen opptjening virk-2 og virk-3 etter utgang år fyller 66
        så regnes faktisk trygdetid til utgang år fyller 66."""
            )
        }

        regel("AvvikHovedregelFørstevirkEtter1991") {
            HVIS { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { avvikHovedregel }
            OG { innFørsteVirk!!.toLocalDate() >= localDate(1991, 1, 1) }
            SÅ {
                innParametere?.ttFaktiskRegnesTil = utgangÅrFyller66
                log_debug("[HIT] BestemFaktiskTTRegnesTilAP2011RS.AP2011FørstevirkEtter1991OvergangFraUP, TT til ${innParametere?.ttFaktiskRegnesTil}")
            }
            kommentar(
                """Hvis fylte 67 år og overgang fra UP eller AFP så unntak fra virk -2/-3
            regelen. 
        Førstevirk etter 1991 og faktisk trygdetid regnes til utgang år fyller 66.
        Ref. CR 188535."""
            )
        }

        regel("AvvikHovedregelFørstevirkFør1991") {
            HVIS { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { avvikHovedregel }
            OG { innFørsteVirk!!.toLocalDate() < localDate(1991, 1, 1) }
            OG { innFørsteVirk!!.toLocalDate() >= localDate(1973, 1, 1) }
            SÅ {
                innParametere?.ttFaktiskRegnesTil = utgangÅrFyller67
                log_debug("[HIT] BestemFaktiskTTRegnesTilAP2011RS.AP2011FørstevirkFør1991OvergangFraUP, TT til ${innParametere?.ttFaktiskRegnesTil}")
            }
            kommentar(
                """Hvis fylte 67 år og overgang fra UP eller AFP så unntak fra virk -2/-3
            regelen. 
        Førstevirk mellom 1973 og 1991 og faktisk trygdetid regnes til utgang år fyller 67.
        Ref. CR 188535."""
            )
        }

        regel("AvvikHovedregelFørstevirkFør1973") {
            HVIS { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { avvikHovedregel }
            OG { innFørsteVirk!!.toLocalDate() < localDate(1973, 1, 1) }
            SÅ {
                innParametere?.ttFaktiskRegnesTil = utgangÅrFyller70
                log_debug("[HIT] BestemFaktiskTTRegnesTilAP2011RS.AP2011FørstevirkFør1973OvergangFraUP, TT til ${innParametere?.ttFaktiskRegnesTil}")
            }
            kommentar(
                """Hvis fylte 67 år og overgang fra UP eller AFP så unntak fra virk -2/-3
            regelen.
        Førstevirk før 1973 og faktisk trygdetid regnes til utgang år fyller 70.
        Ref. CR 188535. """
            )
        }
    }
}