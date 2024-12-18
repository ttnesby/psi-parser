package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.RegelverkTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset

/**
 * Hvis regelverktype AP2025 så er det kapittel 20
 */
class BestemTTKapittel20RS(
    private val innYtelseType: KravlinjeTypeEnum?,
    private val innRegelverkType: RegelverkTypeEnum?
) : AbstractPensjonRuleset<Boolean>() {
    private var retval: Boolean = false

    override fun create() {

        regel("AP2025") {
            HVIS { innYtelseType == KravlinjeTypeEnum.AP }
            OG { innRegelverkType == RegelverkTypeEnum.N_REG_N_OPPTJ }
            SÅ {
                retval = true
            }
            kommentar("Hvis regelverktype AP2025 så er det kapittel 20")

        }

        regel("AP2016") {
            HVIS { innYtelseType == KravlinjeTypeEnum.AP }
            OG { innRegelverkType == RegelverkTypeEnum.N_REG_G_N_OPPTJ }
            SÅ {
                retval = true
            }
            kommentar("Hvis regelverktype AP2016 så er det kapittel 20")

        }

        regel("AFPprivat2025") {
            HVIS { innYtelseType == KravlinjeTypeEnum.AFP_PRIVAT }
            OG { innRegelverkType == RegelverkTypeEnum.N_REG_N_OPPTJ }
            SÅ {
                retval = true
            }
            kommentar("Hvis AFP privat og regelverktype 2025 så er det kapittel 20")

        }

        regel("AFPprivat2016") {
            HVIS { innYtelseType == KravlinjeTypeEnum.AFP_PRIVAT }
            OG { innRegelverkType == RegelverkTypeEnum.N_REG_G_N_OPPTJ }
            SÅ {
                retval = true
            }
            kommentar("Hvis AFP privat og regelverktype 2016 så er det kapittel 20")

        }

        regel("ReturRegel") {
            HVIS { true }
            SÅ {
                RETURNER(retval)
            }
            kommentar("Returner om det er kapittel 20")

        }

    }
}