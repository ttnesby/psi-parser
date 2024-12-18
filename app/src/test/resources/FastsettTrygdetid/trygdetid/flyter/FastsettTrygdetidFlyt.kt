package FastsettTrygdetid.trygdetid.flyter

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleflow
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.TrygdetidGarantitypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.BestemUførehistorikkPåvirkerTTRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.BestemUføretidspunktUTRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.HåndterMedlemskapInfoRS

class FastsettTrygdetidFlyt(
    private val trygdetidParametere: TrygdetidParameterType
) : AbstractPensjonRuleflow() {

    override var ruleflow: () -> Unit = {

        /**
         * Task: Uføretrygd?
         */
        forgrening("Uføretrygd?") {
            gren {
                /**
                 * Ytelse er UT
                 */
                betingelse("Ja") { (trygdetidParametere.variable?.ytelseType == KravlinjeTypeEnum.UT) }
                flyt {
                    /**
                     * Task: Bestem uft for UT
                     */
                    BestemUføretidspunktUTRS(trygdetidParametere).run(this)
                    /**
                     * Task: Håndter medlemskap info
                     */
                    HåndterMedlemskapInfoRS(trygdetidParametere.grunnlag).run(this)
                }
            }
            gren {
                /**
                 * Ytelse er ikke UT
                 */
                betingelse("Nei") { (trygdetidParametere.variable?.ytelseType != KravlinjeTypeEnum.UT) }
                flyt {
                }
            }
        }
        /**
         * Task: Faktisk Trygdetid
         */
        FaktiskTrygdetidFlyt(trygdetidParametere).run(this)
        /**
         * Task: Framtidig Trygdetid
         */
        FramtidigTrygdetidFlyt(trygdetidParametere).run(this)
        /**
         * Task: Samlet Trygdetid
         */
        SamletTrygdetidFlyt(trygdetidParametere).run(this)
        /**
         * Task: Uførehistorikk?
         * Branch 1: Hvis avdød med uførehistorikk i etterlattesak (GJP/GJR) så skal §3-7 anvendes.
        Branch 2: Hvis bruker med uførehistorikk og overgang AP så skal §3-5 anvendes.
         */
        forgrening("Uførehistorikk?") {
            gren {
                /**
                 * Hvis avdød med uførehistorikk i etterlattesak (GJP/GJR) så skal §3-7 anvendes.
                 */
                betingelse("Ja") {
                    (trygdetidParametere.grunnlag?.bruker?.uforeHistorikk != null
                            && trygdetidParametere.grunnlag?.bruker?.uforeHistorikk?.uforeperiodeListe?.isEmpty() == false)
                }
                flyt {
                    /**
                     * Task: Bestem om uførehistorikk påvirker trygdetid
                     */
                    BestemUførehistorikkPåvirkerTTRS(
                        trygdetidParametere,
                        trygdetidParametere.grunnlag?.bruker,
                        trygdetidParametere.variable?.ytelseType, trygdetidParametere.grunnlag?.virkFom
                    ).run(this)
                    /**
                     * Task: Uførehistorikk påvirker trygdetid?
                     * Branch 1: Hvis avdød med uførehistorikk i etterlattesak (GJP/GJR) så skal §3-7 anvendes.
                    Branch 2: Hvis bruker med uførehistorikk og overgang AP så skal §3-5 anvendes.
                     */
                    forgrening("Uførehistorikk påvirker trygdetid?") {
                        gren {
                            betingelse("FT $3-7") {
                                (trygdetidParametere.variable?.garantiType != null

                                        && trygdetidParametere.variable?.garantiType == TrygdetidGarantitypeEnum.FT_3_7)
                            }
                            flyt {
                                /**
                                 * Task: Trygdetid Etterlatte
                                 */
                                TrygdetidEtterlatteFlyt(trygdetidParametere).run(this)
                            }
                        }
                        gren {
                            betingelse("FT $3-5") {
                                (trygdetidParametere.variable?.garantiType != null

                                        && trygdetidParametere.variable?.garantiType == TrygdetidGarantitypeEnum.FT_3_5)
                            }
                            flyt {
                                /**
                                 * Task: Overgang Uføre til AP
                                 */
                                TrygdetidUPtilAPFlyt(trygdetidParametere).run(this)
                            }
                        }
                        gren {
                            betingelse("Nei") { (trygdetidParametere.variable?.garantiType == null) }
                            flyt {
                            }
                        }
                    }
                }
            }
            gren {
                betingelse("Nei") {
                    !(trygdetidParametere.grunnlag?.bruker?.uforeHistorikk != null
                            && trygdetidParametere.grunnlag?.bruker?.uforeHistorikk?.uforeperiodeListe?.isEmpty() == false)
                }
                flyt {
                }
            }
        }

    }

}
