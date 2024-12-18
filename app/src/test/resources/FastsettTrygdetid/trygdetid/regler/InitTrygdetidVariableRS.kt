package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.RegelverkTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidVariable
import java.util.*

/**
 * Setter førstevirk i trygdetidsparametere.
 */
class InitTrygdetidVariableRS(
    private val innParametere: TrygdetidParameterType?,
    private val innFørsteVirk: Date?,
    private val innKapittel20: Boolean?
) : AbstractPensjonRuleset<Unit>() {
    override fun create() {

        regel("OppdaterFørsteVirk") {
            HVIS { innFørsteVirk != null }
            SÅ {
                innParametere?.grunnlag?.førsteVirk = innFørsteVirk
            }
            kommentar("Setter førstevirk i trygdetidsparametere.")
        }

        regel("OppdaterTrygdetidsperioder") {
            HVIS { innParametere?.grunnlag?.trygdetidsperioder!!.isEmpty() }
            SÅ {
                innParametere?.grunnlag?.trygdetidsperioder = (innParametere?.grunnlag?.bruker?.trygdetidPerioder)!!
            }
            kommentar("Setter trygdetidsperioder i trygdetidsparametere.")

        }

        regel("InitVariable") {
            HVIS { innParametere?.variable == null }
            SÅ {
                innParametere?.variable = TrygdetidVariable(
                    ytelseType = innParametere?.grunnlag?.ytelseType,
                    regelverkType = innParametere?.grunnlag?.regelverkType,
                    ttFaktiskBeregnes = true,
                    kapittel20 = innKapittel20,
                    konvertertTilUT = false
                )
            }
            kommentar("Initialiserer trygdetid variable.")
        }
    }
}