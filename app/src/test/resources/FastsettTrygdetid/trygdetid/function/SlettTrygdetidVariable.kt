package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType

/**
 * Setter variable til unknown.
 */
fun slettTrygdetidVariable(targetObject: TrygdetidParameterType?) {
    targetObject?.variable = null
}
