package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidPeriodeLengde

fun trygdetidperiodelengdestring(innTpl: TrygdetidPeriodeLengde?): String {
    val sb: StringBuilder = StringBuilder()

    if (innTpl?.år != null && innTpl.år > 0) {
        sb.append(innTpl.år).append(" år")
    }
    if (innTpl?.år != null && innTpl.år > 0 && innTpl.måneder > 0) {
        sb.append(", ")
    }
    if (innTpl?.måneder != null && innTpl.måneder > 0) {
        sb.append(innTpl.måneder).append(" måneder")
    }
    if (innTpl?.måneder != null && innTpl.måneder > 0 && innTpl.dager > 0) {
        sb.append(", ")
    }
    if (innTpl?.dager != null && innTpl.dager > 0) {
        sb.append(innTpl.dager).append(" dager")
    }

    return sb.toString()
}
