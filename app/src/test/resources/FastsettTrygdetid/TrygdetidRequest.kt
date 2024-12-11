package no.nav.pensjon.regler.internal.to

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.RegelverkTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.pensjon.regler.internal.domain.beregning2011.BeregningsvilkarPeriode
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.pensjon.regler.internal.domain.grunnlag.Uttaksgrad
import java.util.*

/** Fjernet setter for uttaksgradListe pga custom logikk **/
class TrygdetidRequest(
    /**
     * Virkningstidspunktets fom. for �nsket ytelse.
     */
    var virkFom: Date? = null,

    /**
     * Tom for trygdetiden som skal beregnes. Kun for AP2011, AP2016 og AP2025.
     */
    var virkTom: Date? = null,

    /**
     * F�rste virkningstidspunkt,denne m� v�re satt dersom personen er SOKER i persongrunnlaget.
     */
    var brukerForsteVirk: Date? = null,

    /**
     * Type ytelse (AP,UP osv)
     */
    var hovedKravlinjeType: KravlinjeTypeEnum? = null,

    /**
     * Persongrunnlag for personen.
     * Dersom ytelsesType er UP m� uforegrunnlag og uforehistorikk v�re utfylt.
     */
    var persongrunnlag: Persongrunnlag? = null,

    /**
     * Angir om personen har bodd eller arbeidet i utlandet.
     */
    var boddEllerArbeidetIUtlandet: Boolean = false,

    /**
     * Regelverktype bestemmer om trygdetid skal regnes etter gamle eller nye regler.
     */
    var regelverkType: RegelverkTypeEnum? = null,

    var uttaksgradListe: MutableList<Uttaksgrad> = mutableListOf(),

    var redusertFTTUT: Boolean? = null,
    /**
     * Liste av beregningsvilkarPerioder, p�krevd ved uf�retrygd.
     */
    var beregningsvilkarPeriodeListe: MutableList<BeregningsvilkarPeriode> = mutableListOf()
) : ServiceRequest() {

    constructor(
        virkFom: Date?,
        brukerForsteVirk: Date?,
        ytelsesType: KravlinjeTypeEnum?,
        persongrunnlag: Persongrunnlag?,
        boddEllerArbeidetIUtlandet: Boolean,
        regelverkType: RegelverkTypeEnum?,
        uttaksgradListe: MutableList<Uttaksgrad> = mutableListOf()
    ) : this() {
        this.virkFom = virkFom
        this.brukerForsteVirk = brukerForsteVirk
        this.hovedKravlinjeType = ytelsesType
        this.persongrunnlag = persongrunnlag
        this.boddEllerArbeidetIUtlandet = boddEllerArbeidetIUtlandet
        this.regelverkType = regelverkType
        this.uttaksgradListe = uttaksgradListe
    }

    /**
     * Sorterer p� nyeste fomDato - denne blir uttaksgradListe.get(0)
     */
    private fun sorterUttaksgradListe() {
        Collections.sort(uttaksgradListe, Collections.reverseOrder())
        return
    }

    fun sortertUttaksgradListe(): Array<Uttaksgrad> {
        sorterUttaksgradListe()
        return uttaksgradListe.toTypedArray()
    }

    fun sortedBeregningssvilkarPeriodeListe(): MutableList<BeregningsvilkarPeriode> {
        val sortedBvp = ArrayList(beregningsvilkarPeriodeListe)
        sortedBvp.sort()
        return sortedBvp
    }
}