package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.flyter

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleflow
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.RegelverkTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.finnTTResultat
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidPeriodeLengde
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.koder.FaktiskTrygdetidTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.*
import no.nav.pensjon.regler.internal.domain.TTPeriode
import no.nav.pensjon.regler.internal.domain.Trygdetid
import no.nav.preg.system.helper.toLocalDate

class FaktiskTrygdetidFlyt(
    private val trygdetidParametere: TrygdetidParameterType
) : AbstractPensjonRuleflow() {
    private val sumTrygdetid: MutableMap<FaktiskTrygdetidTypeEnum, TrygdetidPeriodeLengde> = mutableMapOf()
    private var ttResultat: Trygdetid? = null
    private var ttPeriodeListe: MutableList<TTPeriode> = mutableListOf()

    override var ruleflow: () -> Unit = {

        /**
         * Task: Finn hvilket resultat
         */
        ttResultat = finnTTResultat(trygdetidParametere)
        /**
         * Task: Bestem TT Parametere
         */
        BestemFaktiskTTParametereRS(
            trygdetidParametere.variable,
            trygdetidParametere.grunnlag?.bruker,
            trygdetidParametere.grunnlag?.førsteVirk,
            ttResultat
        ).run(this)
        /**
         * Task: Alderspensjon?
         */
        forgrening("Alderspensjon?") {
            gren {
                betingelse { trygdetidParametere.variable?.ytelseType == KravlinjeTypeEnum.AP }
                flyt {
                    /**
                     * Task: AP1967?
                     */
                    forgrening("AP1967?") {
                        gren {
                            betingelse { trygdetidParametere.variable?.regelverkType != RegelverkTypeEnum.G_REG }
                            flyt {
                                /**
                                 * Task: Bestem faktisk TT regnes til for AP2011
                                 */
                                BestemFaktiskTTRegnesTilAP2011RS(
                                    trygdetidParametere.variable,
                                    trygdetidParametere.grunnlag?.bruker,
                                    trygdetidParametere.grunnlag?.virkFom,
                                    trygdetidParametere.grunnlag?.førsteVirk
                                ).run(this)
                            }
                        }
                        gren {
                            betingelse { trygdetidParametere.variable?.regelverkType == RegelverkTypeEnum.G_REG }
                            flyt {
                                /**
                                 * Task: Bestem faktisk TT regnes til for AP1967
                                 */
                                BestemFaktiskTTRegnesTilAP1967RS(
                                    trygdetidParametere.variable,
                                    trygdetidParametere.grunnlag?.bruker,
                                    trygdetidParametere.grunnlag?.førsteVirk
                                ).run(this)
                            }
                        }
                    }
                }
            }
            gren {
                betingelse { trygdetidParametere.variable?.ytelseType != KravlinjeTypeEnum.AP }
                flyt {
                    /**
                     * Task: Bestem faktisk TT regnes til
                     */
                    BestemFaktiskTTRegnesTilRS(
                        trygdetidParametere.variable!!,
                        trygdetidParametere.grunnlag?.bruker!!,
                        trygdetidParametere.grunnlag?.virkFom!!.toLocalDate(),
                        trygdetidParametere.grunnlag?.førsteVirk!!.toLocalDate()
                    ).run(this)
                }
            }
        }
        /**
         * Task: Bodd eller arbeidet i utlandet?
         * Branch 1: AP1967, AP2011, AP2016 kap 19 og annet enn AP
        Branch 2: AP2016 kap 20, AP2025
        Branch 3: default
         */
        forgrening("Bodd eller arbeidet i utlandet?") {
            gren {
                betingelse { trygdetidParametere.grunnlag?.boddEllerArbeidetIUtlandet == true }
                flyt {
                    /**
                     * Task: TT perioder?
                     * Branch 1: AP1967, AP2011, AP2016 kap 19 og annet enn AP
                    Branch 2: AP2016 kap 20, AP2025
                    Branch 3: default
                     */
                    forgrening("TT perioder?") {
                        gren {
                            betingelse {
                                (((trygdetidParametere.variable?.regelverkType == RegelverkTypeEnum.N_REG_G_N_OPPTJ
                                        && trygdetidParametere.variable?.kapittel20 == false)
                                        || trygdetidParametere.variable?.regelverkType == RegelverkTypeEnum.N_REG_G_OPPTJ
                                        || trygdetidParametere.variable?.regelverkType == RegelverkTypeEnum.G_REG)
                                        && trygdetidParametere.grunnlag?.bruker?.trygdetidPerioder != null
                                        && trygdetidParametere.grunnlag?.bruker!!.trygdetidPerioder.isNotEmpty())
                            }
                            flyt {
                                /**
                                 * Task: Behandle poeng i inn/ut-år
                                 */
                                PoengIInnOgUtÅrFlyt(trygdetidParametere).run(this)
                                /**
                                 * Task: Konsolider norske perioder
                                 */
                                ttPeriodeListe =
                                    KonsoliderNorskePerioderRS(trygdetidParametere.grunnlag?.trygdetidsperioder!!).run(
                                        this
                                    )

                                /**
                                 * Loop: Legg sammen TT perioder
                                 * Ref. CR 209496. Skal gi merknad dersom visse kombinasjoner av trygdetid/trygdeavtale i
                                Sveits/EØS.
                                 */
                                var periodeTeller = 0
                                val periodeArray: MutableList<TTPeriode> = ttPeriodeListe
                                var aktuellPeriodeLengde: TrygdetidPeriodeLengde?
                                while (periodeTeller < periodeArray.size) {
                                    /**
                                     * Task: Beregn TT periode
                                     */
                                    aktuellPeriodeLengde = BeregnTTPeriodeLengdeRS(
                                        periodeArray[periodeTeller],
                                        ttResultat,
                                        trygdetidParametere.variable
                                    ).run(this)

                                    /**
                                     * Legg til perioder over 0.
                                     */
                                    forgrening("Har aktuellPeriodeLengde > 0") {
                                        gren {
                                            betingelse { aktuellPeriodeLengde.totaltAntallDager() > 0 }
                                            flyt {
                                                /**
                                                 * Task: Adder TT periode
                                                 */
                                                AdderTTperiodeRS(
                                                    periodeArray[periodeTeller],
                                                    aktuellPeriodeLengde,
                                                    sumTrygdetid,
                                                    trygdetidParametere.grunnlag?.virkFom!!
                                                ).run(this)
                                            }
                                        }
                                    }

                                    /**
                                     * Task: Trygdeavtale?
                                     * Ref. CR 209496. Skal gi merknad dersom visse kombinasjoner av trygdetid/trygdeavtale i
                                    Sveits/EØS.
                                     */
                                    forgrening("Trygdeavtale?") {
                                        gren {
                                            betingelse { trygdetidParametere.grunnlag?.bruker?.trygdeavtale != null }
                                            flyt {
                                                /**
                                                 * Task: Kontroller periode Sveits
                                                 * Ref. CR 209496. Skal gi merknad dersom visse kombinasjoner av trygdetid/trygdeavtale i
                                                Sveits/EØS.
                                                 */
                                                KontrollerTTPeriodeSveitsRS(
                                                    trygdetidParametere.grunnlag?.bruker,
                                                    periodeArray[periodeTeller],
                                                    ttResultat
                                                ).run(this)
                                            }
                                        }
                                        gren {
                                            betingelse { trygdetidParametere.grunnlag?.bruker?.trygdeavtale == null }
                                            flyt {
                                            }
                                        }
                                    }
                                    periodeTeller += 1
                                }
                                /**
                                 * Task: Sammenligne poengår og perioder?
                                 */
                                forgrening("Sammenligne poengår og perioder?") {
                                    gren {
                                        betingelse { trygdetidParametere.variable?.ytelseType != KravlinjeTypeEnum.UT }
                                        flyt {
                                            /**
                                             * Task: Sammenlign perioder og poengår
                                             */
                                            SammenlignPerioderOgPoengårRS(
                                                ttPeriodeListe,
                                                trygdetidParametere.grunnlag?.bruker!!,
                                                trygdetidParametere.variable!!,
                                                ttResultat,
                                                trygdetidParametere.grunnlag?.beregning
                                            ).run(this)
                                        }
                                    }
                                    gren {
                                        betingelse { (trygdetidParametere.variable?.ytelseType == KravlinjeTypeEnum.UT) }
                                        flyt {
                                        }
                                    }
                                }
                            }
                        }
                        gren {
                            betingelse {
                                (((trygdetidParametere.variable?.regelverkType ==
                                        RegelverkTypeEnum.N_REG_G_N_OPPTJ && trygdetidParametere.variable?.kapittel20 == true)
                                        || trygdetidParametere.variable?.regelverkType == RegelverkTypeEnum.N_REG_N_OPPTJ)
                                        && trygdetidParametere.grunnlag?.bruker?.trygdetidPerioderKapittel20 != null
                                        && trygdetidParametere.grunnlag?.bruker?.trygdetidPerioderKapittel20?.isEmpty() == false)
                            }
                            flyt {
                                /**
                                 * Task: Konsolider norske perioder
                                 */
                                ttPeriodeListe = KonsoliderNorskePerioderRS(
                                    trygdetidParametere.grunnlag?.bruker?.trygdetidPerioderKapittel20!!
                                ).run(this)
                                /**
                                 * Loop: Legg sammen TT perioder
                                 * Ref. CR 209496. Skal gi merknad dersom visse kombinasjoner av trygdetid/trygdeavtale i
                                Sveits/EØS.
                                 */
                                var periodeTeller = 0
                                val periodeArray: MutableList<TTPeriode> = ttPeriodeListe
                                var aktuellPeriodeLengde: TrygdetidPeriodeLengde?
                                while (periodeTeller < periodeArray.size) {
                                    /**
                                     * Task: Beregn TT periode
                                     */
                                    aktuellPeriodeLengde = BeregnTTPeriodeLengdeRS(
                                        periodeArray[periodeTeller],
                                        ttResultat,
                                        trygdetidParametere.variable
                                    ).run(this)

                                    /**
                                     * Legg til perioder over 0.
                                     */
                                    forgrening("Har aktuellPeriodeLengde > 0") {
                                        gren {
                                            betingelse { aktuellPeriodeLengde.totaltAntallDager() > 0 }
                                            flyt {
                                                /**
                                                 * Task: Adder TT periode
                                                 */
                                                AdderTTperiodeRS(
                                                    periodeArray[periodeTeller],
                                                    aktuellPeriodeLengde,
                                                    sumTrygdetid,
                                                    trygdetidParametere.grunnlag?.virkFom!!
                                                ).run(this)
                                            }
                                        }
                                    }

                                    /**
                                     * Task: Trygdeavtale?
                                     * Ref. CR 209496. Skal gi merknad dersom visse kombinasjoner av trygdetid/trygdeavtale i
                                    Sveits/EØS.
                                     */
                                    forgrening("Trygdeavtale?") {
                                        gren {
                                            betingelse { trygdetidParametere.grunnlag?.bruker?.trygdeavtale != null }
                                            flyt {
                                                /**
                                                 * Task: Kontroller periode Sveits
                                                 * Ref. CR 209496. Skal gi merknad dersom visse kombinasjoner av trygdetid/trygdeavtale i
                                                Sveits/EØS.
                                                 */
                                                KontrollerTTPeriodeSveitsRS(
                                                    trygdetidParametere.grunnlag?.bruker,
                                                    periodeArray[periodeTeller], ttResultat
                                                ).run(this)
                                            }
                                        }
                                        gren {
                                            betingelse { trygdetidParametere.grunnlag?.bruker?.trygdeavtale == null }
                                            flyt {
                                            }
                                        }
                                    }
                                    periodeTeller += 1
                                }
                            }
                        }
                        gren {
                            betingelse { true }
                            flyt {
                            }
                        }
                    }
                }
            }
            gren {
                betingelse { trygdetidParametere.grunnlag?.boddEllerArbeidetIUtlandet == false }
                flyt {
                    /**
                     * Task: Bare TT Norge
                     */
                    BareTTNorgeRS(
                        sumTrygdetid,
                        trygdetidParametere.variable,
                        ttResultat
                    ).run(this)
                }
            }
        }
        /**
         * Task: Beregn Faktisk TT
         */
        BeregnFaktiskTTRS(
            sumTrygdetid,
            ttResultat!!,
            trygdetidParametere.variable!!,
            trygdetidParametere.grunnlag?.førsteVirk!!
        ).run(this)

    }

}
