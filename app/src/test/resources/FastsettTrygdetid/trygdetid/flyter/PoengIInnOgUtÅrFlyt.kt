package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.flyter

import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.function.avrundPoengtall
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.function.beregnPoengtallAvOpptjeningListe
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.function.opprettBeregningsPeriode
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.klasser.PoengrekkeParameter
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.klasser.PoengrekkeVariable
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.regler.OpprettPoengrekkeResultatRS
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.RegelverkTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.bestemFttRedusert
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.finnKonverteringUføretidspunkt
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.settTTperiodeListe
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.koder.UføretrygdTilfelleEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.BehandleOverlappendeNorUtlPerioderRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.BehandlePoengIInnOgUtÅrRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.BestemUføretrygdTilfelleRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.LagMerknadForKonverteringUføretidspunktRS
import no.nav.pensjon.regler.internal.domain.TTPeriode
import no.nav.pensjon.regler.internal.domain.beregning.Beregning
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleflow
import no.nav.preg.system.helper.år
import java.util.*

class PoengIInnOgUtÅrFlyt(
    private val trygdetidParametere: TrygdetidParameterType?
) : AbstractPensjonRuleflow() {
    private var kopiliste: MutableList<TTPeriode> = mutableListOf()
    private var uføretrygdTilfelle: UføretrygdTilfelleEnum? = null
    private var konvUFT: Date? = null
    private var fttRedusert: Boolean? = null
    private var poengrekkeParam: PoengrekkeParameter =
        PoengrekkeParameter(variable = PoengrekkeVariable())
    private var aBeregning: Beregning = Beregning()
    override var ruleflow: () -> Unit = {
 
        /**
         * Task: Uføretrygd?
         * PK-10607: Hvis overgang fra UT til AP (kapittel 19) medregnes poeng i inn/ut-år.
        Hvis framtidig trygdetid for UT ikke var redusert vil ikke alderskonverteringsbatch (BPEN006)
        lage oppgave til saksbehandler.
        I slike tilfeller forventes at poeng i inn/ut-år håndteres automatisk.
         */
        forgrening("Uføretrygd?") {
            gren {
                betingelse { (trygdetidParametere?.variable?.ytelseType == KravlinjeTypeEnum.UT) }
                flyt {
                    /**
                     * Task: Bestem uføretrygd tilfelle
                     */
                    uføretrygdTilfelle = BestemUføretrygdTilfelleRS(trygdetidParametere).run(this)
                    /**
                     * Task: Uføretrygd tilfelle?
                     * PK-10607: Hvis overgang fra UT til AP (kapittel 19) medregnes poeng i inn/ut-år.
                    Hvis framtidig trygdetid for UT ikke var redusert vil ikke alderskonverteringsbatch (BPEN006)
                    lage oppgave til saksbehandler.
                    I slike tilfeller forventes at poeng i inn/ut-år håndteres automatisk.
                     */
                    forgrening("Uføretrygd tilfelle?") {
                        gren {
                            betingelse { uføretrygdTilfelle == UføretrygdTilfelleEnum.UTovergangAP }
                            flyt {
                                /**
                                 * Task: Bestem redusert framtidig trygdetid
                                 */
                                fttRedusert =
                                    bestemFttRedusert(
                                        trygdetidParametere?.grunnlag?.bruker?.uforeHistorikk?.getSortedUforeperiodeListe(
                                            true
                                        )!!
                                    )
                                /**
                                 * Task: Fremtidig trygdetid redusert?
                                 * PK-10607: Hvis overgang fra UT til AP (kapittel 19) medregnes poeng i inn/ut-år.
                                Hvis framtidig trygdetid for UT ikke var redusert vil ikke alderskonverteringsbatch (BPEN006)
                                lage oppgave til saksbehandler.
                                I slike tilfeller forventes at poeng i inn/ut-år håndteres automatisk.
                                 */
                                forgrening("Fremtidig trygdetid redusert?") {
                                    gren {
                                        betingelse { fttRedusert!! }
                                        flyt {
                                        }
                                    }
                                    gren {
                                        betingelse { !fttRedusert!! }
                                        flyt {
                                            /**
                                             * Task: Opprett poengrekke resultat
                                             */
                                            OpprettPoengrekkeResultatRS(
                                                aBeregning,
                                                trygdetidParametere.grunnlag?.bruker,
                                                poengrekkeParam
                                            ).run(this)
                                            /**
                                             * Task: Opprett beregningsperiode
                                             */
                                            opprettBeregningsPeriode(
                                                trygdetidParametere.variable?.ttFaktiskRegnesFra?.år!!,
                                                trygdetidParametere.variable?.ttFaktiskRegnesTil?.år!!,
                                                aBeregning,
                                                poengrekkeParam,
                                                (trygdetidParametere.variable?.ttFaktiskRegnesFra!!.år..trygdetidParametere.variable?.ttFaktiskRegnesTil!!.år).associateWith { år -> Veiet_grunnbeløp(år) }
                                            )
                                            /**
                                             * Task: Beregn poengtall
                                             */
                                            beregnPoengtallAvOpptjeningListe(
                                                trygdetidParametere.grunnlag?.bruker?.opptjeningsgrunnlagListe!!,
                                                trygdetidParametere.variable?.ttFaktiskRegnesTil?.år,
                                                poengrekkeParam.variable,
                                                this
                                            )
                                            /**
                                             * Task: Avrund poengtall
                                             */
                                            avrundPoengtall(poengrekkeParam.variable?.poengtallListe!!)
                                        }
                                    }
                                }
                                /**
                                 * Loop: TrygdetidsperiodeLoop
                                 */
                                val periodeArray: MutableList<TTPeriode> =
                                    trygdetidParametere.grunnlag?.trygdetidsperioder ?: mutableListOf()
                                var periodeTeller = 0
                                while ((periodeTeller < periodeArray.size)) {
                                    /**
                                     * Task: Behandle poeng i inn/ut-år
                                     */
                                    BehandlePoengIInnOgUtÅrRS(
                                        periodeArray[periodeTeller],
                                        kopiliste,
                                        null,
                                        poengrekkeParam.variable?.poengtallListe,
                                        trygdetidParametere.resultat?.trygdetid
                                    ).run(this)
                                    periodeTeller += 1
                                }
                                /**
                                 * Task: Sett modifisert periodeliste
                                 */
                                settTTperiodeListe(trygdetidParametere.grunnlag, kopiliste)
                            }
                        }
                        gren {
                            betingelse { uføretrygdTilfelle == UføretrygdTilfelleEnum.UTkonvertertFraUP }
                            flyt {
                                /**
                                 * Task: Finn konverteringsuføretidspunkt
                                 */
                                konvUFT =
                                    finnKonverteringUføretidspunkt(
                                        trygdetidParametere?.grunnlag?.bruker?.uforeHistorikk?.getSortedUforeperiodeListe(
                                            false
                                        )!!
                                    )
                                /**
                                 * Task: Lag merknad om bruk av poeng i innår/utår for UP konvertert til UT
                                 */
                                LagMerknadForKonverteringUføretidspunktRS(
                                    trygdetidParametere.resultat?.trygdetid,
                                    konvUFT
                                ).run(this)
                                /**
                                 * Loop: TrygdetidsperiodeLoop
                                 */
                                val periodeArray: MutableList<TTPeriode> =
                                    trygdetidParametere.grunnlag?.trygdetidsperioder ?: mutableListOf()
                                var periodeTeller = 0
                                while ((periodeTeller < periodeArray.size)) {
                                    /**
                                     * Task: Behandle poeng i inn/ut-år
                                     */
                                    BehandlePoengIInnOgUtÅrRS(
                                        periodeArray[periodeTeller],
                                        kopiliste,
                                        konvUFT,
                                        null,
                                        trygdetidParametere.resultat?.trygdetid
                                    ).run(this)
                                    periodeTeller += 1
                                }
                                /**
                                 * Task: Sett modifisert periodeliste
                                 */
                                settTTperiodeListe(trygdetidParametere.grunnlag, kopiliste)
                            }
                        }
                        gren {
                            betingelse { uføretrygdTilfelle == UføretrygdTilfelleEnum.UTløpende }
                            flyt {
                            }
                        }
                    }
                }
            }
            gren {
                betingelse { trygdetidParametere?.variable?.ytelseType != KravlinjeTypeEnum.UT }
                flyt {
                    /**
                     * Loop: TrygdetidsperiodeLoop
                     */
                    val periodeArray: MutableList<TTPeriode> =
                        trygdetidParametere?.grunnlag?.trygdetidsperioder ?: mutableListOf()
                    var periodeTeller = 0
                    while (periodeTeller < periodeArray.size) {
                        /**
                         * Task: Behandle poeng i inn/ut-år
                         */
                        BehandlePoengIInnOgUtÅrRS(
                            periodeArray[periodeTeller],
                            kopiliste,
                            null,
                            null,
                            trygdetidParametere?.resultat?.trygdetid
                        ).run(this)
                        periodeTeller += 1
                    }
                    /**
                     * Task: AP kap 19?
                     */
                    forgrening("AP kap 19?") {
                        gren {
                            betingelse {
                                ((trygdetidParametere?.variable?.regelverkType == RegelverkTypeEnum.N_REG_G_N_OPPTJ
                                        && trygdetidParametere.variable?.kapittel20 == false)
                                        || (trygdetidParametere?.variable?.regelverkType == RegelverkTypeEnum.N_REG_G_OPPTJ))
                            }
                            flyt {
                                /**
                                 * Task: Behandle overlappende norske og utenlandske perioder
                                 */
                                kopiliste = BehandleOverlappendeNorUtlPerioderRS(kopiliste).run(this)
                            }
                        }
                        gren {
                            betingelse {
                                (!((trygdetidParametere?.variable?.regelverkType ==
                                        RegelverkTypeEnum.N_REG_G_N_OPPTJ && trygdetidParametere.variable?.kapittel20 == false)
                                        || (trygdetidParametere?.variable?.regelverkType == RegelverkTypeEnum.N_REG_G_OPPTJ)))
                            }
                            flyt {
                            }
                        }
                    }
                    /**
                     * Task: Sett modifisert periodeliste
                     */
                    settTTperiodeListe(trygdetidParametere?.grunnlag, kopiliste)
                }
            }
        }

    }

}
