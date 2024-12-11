package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.TrygdetidGarantitypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.koder.UføretrygdTilfelleEnum
import no.nav.pensjon.regler.internal.domain.grunnlag.Uforeperiode
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.preg.system.helper.localDate
import no.nav.preg.system.helper.minst
import no.nav.preg.system.helper.toLocalDate

/**
 * I forhold til behandling av poeng i inn/ut-år er det nødvendig å kategorisere UT tilfellet.
 */
class BestemUføretrygdTilfelleRS(
    private val innTrygdetidParam: TrygdetidParameterType?
) : AbstractPensjonRuleset<UføretrygdTilfelleEnum>() {
    private var uføreperiodeListe: List<Uforeperiode> = listOf()

    override fun create() {
        regel("UTovergangAP: Rule<UføretrygdTilfelleEnum>") {
            HVIS { innTrygdetidParam?.variable?.garantiType == TrygdetidGarantitypeEnum.FT_3_5 }
            OG { innTrygdetidParam?.variable?.kapittel20 == false }
            SÅ {
                log_debug("[HIT] BehandlePoengIInnOgUtÅrRS.UTovergangAP")
                RETURNER(UføretrygdTilfelleEnum.UTovergangAP)
            }
            kommentar("Hvis overgang fra UT til AP (kapittel 19) medregnes poeng i inn/ut-år. ")
        }

        regel("FinnUføreperiodeListe: Rule<UføretrygdTilfelleEnum>") {
            HVIS { innTrygdetidParam?.grunnlag?.bruker?.uforeHistorikk != null }
            SÅ {
                uføreperiodeListe =
                    innTrygdetidParam?.grunnlag?.bruker?.uforeHistorikk?.getSortedUforeperiodeListe(false)!!
            }
            kommentar("Hjelperegel for å hente frem sortert liste av uføreperioder for UT saker.")
        }

        regel("UTkonvertert: Rule<UføretrygdTilfelleEnum>") {
            HVIS { innTrygdetidParam?.variable?.garantiType == null }
            OG {
                uføreperiodeListe
                    .minst(1) { it.virk!!.toLocalDate() < localDate(2015, 1, 1) }
            }
            SÅ {
                log_debug("[HIT] BehandlePoengIInnOgUtÅrRS.UTkonvertert")
                RETURNER(UføretrygdTilfelleEnum.UTkonvertertFraUP)
            }
            kommentar("""Hvis det finnes noen uføreperiode med virk før 2015 så er det konvertert fra UP.""")
        }

        regel("UTløpende: Rule<UføretrygdTilfelleEnum>") {
            HVIS { true }
            SÅ {
                log_debug("[HIT] BehandlePoengIInnOgUtÅrRS.UTløpende")
                RETURNER(UføretrygdTilfelleEnum.UTløpende)
            }
            kommentar(
                """Dersom ikke overgang til AP eller konvertert fra UP så er det vanlig løpende
            UT."""
            )
        }
    }
}