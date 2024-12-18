package no.nav.domain.pensjon.regler.repository.tjeneste.fastsetttrygdetid.function

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleService
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidGrunnlag
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidResultat
import no.nav.domain.pensjon.regler.repository.tjeneste.fastsetttrygdetid.flyter.StartTrygdetidFlyt
import no.nav.pensjon.regler.internal.domain.Pakkseddel
import no.nav.pensjon.regler.internal.to.TrygdetidRequest
import no.nav.pensjon.regler.internal.to.TrygdetidResponse

class FastsettTrygdetidService(
    private val innTrygdetidRequest: TrygdetidRequest
) : AbstractPensjonRuleService<TrygdetidResponse>(innTrygdetidRequest) {
    override val ruleService: () -> TrygdetidResponse = {
        log_debug("[FUN] startFastsettTrygdetid")

        val trygdetidParametere = TrygdetidParameterType(
            grunnlag = TrygdetidGrunnlag(
                bruker = innTrygdetidRequest.persongrunnlag,
                boddEllerArbeidetIUtlandet = innTrygdetidRequest.boddEllerArbeidetIUtlandet,
                førsteVirk = innTrygdetidRequest.brukerForsteVirk,
                virkFom = innTrygdetidRequest.virkFom,
                virkTom = innTrygdetidRequest.virkTom,
                ytelseType = innTrygdetidRequest.hovedKravlinjeType,
                regelverkType = innTrygdetidRequest.regelverkType,
                uttaksgradListe = innTrygdetidRequest.uttaksgradListe,
                beregningsvilkarsPeriodeListe = innTrygdetidRequest.sortedBeregningssvilkarPeriodeListe(),
                redusertFTTUT = innTrygdetidRequest.redusertFTTUT,
                beregning = null
            )
        )

        /**
         * Utled regelverkstype hvis ikke satt i request.
         * Default er G_REG.
         */
        if (trygdetidParametere.grunnlag?.regelverkType == null
            && trygdetidParametere.grunnlag?.bruker != null
            && trygdetidParametere.grunnlag?.ytelseType != null) {
            trygdetidParametere.grunnlag!!.regelverkType = utledRegelverkstype(
                trygdetidParametere.grunnlag?.bruker!!,
                trygdetidParametere.grunnlag?.ytelseType!!
            )
        }

        trygdetidParametere.resultat = TrygdetidResultat(pakkseddel = Pakkseddel())

        // Kjør reglene
        StartTrygdetidFlyt(trygdetidParametere).run(this)

        TrygdetidResponse(
            trygdetid = trygdetidParametere.resultat?.trygdetid,
            trygdetidAlternativ = trygdetidParametere.resultat?.trygdetidAlternativ,
            trygdetidKapittel20 = trygdetidParametere.resultat?.trygdetidKapittel20,
            pakkseddel = trygdetidParametere.resultat?.pakkseddel!!
        )
    }
}
