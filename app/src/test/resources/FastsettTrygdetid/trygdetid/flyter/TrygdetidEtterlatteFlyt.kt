package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.flyter

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleflow
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.finnTTResultat
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.settOppBeregnTrygdetidSisteUFT
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.tilbakestillBeregnTrygdetidSisteUFT
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.BestemGunstigsteTTAvdødRS
import no.nav.pensjon.regler.internal.domain.Trygdetid

class TrygdetidEtterlatteFlyt(
    private val trygdetidParametere: TrygdetidParameterType
) : AbstractPensjonRuleflow() {
    private var TTavdød: Trygdetid? = null
    private var TTuføre: Trygdetid? = null

    override var ruleflow: () -> Unit = {

        /**
         * Task: Lagre trygdetid ved død
         */
        TTavdød = finnTTResultat(trygdetidParametere)
        /**
         * Task: Sett opp beregn trygdetid siste UFT
         */
        settOppBeregnTrygdetidSisteUFT(trygdetidParametere, TTavdød)
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
         * Task: Lagre trygdetid siste uføretidspunkt
         */
        TTuføre = finnTTResultat(trygdetidParametere)
        /**
         * Task: Bestem Gunstigste Trygdetid Avdød
         */
        BestemGunstigsteTTAvdødRS(
            trygdetidParametere,
            trygdetidParametere.grunnlag?.bruker, TTavdød,
            TTuføre
        ).run(this)
        /**
         * Task: Tilbakestill parametere
         */
        tilbakestillBeregnTrygdetidSisteUFT(trygdetidParametere)

    }

}
