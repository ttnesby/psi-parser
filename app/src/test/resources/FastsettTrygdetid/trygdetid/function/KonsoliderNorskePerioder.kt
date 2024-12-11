package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.AvtaleLandEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.LandkodeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.PeriodeOverlappEnum.*
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.regler.BestemOverlappendePerioderRS
import no.nav.pensjon.regler.internal.domain.TTPeriode
import no.nav.preg.system.helper.PeekingIterator
import no.nav.preg.system.helper.dager
import no.nav.preg.system.helper.formatToString
import no.nav.preg.system.helper.toLocalDate
import no.nav.system.rule.dsl.AbstractRuleComponent

fun konsoliderNorskePerioder(
    innTTPeriodeListe: MutableList<TTPeriode>,
    ruleComponent: AbstractRuleComponent
): MutableList<TTPeriode> {
    val deepCopy = mutableListOf<TTPeriode>()

    innTTPeriodeListe.forEach {
        deepCopy.add(TTPeriode(it))
    }

    val kunNorskePerioder = deepCopy
        .filter { it.land == LandkodeEnum.NOR }
        .toMutableList()

    val norskePeriodeFørst = Comparator<TTPeriode> { a, b ->
        when {
            (a.land == LandkodeEnum.NOR && b.land != LandkodeEnum.NOR) -> -1
            (a.land != LandkodeEnum.NOR && b.land == LandkodeEnum.NOR) -> 1
            else -> a.compareTo(b)
        }
    }

    if (kunNorskePerioder.size < 2) {
        return deepCopy.sortedWith(norskePeriodeFørst).toMutableList()
    }

    kunNorskePerioder.sort()

    val peekingIterator = PeekingIterator(kunNorskePerioder.iterator())
    val deleteList = mutableListOf<TTPeriode>()

    var currentTTPeriode: TTPeriode?
    var nextTTPeriode: TTPeriode?

    while (peekingIterator.hasNext()) {
        currentTTPeriode = peekingIterator.next()
        nextTTPeriode = peekingIterator.peek()

        if (nextTTPeriode == null) break

        when (BestemOverlappendePerioderRS(
            currentTTPeriode.fom,
            currentTTPeriode.tom,
            nextTTPeriode.fom,
            nextTTPeriode.tom,
            false
        ).run(ruleComponent)) {
            perioder_overlapper_delvis -> {
                nextTTPeriode.fom = currentTTPeriode.fom
                deleteList.add(currentTTPeriode)
                log_debug("[HIT] KonsoliderNorskePerioder.PeriodeOverlapperDelvis, (${currentTTPeriode.fom!!.formatToString()}, ${currentTTPeriode.tom!!.formatToString()}) overlapper (${nextTTPeriode.fom!!.formatToString()}, ${nextTTPeriode.tom!!.formatToString()})")
            }
            periode_b_innenfor_periode_a -> {
                nextTTPeriode.fom = currentTTPeriode.fom
                nextTTPeriode.tom = currentTTPeriode.tom
                deleteList.add(currentTTPeriode)
            }
            periode_a_innenfor_periode_b, perioder_like -> {
                log_debug("[HIT] KonsoliderNorskePerioderRS.PeriodeOverlapperFullstendig, (${currentTTPeriode.fom!!.formatToString()}, ${currentTTPeriode.tom!!.formatToString()}) overlapper (${nextTTPeriode.fom!!.formatToString()}, ${nextTTPeriode.tom!!.formatToString()})")
                deleteList.add(currentTTPeriode)
            }
            perioder_ulike -> {
                if (currentTTPeriode.tom != null && (currentTTPeriode.tom!!.toLocalDate() + 1.dager) == nextTTPeriode.fom!!.toLocalDate()) {
                    nextTTPeriode.fom = currentTTPeriode.fom
                    nextTTPeriode.poengIUtAr = false // v2: litt uklart hvorfor dette skjer og om det er nødvendig.
                    log_debug("[HIT] KonsoliderNorskePerioder.PeriodeTilstøter, (${currentTTPeriode.fom?.formatToString()}, ${currentTTPeriode.tom?.formatToString()}) tilstøter (${nextTTPeriode.fom?.formatToString()}, ${nextTTPeriode.tom?.formatToString()})")
                    log_debug("[   ]    Endret periode til (${nextTTPeriode.fom?.formatToString()}, ${nextTTPeriode.tom?.formatToString()})")
                    deleteList.add(currentTTPeriode)
                }
            }
            else -> break
        }
    }

    kunNorskePerioder.removeAll(deleteList.toSet())
    return deepCopy
        .filter { it.land != LandkodeEnum.NOR }
        .toMutableList()
        .apply { addAll(kunNorskePerioder) }

}