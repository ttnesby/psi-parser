package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.flyter.BestemBeregningsmetodeFlyt
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.*
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.klasser.BestemBeregningsmetodeParametere
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.TrygdetidGarantitypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.regler.BestemTrygdetidOgProRataGPRS
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.regler.KunProRataBeregningRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.settTTResultat
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.merknad.MerknadEnum
import no.nav.pensjon.regler.internal.domain.Trygdetid
import no.nav.pensjon.regler.internal.domain.beregning.Beregning
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.pensjon.regler.internal.domain.trygdetid.AnvendtTrygdetid
import no.nav.pensjon.regler.internal.domain.vedtak.VilkarsVedtak
import no.nav.pensjon.regler.util.getOrNull

/**
 * Dersom inngang og eksportgrunnlag er angitt, bestem om vilkår folketrygd var oppfylt eller om det
 * kun ble gitt prorata beregnet uførepensjon.
 */
class BestemGunstigsteTTAvdødRS(
    private val innParametere: TrygdetidParameterType,
    private val innBruker: Persongrunnlag?,
    private val innTTavdød: Trygdetid?,
    private val innTTsisteUFT: Trygdetid?,
) : AbstractPensjonRuleset<Unit>() {
    private var kunProrata: Boolean? = null
    private var ttProRata_UFT: AnvendtTrygdetid? = null
    private var ttProRata_DOD: AnvendtTrygdetid? = null
    private var resultatTT: Trygdetid? = null

    override fun create() {
        regel("BestemKunProrata") {
            HVIS { innBruker?.inngangOgEksportGrunnlag != null }
            SÅ {
                val p = Persongrunnlag(
                    inngangOgEksportGrunnlag = innBruker?.inngangOgEksportGrunnlag,
                    vilkarsVedtak = VilkarsVedtak(kravlinjeType = KravlinjeTypeEnum.UP),
                    trygdetid = innTTsisteUFT
                )
                val b = Beregning(tt_anv = innTTsisteUFT?.tt!!)
                kunProrata =
                    KunProRataBeregningRS(b.merknadListe, p, false, b.tt_anv, innParametere.variable?.ytelseType).run(
                        this
                    )

                log_debug("[HIT] BestemGunstigsteTTAvdødRS.BestemKunProrata, kunProrata = $kunProrata")
            }
            kommentar(
                """Dersom inngang og eksportgrunnlag er angitt, bestem om vilkår folketrygd var
            oppfylt eller om det kun ble gitt prorata beregnet uførepensjon."""
            )

        }
        regel("BestemProrataTrygdetid") {
            HVIS { innBruker?.inngangOgEksportGrunnlag != null }
            OG { kunProrata == true }
            SÅ {
                log_debug("[HIT] BestemGunstigsteTTAvdødRS.BestemProrataTrygdetid")
                innBruker?.trygdetid = innParametere.resultat?.trygdetid
                innBruker?.trygdetidKapittel20 = innParametere.resultat?.trygdetidKapittel20
                val b = Beregning()
                val param: BestemBeregningsmetodeParametere = lagBestemBeregningsmetodeParametere(
                    innBruker,
                    b,
                    innParametere.grunnlag?.virkFom,
                    innParametere.grunnlag?.regelverkType,
                    innParametere.variable?.ytelseType
                )
                BestemBeregningsmetodeFlyt(param).run(this)
                settBestemBeregningsmetodeParametrePåBeregning(b, param)
                innBruker?.trygdetid = null
                innBruker?.trygdetidKapittel20 = null
                val p = Persongrunnlag()
                // Trygdetid og prorata faktor for siste UFT
                p.trygdetid = innTTsisteUFT
                ttProRata_UFT = BestemTrygdetidOgProRataGPRS(
                    b,
                    p,
                    b.beregningsMetode,
                    b.avtaleBeregningsmetode,
                    b.merknadListe,
                    p.trygdetid,
                    innParametere.variable?.ytelseType
                ).run(this).getOrNull()
                // Trygdetid og prorata faktor for dødsdato
                p.trygdetid = innTTavdød
                ttProRata_DOD = BestemTrygdetidOgProRataGPRS(
                    b,
                    p,
                    b.beregningsMetode,
                    b.avtaleBeregningsmetode,
                    b.merknadListe,
                    p.trygdetid,
                    innParametere.variable?.ytelseType
                ).run(this).getOrNull()
            }
            kommentar(
                """Bestem trygdetid og pro rata faktor for siste uføretidspunkt og for
            dødsdato"""
            )

        }
        regel("TTSisteUFTGunstigst") {
            HVIS { innBruker?.inngangOgEksportGrunnlag == null }
            OG { innTTsisteUFT?.tt!! > innTTavdød?.tt!! }
            SÅ {
                log_debug("[HIT] BestemGunstigsteTTAvdødRS.TTSisteUFTGunstigst, ${innTTsisteUFT?.tt} > ${innTTavdød?.tt}")
                resultatTT = settTTResultat(innParametere, innTTsisteUFT!!)
                innParametere.resultat?.trygdetidAlternativ = innTTavdød
                resultatTT?.merknadListe?.add(MerknadEnum.BestemGunstigsteTTAvdodRS__TTSisteUFTGunstigst.lagMerknad())
            }
            kommentar("Trygdetid for siste uføretidspunkt gunstigst")

        }
        regel("TTSisteUFTGunstigstOgKravFToppfylt") {
            HVIS { innBruker?.inngangOgEksportGrunnlag != null }
            OG { kunProrata == false }
            OG { innTTsisteUFT?.tt!! > innTTavdød?.tt!! }
            SÅ {
                log_debug("[HIT] BestemGunstigsteTTAvdødRS.TTSisteUFTGunstigstOgKravFToppfylt, ${innTTsisteUFT?.tt} > ${innTTavdød?.tt}")
                resultatTT = settTTResultat(innParametere, innTTsisteUFT!!)
                innParametere.resultat?.trygdetidAlternativ = innTTavdød
                resultatTT?.merknadListe?.add(MerknadEnum.BestemGunstigsteTTAvdodRS__TTSisteUFTGunstigstOgKravFToppfylt.lagMerknad())
            }
            kommentar(
                """Trygdetid for siste uføretidspunkt gunstigst og inngangsvilkår folketrygd
            oppfylt"""
            )

        }
        regel("TTDødsdatoGunstigst") {
            HVIS { innBruker?.inngangOgEksportGrunnlag == null }
            OG { innTTsisteUFT?.tt!! <= innTTavdød?.tt!! }
            SÅ {
                log_debug("[HIT] BestemGunstigsteTTAvdødRS.TTDødsdatoGunstigst, ${innTTsisteUFT?.tt} <= ${innTTavdød?.tt}")
                resultatTT = settTTResultat(innParametere, innTTavdød!!)
                innParametere.resultat?.trygdetidAlternativ = innTTsisteUFT
                resultatTT?.merknadListe?.add(MerknadEnum.BestemGunstigsteTTAvdodRS__TTDodsdatoGunstigst.lagMerknad())
            }
            kommentar("Trygdetid for dødsdato gunstigst")

        }
        regel("TTDødsdatoGunstigstOgKravFToppfylt") {
            HVIS { innBruker?.inngangOgEksportGrunnlag != null }
            OG { kunProrata == false }
            OG { innTTsisteUFT?.tt!! <= innTTavdød?.tt!! }
            SÅ {
                log_debug("[HIT] BestemGunstigsteTTAvdødRS.TTDødsdatoGunstigstOgKravFToppfylt, ${innTTsisteUFT?.tt} <= ${innTTavdød?.tt}")
                resultatTT = settTTResultat(innParametere, innTTavdød!!)
                innParametere.resultat?.trygdetidAlternativ = innTTsisteUFT
                resultatTT?.merknadListe?.add(MerknadEnum.BestemGunstigsteTTAvdodRS__TTDodsdatoGunstigstOgKravFToppfylt.lagMerknad())
            }
            kommentar("Trygdetid for dødsdato gunstigst og inngangsvilkår folketrygd oppfylt")

        }
        regel("TTSisteUFTGunstigstProrata") {
            HVIS { innBruker?.inngangOgEksportGrunnlag != null }
            OG { kunProrata == true }
            OG {
                ttProRata_UFT?.tt_anv!! * finnProrata(ttProRata_UFT) > ttProRata_DOD?.tt_anv!! * finnProrata(
                    ttProRata_DOD
                )
            }
            SÅ {
                log_debug(
                    "[HIT] BestemGunstigsteTTAvdødRS.TTSisteUFTGunstigstProrata, ${ttProRata_UFT?.tt_anv} * ${
                        finnProrataTeller(
                            ttProRata_UFT
                        )
                    }/${finnProrataNevner(ttProRata_UFT)} > ${ttProRata_DOD?.tt_anv} * ${finnProrataTeller(ttProRata_DOD)}/${
                        finnProrataNevner(
                            ttProRata_DOD
                        )
                    }"
                )
                resultatTT = settTTResultat(innParametere, innTTsisteUFT!!)
                innParametere.resultat?.trygdetidAlternativ = innTTavdød
                resultatTT?.merknadListe?.add(MerknadEnum.BestemGunstigsteTTAvdodRS__TTSisteUFTGunstigstProrata.lagMerknad())
            }
            kommentar("""Trygdetid for siste uføretidspunkt gunstigst og inngangsvilkår folketrygd ikke oppfylt""")

        }
        regel("TTDødsdatoGunstigstProrata") {
            HVIS { innBruker?.inngangOgEksportGrunnlag != null }
            OG { kunProrata == true }
            OG {
                ttProRata_UFT?.tt_anv!! * finnProrata(ttProRata_UFT) <= ttProRata_DOD?.tt_anv!! * finnProrata(
                    ttProRata_DOD
                )
            }
            SÅ {
                log_debug("[HIT] BestemGunstigsteTTAvdødRS.TTDødsdatoGunstigstProrata")
                log_debug("[   ]    ttProRata_UFT.tt_anv * finnProrataTeller(ttProRata_UFT) / finnProrataNevner(ttProRata_UFT) <= ttProRata_DOD.tt_anv * finnProrataTeller(ttProRata_DOD) / finnProrataNevner(ttProRata_DOD)")
                log_debug(
                    "[   ]    ${ttProRata_UFT?.tt_anv} * ${finnProrataTeller(ttProRata_UFT)}/${
                        finnProrataNevner(
                            ttProRata_UFT
                        )
                    } <= ${ttProRata_DOD?.tt_anv} * ${finnProrataTeller(ttProRata_DOD)}/${
                        finnProrataNevner(
                            ttProRata_DOD
                        )
                    }"
                )
                resultatTT = settTTResultat(innParametere, innTTavdød!!)
                innParametere.resultat?.trygdetidAlternativ = innTTsisteUFT
                resultatTT?.merknadListe?.add(MerknadEnum.BestemGunstigsteTTAvdodRS__TTDodsdatoGunstigstProrata.lagMerknad())
            }
            kommentar("Trygdetid for dødsdato gunstigst og inngangsvilkår folketrygd ikke oppfylt")

        }
        regel("SettGarantitype") {
            HVIS { resultatTT != null }
            SÅ {
                resultatTT?.garantiType = TrygdetidGarantitypeEnum.FT_3_7
            }
        }

    }
}
