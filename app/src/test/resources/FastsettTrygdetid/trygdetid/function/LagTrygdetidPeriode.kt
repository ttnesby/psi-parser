package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidPeriodeLengde
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.koder.FaktiskTrygdetidTypeEnum

fun lagTrygdetidPeriode(
    innSumTrygdetid: MutableMap<FaktiskTrygdetidTypeEnum, TrygdetidPeriodeLengde> = mutableMapOf(),
    innFaktiskTrygdetidType: FaktiskTrygdetidTypeEnum
)
        : TrygdetidPeriodeLengde {
    var retval: TrygdetidPeriodeLengde?

    retval = innSumTrygdetid[innFaktiskTrygdetidType]
    if (retval == null) {
        retval = TrygdetidPeriodeLengde(
            år = 0,
            måneder = 0,
            dager = 0
        )
        innSumTrygdetid[innFaktiskTrygdetidType] = retval
    }
    return retval
}
