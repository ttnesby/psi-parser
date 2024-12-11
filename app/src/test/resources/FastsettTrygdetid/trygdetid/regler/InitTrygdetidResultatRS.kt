package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.RegelverkTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.pensjon.regler.internal.domain.Trygdetid
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset

/**
 * For AP2025 skal bare beregne trygdetid kapittel 20.
 */
class InitTrygdetidResultatRS(
    private val innParametere: TrygdetidParameterType?,
    private val innKapittel20: Boolean?
) : AbstractPensjonRuleset<Unit>() {
    private var regelverk: RegelverkTypeEnum = innParametere?.variable?.regelverkType!!

    override fun create() {

        regel("AP2025") {
            HVIS { innKapittel20!! }
            OG { regelverk == RegelverkTypeEnum.N_REG_N_OPPTJ }
            SÅ {
                log_debug("[HIT] InitTrygdetidResultatRS.AP2025")
                innParametere?.resultat?.trygdetid = null
                innParametere?.resultat?.trygdetidKapittel20 =
                    Trygdetid(regelverkType = RegelverkTypeEnum.N_REG_N_OPPTJ)
            }
            kommentar("For AP2025 skal bare beregne trygdetid kapittel 20.")

        }

        regel("AP2016") {
            HVIS { innKapittel20!! }
            OG { regelverk == RegelverkTypeEnum.N_REG_G_N_OPPTJ }
            SÅ {
                log_debug("[HIT] InitTrygdetidResultatRS.AP2016")
                innParametere?.resultat?.trygdetid =
                    Trygdetid(regelverkType = RegelverkTypeEnum.N_REG_G_OPPTJ)
                innParametere?.resultat?.trygdetidKapittel20 =
                    Trygdetid(regelverkType = RegelverkTypeEnum.N_REG_N_OPPTJ)
            }
            kommentar(
                """For AP2016 skal både beregne trygdetid kapittel 19 og trygdetid kapittel 20.
            """
            )

        }

        regel("AP2011") {
            HVIS { regelverk == RegelverkTypeEnum.N_REG_G_OPPTJ }
            SÅ {
                log_debug("[HIT] InitTrygdetidResultatRS.AP2011")
                innParametere?.resultat?.trygdetid =
                    Trygdetid(regelverkType = RegelverkTypeEnum.N_REG_G_OPPTJ)
                innParametere?.resultat?.trygdetidKapittel20 = null
            }
            kommentar("For AP2011 skal bare beregne trygdetid kapittel 19.")

        }

        regel("GammeltRegelverk") {
            HVIS { "AP2025".harIkkeTruffet() }
            OG { "AP2016".harIkkeTruffet() }
            OG { "AP2011".harIkkeTruffet() }
            SÅ {
                log_debug("[HIT] InitTrygdetidResultatRS.GammeltRegelverk")
                innParametere?.resultat?.trygdetid =
                    Trygdetid(regelverkType = RegelverkTypeEnum.G_REG)
                innParametere?.resultat?.trygdetidKapittel20 = null
            }
            kommentar("Regel for alt annet enn AP2025, AP2016 og AP2011.")

        }

    }
}