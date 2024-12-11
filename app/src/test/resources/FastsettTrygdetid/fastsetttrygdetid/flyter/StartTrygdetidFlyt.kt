package no.nav.domain.pensjon.regler.repository.tjeneste.fastsetttrygdetid.flyter

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleflow
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.settPregVariableUtenGlobals
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.GrunnlagsrolleEnum.*
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.RegelverkTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.VedtakResultatEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.regler.BestemBosattLandRS
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.regler.FinnPersonensFørsteVirkRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.flyter.FastsettTrygdetidFlyt
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.flyter.TrygdetidOvergangskullFlyt
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.BestemTTKapittel20RS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.InitTrygdetidResultatRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.InitTrygdetidVariableRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.SettVirkFomOgTomPåTrygdetidResultatRS
import no.nav.domain.pensjon.regler.repository.tjeneste.fastsetttrygdetid.function.settEPStilSøker
import no.nav.pensjon.regler.internal.domain.beregning.Beregning
import no.nav.pensjon.regler.internal.domain.vedtak.VilkarsVedtak
import java.util.*

class StartTrygdetidFlyt(
    private val trygdetidParametere: TrygdetidParameterType
) : AbstractPensjonRuleflow() {
    private var førsteVirk: Date? = null
    private var kapittel20: Boolean? = null

    override var ruleflow: () -> Unit = {

        /**
         * Task: Kontroller informasjonsgrunnlag
         */
        KontrollerTrygdetidInformasjonsgrunnlagFlyt(trygdetidParametere).run(this)
        /**
         * Task: Input ok?
         * EPS skal beregnes som SOKER når ytelsen er AP. CR 165527
         */
        forgrening("Input ok?") {
            gren {
                betingelse { trygdetidParametere.resultat?.pakkseddel!!.merknadListe.isEmpty() }
                flyt {
                    /**
                     * Task: Init Trygdetidberegning
                     */
                    settPregVariableUtenGlobals(
                        trygdetidParametere.grunnlag?.bruker,
                        trygdetidParametere.grunnlag?.virkFom
                    )
                    trygdetidParametere.grunnlag?.bruker?.vilkarsVedtak = VilkarsVedtak(
                        kravlinjeType = trygdetidParametere.grunnlag?.ytelseType,
                        virkFom = trygdetidParametere.grunnlag?.virkFom,
                        forsteVirk = trygdetidParametere.grunnlag?.førsteVirk,
                        vilkarsvedtakResultat = VedtakResultatEnum.INNV
                    )
                    trygdetidParametere.grunnlag?.beregning = Beregning()

                    /**
                     * Task: AP og bruker er EPS?
                     * EPS skal beregnes som SOKER når ytelsen er AP. CR 165527
                     */
                    forgrening("AP og bruker er EPS?") {
                        gren {
                            betingelse {
                                trygdetidParametere.grunnlag?.ytelseType == KravlinjeTypeEnum.AP &&
                                        trygdetidParametere.grunnlag?.bruker?.grunnlagsrolle in listOf(
                                            EKTEF,
                                            PARTNER,
                                            SAMBO
                                        )
                            }
                            flyt {
                                /**
                                 * Task: Gjør om EPS til soker
                                 * Gjør om EPS til soker
                                 */
                                settEPStilSøker(trygdetidParametere)
                            }
                        }
                        gren {
                            betingelse { false }
                            flyt {
                            }
                        }
                    }
                    /**
                     * Task: Finn første virkningsdato i trygden
                     */
                    førsteVirk = FinnPersonensFørsteVirkRS(
                        trygdetidParametere.grunnlag?.bruker!!,
                        trygdetidParametere.grunnlag?.førsteVirk,
                        trygdetidParametere.grunnlag?.virkFom!!,
                        trygdetidParametere.grunnlag?.ytelseType!!,
                        trygdetidParametere.grunnlag?.uttaksgradListe!!
                    ).run(this)
                    /**
                     * Task: Bestem kapittel 20
                     */
                    kapittel20 = BestemTTKapittel20RS(
                        trygdetidParametere.grunnlag?.ytelseType!!,
                        trygdetidParametere.grunnlag?.regelverkType
                    ).run(this)
                    /**
                     * Task: Init Variable
                     */
                    InitTrygdetidVariableRS(trygdetidParametere, førsteVirk, kapittel20).run(this)
                    /**
                     * Task: Init resultat
                     */
                    InitTrygdetidResultatRS(trygdetidParametere, kapittel20).run(this)
                    /**
                     * Task: Kontroller bostedLand
                     */
                    BestemBosattLandRS(trygdetidParametere.grunnlag?.bruker!!).run(this)
                    /**
                     * Task: Overgangskull?
                     */
                    forgrening("Overgangskull?") {
                        gren {
                            betingelse {
                                (trygdetidParametere.variable?.kapittel20 == true
                                        && trygdetidParametere.variable?.regelverkType == RegelverkTypeEnum.N_REG_G_N_OPPTJ)
                            }
                            flyt {
                                /**
                                 * Task: Fastsett Trygdetid overgangskull
                                 */
                                TrygdetidOvergangskullFlyt(trygdetidParametere).run(this)
                            }
                        }
                        gren {
                            betingelse {
                                !(trygdetidParametere.variable?.kapittel20 == true
                                        && trygdetidParametere.variable?.regelverkType == RegelverkTypeEnum.N_REG_G_N_OPPTJ)
                            }
                            flyt {
                                /**
                                 * Task: Fastsett Trygdetid
                                 */
                                FastsettTrygdetidFlyt(trygdetidParametere).run(this)
                            }
                        }
                    }
                    /**
                     * Task: Sett virkFom og virkTom på alle returnerte trygdetider
                     */
                    SettVirkFomOgTomPåTrygdetidResultatRS(trygdetidParametere).run(this)
                }
            }
            gren {
                betingelse { trygdetidParametere.resultat?.pakkseddel!!.merknadListe.isNotEmpty() }
                flyt {
                }
            }
        }

    }

}
