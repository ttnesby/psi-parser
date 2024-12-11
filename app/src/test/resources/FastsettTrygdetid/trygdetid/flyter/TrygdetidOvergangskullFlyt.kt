package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.flyter

import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.settTTKapittel20
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.slettTrygdetidVariable
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.InitTrygdetidVariableRS
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleflow

class TrygdetidOvergangskullFlyt(
    trygdetidParametere: TrygdetidParameterType
) : AbstractPensjonRuleflow() {

    override var ruleflow: () -> Unit = {
 
        /**
         * Task: Init kapittel 19
         */
        settTTKapittel20(trygdetidParametere, false)
        /**
         * Task: Trygdetid kapittel 19
         */
        FastsettTrygdetidFlyt(trygdetidParametere).run(this)
        /**
         * Task: Slett Trygdetidvariable
         */
        slettTrygdetidVariable(trygdetidParametere)
        /**
         * Task: Gjenopprett Trygdetidvariable
         */
        InitTrygdetidVariableRS(trygdetidParametere, null, true).run(this)
        /**
         * Task: Init kapittel 20
         */
        settTTKapittel20(trygdetidParametere, true)
        /**
         * Task: Trygdetid kapittel 20
         */
        FastsettTrygdetidFlyt(trygdetidParametere).run(this)

    }

}
