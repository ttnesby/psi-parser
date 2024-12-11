package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.flyter

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleflow
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.overstyrGrunnlagsrolle
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.GrunnlagsrolleEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.finnTTResultat
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.settOppBeregnTrygdetidSisteUFT
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.tilbakestillBeregnTrygdetidSisteUFT
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler.BestemGunstigsteTTOvergangAPRS
import no.nav.pensjon.regler.internal.domain.Trygdetid

class TrygdetidUPtilAPFlyt(
    private val trygdetidParametere: TrygdetidParameterType
) : AbstractPensjonRuleflow() {
    private var TTalder: Trygdetid? = null
    private var TTuføre: Trygdetid? = null

    override var ruleflow: () -> Unit = {

        /**
         * Task: Lagre trygdetid overgang alder
         */
        TTalder = finnTTResultat(trygdetidParametere)
        /**
         * Task: Sett grunnlagsrolle SOKER
         * PK-21066: Regelflyt "TrygdetidUPtilAPFlyt" endres ved at ny funksjon "settGrunnlagsrolle"
        brukes til å overstyre grunnlagsrolle for avdød til å være lik SOKER ved beregning av
        trygdetid for siste uføretidspunkt.
         */
        overstyrGrunnlagsrolle(
            trygdetidParametere.grunnlag?.bruker,
            GrunnlagsrolleEnum.AVDOD,
            GrunnlagsrolleEnum.SOKER
        )
        /**
         * Task: Sett opp beregn trygdetid siste UFT
         */
        settOppBeregnTrygdetidSisteUFT(trygdetidParametere, TTalder)
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
         * Task: Bestem Gunstigste Trygdetid Overgang AP
         */
        BestemGunstigsteTTOvergangAPRS(trygdetidParametere, TTalder, TTuføre).run(this)
        /**
         * Task: Tilbakestill parametere
         */
        tilbakestillBeregnTrygdetidSisteUFT(trygdetidParametere)

    }

}
