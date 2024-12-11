package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.flyter

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleflow
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.finnTTResultat
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.BeregnPoengtallSomPåvirkerTTRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.BeregnSamletTTRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.BeregnTrygdetidForPoengÅrRS
import no.nav.pensjon.regler.internal.domain.Trygdetid

class SamletTrygdetidFlyt(
    private val trygdetidParametere: TrygdetidParameterType
) : AbstractPensjonRuleflow() {
    private var ttResultat: Trygdetid? = null

    override var ruleflow: () -> Unit = {

        /**
         * Task: Finn hvilket resultat
         */
        ttResultat = finnTTResultat(trygdetidParametere)
        /**
         * Task: Medregne poengår?
         */
        forgrening("Medregne poengår?") {
            gren {
                betingelse { !(trygdetidParametere.variable?.kapittel20!! || trygdetidParametere.variable?.ytelseType == KravlinjeTypeEnum.UT) }
                flyt {
                    /**
                     * Task: Poengrekke oppgitt?
                     */
                    forgrening("Poengrekke oppgitt?") {
                        gren {
                            betingelse { trygdetidParametere.grunnlag?.beregning?.tp?.spt?.poengrekke == null }
                            flyt {
                                /**
                                 * Task: Beregn poengtall som påvirker TT
                                 */
                                BeregnPoengtallSomPåvirkerTTRS(
                                    trygdetidParametere.grunnlag?.bruker,
                                    trygdetidParametere.grunnlag?.beregning,
                                    trygdetidParametere.variable,
                                    trygdetidParametere.grunnlag?.virkFom
                                ).run(this)
                            }
                        }
                        gren {
                            betingelse {
                                trygdetidParametere.grunnlag?.beregning?.tp?.spt?.poengrekke != null
                            }
                            flyt {
                            }
                        }
                    }
                    /**
                     * Task: Beregn TT for poengår
                     */
                    BeregnTrygdetidForPoengÅrRS(
                        trygdetidParametere.grunnlag?.bruker!!,
                        trygdetidParametere.grunnlag?.beregning!!,
                        trygdetidParametere.variable?.regelverkType!!,
                        trygdetidParametere.grunnlag?.virkFom!!,
                        ttResultat!!
                    ).run(this)
                }
            }
            gren {
                betingelse {
                    (trygdetidParametere.variable?.kapittel20!!
                            || trygdetidParametere.variable?.ytelseType == KravlinjeTypeEnum.UT)
                }
                flyt {
                }
            }
        }
        /**
         * Task: Beregn Samlet Trygdetid
         */
        BeregnSamletTTRS(
            ttResultat!!,
            trygdetidParametere.grunnlag?.bruker!!,
            trygdetidParametere.variable!!,
            trygdetidParametere.grunnlag?.virkFom!!,
            trygdetidParametere.grunnlag?.førsteVirk!!
        ).run(this)

    }

}
