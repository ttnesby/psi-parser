package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.flyter

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleflow
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.finnTTResultat
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.BeregnFramtidigTTRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.BestemFramtidigTTBeregnesRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.BestemFramtidigTTParametereRS
import no.nav.pensjon.regler.internal.domain.Trygdetid

class FramtidigTrygdetidFlyt(
    private val trygdetidParametere: TrygdetidParameterType
) : AbstractPensjonRuleflow() {
    private var fttBeregnes: Boolean? = null
    private var ttResultat: Trygdetid? = null

    override var ruleflow: () -> Unit = {

        /**
         * Task: Finn hvilket resultat
         */
        ttResultat = finnTTResultat(trygdetidParametere)
        /**
         * Task: Bestem om FTT skal beregnes
         */
        fttBeregnes = BestemFramtidigTTBeregnesRS(
            trygdetidParametere.grunnlag?.bruker,
            trygdetidParametere.variable
        ).run(this)
        /**
         * Task: Skal FTT beregnes?
         */
        forgrening("Skal FTT beregnes?") {
            gren {
                betingelse { fttBeregnes!! }
                flyt {
                    /**
                     * Task: Bestem FTT parametere
                     */
                    BestemFramtidigTTParametereRS(
                        trygdetidParametere.variable!!,
                        trygdetidParametere.grunnlag?.bruker,
                        ttResultat,
                        trygdetidParametere.grunnlag?.førsteVirk
                    ).run(this)
                    /**
                     * Task: Beregn FTT
                     */
                    BeregnFramtidigTTRS(
                        ttResultat!!,
                        trygdetidParametere.variable!!,
                        trygdetidParametere.grunnlag?.bruker!!,
                        trygdetidParametere.grunnlag?.førsteVirk!!
                    ).run(this)
                }
            }
            gren {
                betingelse { !fttBeregnes!! }
                flyt {
                }
            }
        }
    }
}
