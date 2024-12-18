package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.GrunnlagsrolleEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidVariable
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.preg.system.helper.*
import java.util.*

class BestemFaktiskTTRegnesTilAP1967RS(
    private val innParametere: TrygdetidVariable?,
    private val innBruker: Persongrunnlag?,
    private val innFørsteVirk: Date?
) : AbstractPensjonRuleset<Unit>() {

    private var utgangÅrFyller66: Date = date((innBruker?.fodselsdato!! + 66.år).år, 12, 31)

    override fun create() {

        regel("FørstevirkEtter1991") {
            HVIS { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { innFørsteVirk!!.toLocalDate() >= localDate(1991, 1, 1) }
            SÅ {
                innParametere?.ttFaktiskRegnesTil = utgangÅrFyller66
                log_debug("[HIT] BestemFaktiskTTRegnesTilAP1967RS.AP1967FørstevirkEtter1991, TT til ${innParametere?.ttFaktiskRegnesTil}")
            }
            kommentar(
                """Hvis AP1967 og førstevirk etter 1991 så regnes faktisk trygdetid frem til
            utgangen av det året pensjonisten fyller 66 år."""
            )

        }

        regel("AP1967FørstevirkFør1991") {
            HVIS { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { innFørsteVirk!!.toLocalDate() < localDate(1991, 1, 1) }
            OG { innFørsteVirk!!.toLocalDate() >= localDate(1973, 1, 1) }
            SÅ {
                innParametere?.ttFaktiskRegnesTil = (innBruker?.fodselsdato)!! + 67.år
                log_debug("[HIT] BestemFaktiskTTRegnesTilAP1967RS.AP1967FørstevirkFør1991, TT til ${innParametere?.ttFaktiskRegnesTil}")
            }
            kommentar(
                """Hvis AP1967 og førstevirk før 1991 så regnes faktisk trygdetid frem til den
            dagen pensjonisten fyller 67 år."""
            )

        }

        regel("AP1967FørstevirkFør1973") {
            HVIS { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { innFørsteVirk!!.toLocalDate() < localDate(1973, 1, 1) }
            SÅ {
                innParametere?.ttFaktiskRegnesTil = (innBruker?.fodselsdato)!! + 70.år
                log_debug("[HIT] BestemFaktiskTTRegnesTilAP1967RS.AP1967FørstevirkFør1973, TT til ${innParametere?.ttFaktiskRegnesTil}")
            }
            kommentar(
                """Hvis AP1967 og førstevirk før 1973 så regnes faktisk trygdetid frem til den
            dagen pensjonisten fyller 70 år."""
            )
        }
    }
}