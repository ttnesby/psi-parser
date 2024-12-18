package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.RegelverkTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.pensjon.regler.internal.domain.TTPeriode
import no.nav.pensjon.regler.internal.domain.beregning.Beregning
import no.nav.pensjon.regler.internal.domain.beregning2011.BeregningsvilkarPeriode
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.pensjon.regler.internal.domain.grunnlag.Uttaksgrad
import java.io.Serializable
import java.util.*

class TrygdetidGrunnlag : Serializable {
    var bruker: Persongrunnlag? = null
    var virkFom: Date? = null
    var virkTom: Date? = null
    var førsteVirk: Date? = null
    var ytelseType: KravlinjeTypeEnum? = null
    var boddEllerArbeidetIUtlandet: Boolean? = null
    var trygdetidsperioder: MutableList<TTPeriode> = mutableListOf()
    var regelverkType: RegelverkTypeEnum? = null
    var uttaksgradListe: MutableList<Uttaksgrad> = mutableListOf()
    var beregningsvilkarsPeriodeListe: MutableList<BeregningsvilkarPeriode> = mutableListOf()
    var beregning: Beregning? = null

    private var redusertFTTUT: Boolean? = null

    constructor(
        bruker: Persongrunnlag? = null,
        virkFom: Date? = null,
        førsteVirk: Date? = null,
        ytelseType: KravlinjeTypeEnum? = null,
        boddEllerArbeidetIUtlandet: Boolean? = null,
        trygdetidsperioder: MutableList<TTPeriode> = mutableListOf(),
        regelverkType: RegelverkTypeEnum? = null,
        uttaksgradListe: MutableList<Uttaksgrad> = mutableListOf(),
        beregningsvilkarsPeriodeListe: MutableList<BeregningsvilkarPeriode> = mutableListOf(),
        redusertFTTUT: Boolean? = null,
        virkTom: Date? = null,
        beregning: Beregning?
    ) {
        this.bruker = bruker
        this.virkFom = virkFom
        this.førsteVirk = førsteVirk
        this.ytelseType = ytelseType
        this.boddEllerArbeidetIUtlandet = boddEllerArbeidetIUtlandet
        this.trygdetidsperioder = trygdetidsperioder
        this.regelverkType = regelverkType
        this.uttaksgradListe = uttaksgradListe
        this.beregningsvilkarsPeriodeListe = beregningsvilkarsPeriodeListe
        this.redusertFTTUT = redusertFTTUT
        this.virkTom = virkTom
        this.beregning = beregning
    }

    constructor()
}
