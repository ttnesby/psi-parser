package no.nav.domain.pensjon.regler.repository.tjeneste.fastsetttrygdetid.flyter

import no.nav.domain.pensjon.regler.repository.komponent.kontrollerinformasjonsgrunnlag.flyter.KontrollerBeregningsvilkårperiodeFlyt
import no.nav.domain.pensjon.regler.repository.komponent.kontrollerinformasjonsgrunnlag.flyter.KontrollerPersongrunnlagFlyt
import no.nav.domain.pensjon.regler.repository.komponent.kontrollerinformasjonsgrunnlag.function.initKontrollerBeregningsvilkårperioder
import no.nav.domain.pensjon.regler.repository.komponent.kontrollerinformasjonsgrunnlag.function.initKontrollerPersongrunnlag
import no.nav.domain.pensjon.regler.repository.komponent.kontrollerinformasjonsgrunnlag.klasser.GrunnlagParameter
import no.nav.domain.pensjon.regler.repository.komponent.kontrollerinformasjonsgrunnlag.klasser.KontrollerBeregningsvilkårperiodeParameter
import no.nav.domain.pensjon.regler.repository.komponent.kontrollerinformasjonsgrunnlag.klasser.KontrollerPersongrunnlagParameter
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.GrunnlagsrolleEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.tjeneste.fastsetttrygdetid.regler.InputdataKontrollTTRS
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleflow

class KontrollerTrygdetidInformasjonsgrunnlagFlyt(
    private val trygdetidParametere: TrygdetidParameterType
) : AbstractPensjonRuleflow() {
    private var kontrollParam: KontrollerPersongrunnlagParameter? = null
    private var kontrollBVPparameter: KontrollerBeregningsvilkårperiodeParameter? = null

    override var ruleflow: () -> Unit = {

        /**
         * Task: Kontroller input
         */
        InputdataKontrollTTRS(
            trygdetidParametere.grunnlag?.bruker,
            trygdetidParametere.grunnlag?.ytelseType,
            trygdetidParametere.grunnlag?.virkFom,
            trygdetidParametere.resultat?.pakkseddel?.merknadListe!!,
            trygdetidParametere.grunnlag?.førsteVirk,
            trygdetidParametere.grunnlag?.boddEllerArbeidetIUtlandet,
            trygdetidParametere.grunnlag?.regelverkType,
            trygdetidParametere.grunnlag?.ytelseType,
            trygdetidParametere.grunnlag?.uttaksgradListe!!
        ).run(this)
        /**
         * Task: Merknader?
         */
        forgrening("Merknader?") {
            gren {
                betingelse { (trygdetidParametere.resultat?.pakkseddel?.merknadListe?.size == 0) }
                flyt {
                    /**
                     * Task: Init kontroller persongrunnlag
                     */
                    kontrollParam = initKontrollerPersongrunnlag(
                        trygdetidParametere.grunnlag?.bruker,
                        trygdetidParametere.resultat?.pakkseddel!!,
                        GrunnlagParameter(),
                        trygdetidParametere.grunnlag?.virkFom,
                        false,
                        trygdetidParametere.grunnlag?.ytelseType,
                        trygdetidParametere.grunnlag?.regelverkType,
                        null,
                        mutableListOf(),
                        false
                    )
                    /**
                     * Task: Kontroller Persongrunnlag
                     */
                    KontrollerPersongrunnlagFlyt(kontrollParam!!).run(this)
                    forgrening("Uføretrygd?") {
                        gren {
                            betingelse { trygdetidParametere.grunnlag?.ytelseType == KravlinjeTypeEnum.UT }
                            flyt {
                                /**
                                 * Task: Init kontroll bvp
                                 */
                                kontrollBVPparameter = initKontrollerBeregningsvilkårperioder(
                                    trygdetidParametere.grunnlag!!.beregningsvilkarsPeriodeListe,
                                    trygdetidParametere.resultat?.pakkseddel,
                                    GrunnlagsrolleEnum.SOKER.toString(),
                                    KravlinjeTypeEnum.UT,
                                    true
                                )
                                /**
                                 * Task: Kontroller beregningsvilkårperioder
                                 */
                                KontrollerBeregningsvilkårperiodeFlyt(kontrollBVPparameter!!).run(this)
                            }
                        }
                        gren {
                            betingelse { trygdetidParametere.grunnlag?.ytelseType != KravlinjeTypeEnum.UT }
                            flyt {

                            }
                        }
                    }

                }
            }
            gren {
                betingelse { (trygdetidParametere.resultat?.pakkseddel?.merknadListe?.size!! > 0) }
                flyt {
                }
            }
        }

    }

}
