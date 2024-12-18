package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.AvtaleDatoEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.AvtaleLandEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.AvtaletypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.lagTrygdetidTrygdeavtale
import no.nav.domain.pensjon.regler.repository.merknad.MerknadEnum
import no.nav.pensjon.regler.internal.domain.TTPeriode
import no.nav.pensjon.regler.internal.domain.TTUtlandTrygdeavtale
import no.nav.pensjon.regler.internal.domain.Trygdetid
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset

/**
 * Ref. CR 209496. Skal gi merknad dersom visse kombinasjoner av trygdetid/trygdeavtale i
 * Sveits/EØS.
 */
class KontrollerTTPeriodeSveitsRS(
    private val innBruker: Persongrunnlag?,
    private val innPeriode: TTPeriode?,
    private val innTrygdetid: Trygdetid?
) : AbstractPensjonRuleset<Unit>() {
    private var periodeEØS: Boolean = false

    private var land: AvtaleLandEnum? = null

    override fun create() {
        regel("TrygdeavtaleSveits") {
            HVIS { innBruker?.trygdeavtale != null }
            OG { innBruker?.trygdeavtale?.avtaleType != null }
            OG { innBruker?.trygdeavtale?.avtaleType == AvtaletypeEnum.CHE }
            SÅ {
            }

        }

        regel("TrygdeavtaleEøsAvtaledatoFør2010") {
            HVIS { innBruker?.trygdeavtale != null }
            OG { innBruker?.trygdeavtale?.avtaleType != null }
            OG { innBruker?.trygdeavtale?.avtaleType == AvtaletypeEnum.EOS_NOR }
            OG {
                !(innBruker?.trygdeavtale?.avtaledato != null
                        && innBruker.trygdeavtale?.avtaledato == AvtaleDatoEnum.EOS2010)
            }
        }

        regel("TrygdeavtaleIkkeRelevant") {
            HVIS { "TrygdeavtaleSveits".harIkkeTruffet() }
            OG { "TrygdeavtaleEøsAvtaledatoFør2010".harIkkeTruffet() }
        }

        regel("BestemTTperiodeLand") {
            HVIS { "TrygdeavtaleIkkeRelevant".harIkkeTruffet() }
            OG  { innPeriode?.land != null }
            SÅ {
                land = AvtaleLandEnum.valueOf(innPeriode?.land!!.name)
            }
            kommentar("Bestem periode land")

        }

        regel("BestemPeriodeEØS") {
            HVIS { "TrygdeavtaleIkkeRelevant".harIkkeTruffet() }
            OG { land != null }
            SÅ {
                periodeEØS = EøsKonvensjonsLand(land!!)
            }
            kommentar("Bestem om land er med i EØS.")

        }

        regel("TrygdeavtaleSveitsOgTrygdetidAnnetEØSland") {
            HVIS { "TrygdeavtaleSveits".harTruffet() }
            OG { periodeEØS }
            OG { land != AvtaleLandEnum.CHE }
            OG { land != AvtaleLandEnum.NOR }
            SÅ {
                log_debug("[HIT] KontrollerTTPeriodeSveitsRS.TrygdeavtaleSveitsOgTrygdetidAnnetEØSland")
                val tt: TTUtlandTrygdeavtale = lagTrygdetidTrygdeavtale(innTrygdetid, AvtaleLandEnum.CHE)
                tt.merknadListe.add(MerknadEnum.KontrollerTTPeriodeSveitsRS__TrygdeavtaleSveitsOgTrygdetidAnnetEOSland.lagMerknad())
            }
            kommentar(
                """Hvis trygdeavtale er Sveits og det er angitt trygdetid i Sveits og i andre EØS
            land
        Så gis merknad"""
            )

        }

        regel("TrygdeavtaleEøSAvtaledatoFør2010ogTrygdetidSveits") {
            HVIS { "TrygdeavtaleEøsAvtaledatoFør2010".harTruffet() }
            OG { land == AvtaleLandEnum.CHE }
            SÅ {
                log_debug("[HIT] KontrollerTTPeriodeSveitsRS.TrygdeavtaleEøSAvtaledatoFør2010ogTrygdetidSveits")
                val tt: TTUtlandTrygdeavtale = lagTrygdetidTrygdeavtale(innTrygdetid, AvtaleLandEnum.CHE)
                tt.merknadListe.add(MerknadEnum.KontrollerTTPeriodeSveitsRS__TrygdeavtaleEoSAvtaledatoFor2010ogTrygdetidSveits.lagMerknad())
            }
            kommentar(
                """Hvis trygdeavtale er EØS med avtaledato før 2010 og det er angitt trygdetid i
            Sveits
        Så gis merknad."""
            )

        }

    }
}