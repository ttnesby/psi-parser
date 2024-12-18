package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.GrunnlagsrolleEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.TrygdetidGarantitypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidVariable
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset

/**
 * Regelsett bestemmer om framtidig trygdetid skal beregnes eller ikke. Framtidig trygdetid
 * beregnes bare for bestemte kombinasjoner av ytelsetype og grunnlagsrolle.
 */
class BestemFramtidigTTBeregnesRS(
    private val innBruker: Persongrunnlag?,
    private val innParametere: TrygdetidVariable?
) : AbstractPensjonRuleset<Boolean>() {
    private var fttBeregnes: Boolean = false

    override fun create() {

        regel("FttBeregnesForUP") {
            HVIS { innParametere?.ytelseType == KravlinjeTypeEnum.UP }
            OG { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            SÅ {
                log_debug("[HIT] BestemFramtidigTTBeregnesRS.FttBeregnesForUP")
                fttBeregnes = true
            }
            kommentar("Framtidig trygdetid beregnes for søker ved uførepensjon.")
        }

        regel("FttBeregnesForUT") {
            HVIS { innParametere?.ytelseType == KravlinjeTypeEnum.UT }
            OG { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            SÅ {
                log_debug("[HIT] BestemFramtidigTTBeregnesRS.FttBeregnesForUT")
                fttBeregnes = true
            }
            kommentar("Framtidig trygdetid beregnes for søker ved uføretrygd.")
        }

        regel("FttBeregnesForUPAvdød") {
            HVIS { innParametere?.ytelseType == KravlinjeTypeEnum.UP }
            OG { innParametere?.garantiType == TrygdetidGarantitypeEnum.FT_3_7 }
            OG { innParametere?.sisteUFT != null }
            SÅ {
                log_debug("[HIT] BestemFramtidigTTBeregnesRS.FttBeregnesForUPAvdød")
                fttBeregnes = true
            }
            kommentar(
                """Framtidig trygdetid beregnes for avdød med uførehistorikk og hvor siste
            uføretidspunkt er bestemt for ytelsestype UP"""
            )
        }

        regel("FttBeregnesForUTAvdød") {
            HVIS { innParametere?.ytelseType == KravlinjeTypeEnum.UT }
            OG { innParametere?.garantiType == TrygdetidGarantitypeEnum.FT_3_7 }
            OG { innParametere?.sisteUFT != null }
            SÅ {
                log_debug("[HIT] BestemFramtidigTTBeregnesRS.FttBeregnesForUTAvdød")
                fttBeregnes = true
            }
            kommentar(
                """Framtidig trygdetid beregnes for avdød med uførehistorikk og hvor siste
            uføretidspunkt er bestemt for ytelsestype UT"""
            )
        }

        regel("FttBeregnesForGJP") {
            HVIS { innParametere?.ytelseType == KravlinjeTypeEnum.GJP }
            OG { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.AVDOD }
            OG { innBruker?.dodsdato!! < innParametere?.ttFaktiskMaksGrense }
            SÅ {
                log_debug("[HIT] BestemFramtidigTTBeregnesRS.FttBeregnesForGJP")
                fttBeregnes = true
            }
            kommentar(
                """Framtidig trygdetid beregnes for avdød ved pensjon til gjenlevende, forutsatt
            at dødsdato er før overgang alderspensjon."""
            )
        }

        regel("FttBeregnesForGJR") {
            HVIS { innParametere?.ytelseType == KravlinjeTypeEnum.GJR }
            OG { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.AVDOD }
            OG { innBruker?.dodsdato!! < innParametere?.ttFaktiskMaksGrense }
            SÅ {
                log_debug("[HIT] BestemFramtidigTTBeregnesRS.FttBeregnesForGJR")
                fttBeregnes = true
            }
            kommentar(
                """Framtidig trygdetid beregnes for avdød ved pensjon til gjenlevende, forutsatt
            at dødsdato er før overgang alderspensjon."""
            )
        }

        regel("FttBeregnesForUT_GJT") {
            HVIS { innParametere?.ytelseType == KravlinjeTypeEnum.UT_GJT }
            OG { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.AVDOD }
            OG { innBruker?.dodsdato!! < innParametere?.ttFaktiskMaksGrense }
            SÅ {
                log_debug("[HIT] BestemFramtidigTTBeregnesRS.FttBeregnesForUT_GJT")
                fttBeregnes = true
            }
            kommentar(
                """Framtidig trygdetid beregnes for avdød ved pensjon til gjenlevende, forutsatt
            at dødsdato er før overgang alderspensjon."""
            )
        }

        regel("FttBeregnesForFarBP") {
            HVIS { innParametere?.ytelseType == KravlinjeTypeEnum.BP }
            OG { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.FAR }
            OG { innBruker?.dodsdato != null }
            OG { innBruker?.dodsdato!! < innParametere?.ttFaktiskMaksGrense }
            SÅ {
                log_debug("[HIT] BestemFramtidigTTBeregnesRS.FttBeregnesForFarBP")
                fttBeregnes = true
            }
            kommentar(
                """Framtidig trygdetid beregnes for avdød far ved barnepensjon, forutsatt at
            dødsdato er før overgang alderspensjon."""
            )
        }

        regel("FttBeregnesForMorBP") {
            HVIS { innParametere?.ytelseType == KravlinjeTypeEnum.BP }
            OG { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.MOR }
            OG { innBruker?.dodsdato != null }
            OG { innBruker?.dodsdato!! < innParametere?.ttFaktiskMaksGrense }
            SÅ {
                log_debug("[HIT] BestemFramtidigTTBeregnesRS.FttBeregnesForMorBP")
                fttBeregnes = true
            }
            kommentar(
                """Framtidig trygdetid beregnes for avdød mor ved barnepensjon, forutsatt at
            dødsdato er før overgang alderspensjon."""
            )
        }

        regel("FttBeregnesForAFP") {
            HVIS { innParametere?.ytelseType == KravlinjeTypeEnum.AFP }
            OG { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            SÅ {
                log_debug("[HIT] BestemFramtidigTTBeregnesRS.FttBeregnesForAFP")
                fttBeregnes = true
            }
            kommentar("Framtidig trygdetid beregnes for søker ved AFP.")
        }

        regel("FttBeregnesForFP") {
            HVIS { innParametere?.ytelseType == KravlinjeTypeEnum.FP }
            OG { innBruker?.grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            SÅ {
                log_debug("[HIT] BestemFramtidigTTBeregnesRS.FttBeregnesForFP")
                fttBeregnes = true
            }
            kommentar("Framtidig trygdetid beregnes for søker ved pensjon til familiepleier.")
        }

        regel("FTTBeregnesIkke") {
            HVIS { !fttBeregnes }
            SÅ {
                log_debug("[HIT] BestemFramtidigTTBeregnesRS.FTTBeregnesIkke")
            }
            kommentar("""Default regel. Hvis ikke treff på regler ovenfor så beregnes ikke framtidig trygdetid""")
        }

        regel("ReturRegel") {
            HVIS { true }
            SÅ {
                RETURNER(fttBeregnes)
            }
        }
    }
}