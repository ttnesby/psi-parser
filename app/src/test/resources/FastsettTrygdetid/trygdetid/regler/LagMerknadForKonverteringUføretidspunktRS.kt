package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.merknad.MerknadEnum
import no.nav.pensjon.regler.internal.domain.Merknad
import no.nav.pensjon.regler.internal.domain.Trygdetid
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.preg.system.helper.formatToString
import java.util.*

/**
 * NAV ønsket opprinnelig å vite detaljer om delsummer av faktisk trygdetid før og etter
 * konverteringstidspunkt.
 * Dette var basert på en antakelse om at disse delsummene var avrundet etter forskjellige regler,
 * og ble bevart som separate delsummer i PREG.
 * Da dette ikke er tilfelle, gikk de for en mer generisk variant av merknaden i stedet.
 *
 * Den indikerer at ved beregning av trygdetid i uføretrygdsaker som er konvertert fra uførepensjon,
 * vil poeng i inn- og utår kun medregnes i perioden frem til konverteringsuføretidspunktet.
 */
class LagMerknadForKonverteringUføretidspunktRS(
    private val innTrygdetid: Trygdetid?,
    private val innDatoKonverteringsUFT: Date?
) : AbstractPensjonRuleset<Unit>() {
    override fun create() {

        regel("LagMerknadOmPoengIInnOgUtaarForUPKonvertertTilUT") {
            HVIS { innDatoKonverteringsUFT != null }
            SÅ {
                log_debug("[HIT] LagMerknadForKonverteringUføretidspunktRS.LagMerknadOmPoengIInnOgUtaarForUPKonvertertTilUT")
                val tempMerknad: Merknad =
                    MerknadEnum.LagMerknadForKonverteringUføretidspunktRS__LagMerknadOmPoengIInnOgUtaarForUPKonvertertTilUT.lagMerknad(
                        mutableListOf(innDatoKonverteringsUFT?.formatToString(".")!!)
                    )
                innTrygdetid?.merknadListe?.add(tempMerknad)
            }
        }
    }
}