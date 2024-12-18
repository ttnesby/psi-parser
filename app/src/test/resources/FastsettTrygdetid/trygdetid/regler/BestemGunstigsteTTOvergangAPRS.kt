package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.ProRataBeregningTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.TrygdetidGarantitypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.settTTResultat
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.merknad.MerknadEnum
import no.nav.pensjon.regler.internal.domain.Trygdetid

/**
 * Ved beregning av AP hvor bruker er minst 67 år og 1 mnd og det er overgang fra uførepensjon skal
 * folketrygdlovens §3-5 6. ledd anvendes.
 * Trygdetiden som er fastsatt for alderspensjonen skal vurderes mot trygdetiden ved siste
 * uføretidspunkt og den største velges.
 */
class BestemGunstigsteTTOvergangAPRS(
    private val innParametere: TrygdetidParameterType,
    private val innTTalder: Trygdetid?,
    private val innTTsisteUFT: Trygdetid?
) : AbstractPensjonRuleset<Unit>() {
    override fun create() {

        regel("OvergangFraKunProrataUP") {
            HVIS { innParametere.variable?.prorataBeregningType != null }
            OG { innParametere.variable?.prorataBeregningType == ProRataBeregningTypeEnum.KUN_EOS }
            SÅ {
                log_debug("[HIT] BestemGunstigsteTTOvergangAPRS.OvergangFraKunProrataUP")
                val resultatTT: Trygdetid = settTTResultat(innParametere, innTTalder!!)
                resultatTT.ttUtlandEos = innTTsisteUFT?.ttUtlandEos
                resultatTT.ttUtlandKonvensjon = innTTsisteUFT?.ttUtlandKonvensjon
                resultatTT.ttUtlandTrygdeavtaler = innTTsisteUFT?.ttUtlandTrygdeavtaler!!
                resultatTT.garantiType = TrygdetidGarantitypeEnum.FT_3_5
                resultatTT.merknadListe.add(MerknadEnum.BestemGunstigsteTTOvergangAPRS__OvergangFraKunProrataUP.lagMerknad())
            }
            kommentar(
                """Bruker hadde ikke rett til folketrygdberegnet UP. Resultat settes til en
            kombinasjon av folketrygd delen fra TTalder
        og avtaleland delen fra TTsisteUFT."""
            )

        }

        regel("TTSisteUFTGunstigst") {
            HVIS { innTTsisteUFT?.tt!! > innTTalder?.tt!! }
            OG { "OvergangFraKunProrataUP".harIkkeTruffet() }
            SÅ {
                log_debug("[HIT] BestemGunstigsteTTOvergangAPRS.TTSisteUFTGunstigst, ${innTTsisteUFT?.tt} > ${innTTalder?.tt}")
                val resultatTT: Trygdetid = settTTResultat(innParametere, innTTsisteUFT!!)
                resultatTT.garantiType = TrygdetidGarantitypeEnum.FT_3_5
                resultatTT.merknadListe.add(MerknadEnum.BestemGunstigsteTTOvergangAPRS__TTSisteUFTGunstigst.lagMerknad())
            }
            kommentar("Trygdetid for siste uføretidspunkt gunstigst")

        }

        regel("TTalderGunstigst") {
            HVIS { innTTsisteUFT?.tt!! <= innTTalder?.tt!! }
            OG { "OvergangFraKunProrataUP".harIkkeTruffet() }
            SÅ {
                log_debug("[HIT] BestemGunstigsteTTOvergangAPRS.TTalderGunstigst, ${innTTsisteUFT?.tt} <= ${innTTalder?.tt}")
                val resultatTT: Trygdetid = settTTResultat(innParametere, innTTalder!!)
                resultatTT.garantiType = TrygdetidGarantitypeEnum.FT_3_5
                resultatTT.merknadListe.add(MerknadEnum.BestemGunstigsteTTOvergangAPRS__TTalderGunstigst.lagMerknad())
            }
            kommentar("Trygdetid for alderspensjon gunstigst")

        }

    }
}