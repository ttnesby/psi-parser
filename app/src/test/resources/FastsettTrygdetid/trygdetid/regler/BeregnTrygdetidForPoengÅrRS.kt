package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.RegelverkTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.RegelverkTypeEnum.*
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.antallPoengår
import no.nav.pensjon.regler.internal.domain.Trygdetid
import no.nav.pensjon.regler.internal.domain.beregning.Beregning
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.preg.system.helper.plus
import no.nav.preg.system.helper.år
import java.lang.Math.*
import java.util.*

class BeregnTrygdetidForPoengÅrRS(
    private val innBruker: Persongrunnlag,
    private val innBeregning: Beregning,
    private val innRegelverktypeEnum: RegelverkTypeEnum,
    private val innVirk: Date,
    private val innTrygdetid: Trygdetid
) : AbstractPensjonRuleset<Unit>() {
    private var årFyller67: Int = (innBruker.fodselsdato!! + 67.år).år
    private var årFyller69: Int = (innBruker.fodselsdato!! + 69.år).år
    private var årFyller75: Int = (innBruker.fodselsdato!! + 75.år).år
    private var poengår: Int = 0

    override fun create() {

        regel("FinnPoengår_AP1967") {
            HVIS { innRegelverktypeEnum == G_REG }
            OG { innBeregning.tp?.spt?.poengrekke != null }
            OG { poengår == 0 }
            SÅ {
                poengår = antallPoengår(innBeregning.tp?.spt?.poengrekke!!.poengtallListe, årFyller67, årFyller69)
                log_debug("[HIT] BeregnTrygdetidForPoengÅrRS.FinnPoengår_AP1967, $poengår poengår fra $årFyller67 til $årFyller69")
            }
            kommentar("Alderspensjon 1967: tell opp antall poengår fra år fyller 67 til år fyller 69.")
        }

        regel("FinnPoengår_AP2011") {
            HVIS { innRegelverktypeEnum != G_REG }
            OG { innBeregning.tp?.spt?.poengrekke != null }
            OG { poengår == 0 }
            OG { innBruker.vilkarsVedtak?.kravlinjeType == KravlinjeTypeEnum.AP }
            OG { innVirk.år - 2 >= årFyller67 }
            SÅ {
                var sluttar: Int = min(årFyller75, innVirk.år - 2)
                sluttar = max(årFyller67, sluttar)
                poengår = antallPoengår(innBeregning.tp?.spt?.poengrekke!!.poengtallListe, årFyller67, sluttar)
                log_debug("[HIT] BeregnTrygdetidForPoengÅrRS.FinnPoengår_AP2011, $poengår poengår fra $årFyller67 til $sluttar")
            }
            kommentar(
                """Alderspensjon 2011: Tell opp antall poengår fra år brukeren fyller 67 frem til
                og med 2 år før virkningsåret, 
                maks til og med året fyller 75.
                PEN-4947: Lagt til betingelse om at virk.year - 2 >= årFyller67."""
            )
        }

        regel("FinnPoengår_GJP") {
            HVIS { innRegelverktypeEnum != G_REG }
            OG { innBeregning.tp?.spt?.poengrekke != null }
            OG { poengår == 0 }
            OG { innBruker.vilkarsVedtak?.kravlinjeType == KravlinjeTypeEnum.GJP }
            SÅ {
                var sluttar: Int = min(årFyller75, innBruker.dodsdato!!.år - 1)
                sluttar = max(årFyller67, sluttar)
                poengår = antallPoengår(innBeregning.tp?.spt?.poengrekke!!.poengtallListe, årFyller67, sluttar)
                log_debug("[HIT] BeregnTrygdetidForPoengÅrRS.FinnPoengår_GJP, $poengår poengår fom $årFyller67 tom $sluttar")
            }
            kommentar("Når det beregnes gjenlevendepensjon godskrives trygdetid for poengår tom året før dødstidspunktet")
        }
        regel("FinnPoengår_GJR") {
            HVIS { innRegelverktypeEnum != G_REG }
            OG { innBeregning.tp?.spt?.poengrekke != null }
            OG { poengår == 0 }
            OG { innBruker.vilkarsVedtak?.kravlinjeType == KravlinjeTypeEnum.GJR }
            SÅ {
                var sluttar: Int = min(årFyller75, innBruker.dodsdato!!.år - 1)
                sluttar = min(sluttar, innVirk.år - 2)
//                sluttar = max(årFyller67, sluttar)
                poengår = antallPoengår(innBeregning.tp?.spt?.poengrekke!!.poengtallListe, årFyller67, sluttar)
                log_debug("[HIT] BeregnTrygdetidForPoengÅrRS.FinnPoengår_GJR, $poengår poengår fom $årFyller67 tom $sluttar")
            }
            kommentar("Når det beregnes gjenlevenderett godskrives trygdetid for poengår tom året før dødstidspunktet")
        }
        regel("PoengÅr67til70") {
            HVIS { innRegelverktypeEnum == G_REG }
            OG { poengår > 0 }
            SÅ {
                log_debug("[HIT] BeregnTrygdetidForPoengÅrRS.PoengÅr67til70")
                log_debug("[   ]    tt_67_70 = $poengår")
                innTrygdetid.tt_67_70 = poengår
            }
            kommentar(
                """Alderspensjon 1967: Opptjening av pensjonspoeng i det året medlemmet fyller
            67, 68 og 69 år regnes også som trygdetid.
        §3-5 tredje ledd."""
            )
        }

        regel("PoengÅr67til75") {
            HVIS { innRegelverktypeEnum != G_REG }
            OG { poengår > 0 }
            SÅ {
                log_debug("[HIT] BeregnTrygdetidForPoengÅrRS.PoengÅr67til75, tt_67_75 = $poengår")
                innTrygdetid.tt_67_75 = poengår
            }
            kommentar(
                """Alderspensjon 2011: Opptjening av pensjonspoeng i det året medlemmet fyller 67
            til 75 år regnes også som trygdetid."""
            )

        }
    }
}