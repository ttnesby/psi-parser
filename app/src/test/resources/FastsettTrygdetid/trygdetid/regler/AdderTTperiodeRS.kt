package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.AvtaleLandEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.adderPeriodeLengde
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.finnPeriodeLengde
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.lagTrygdetidPeriode
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidPeriodeLengde
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.koder.FaktiskTrygdetidTypeEnum
import no.nav.pensjon.regler.internal.domain.TTPeriode
import no.nav.preg.system.helper.date
import no.nav.preg.system.helper.localDate
import no.nav.preg.system.helper.toLocalDate
import java.util.*

/**
 * Summerer også trygdetid i avtaleland til TT_UTLAND.
 */
class AdderTTperiodeRS(
    private val innPeriode: TTPeriode,
    private val innPeriodeLengde: TrygdetidPeriodeLengde,
    private val innSumTT: MutableMap<FaktiskTrygdetidTypeEnum, TrygdetidPeriodeLengde>,
    private val innVirk: Date
) : AbstractPensjonRuleset<Unit>() {
    private var land: AvtaleLandEnum? = null
    private var eøskonvensjon: Boolean = false
    private var nordiskkonvensjon: Boolean = false

    override fun create() {
        regel("BestemTTperiodeLand") {
            HVIS { innPeriode.land != null }
            SÅ {
                land = AvtaleLandEnum.valueOf(innPeriode.land!!.name)
            }
            kommentar("Bestem periode land")

        }

        regel("BestemEøsKonvensjonsLand") {
            HVIS { land != null }
            SÅ {
                eøskonvensjon = EøsKonvensjonsLand(land!!, innVirk)
            }
            kommentar("Bestem om land tilhører EØS")

        }
        regel("BestemNordiskKonvensjonsLand") {
            HVIS { land != null }
            SÅ {
                nordiskkonvensjon = NordiskKonvensjonsLand(land!!, innVirk)
            }
            kommentar("Bestem om land tilhører Nordisk konvensjon")

        }
        regel("PeriodeNorge") {
            HVIS { land == AvtaleLandEnum.NOR }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeNorge")
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_NORGE), innPeriodeLengde)
            }
            kommentar(
                """Hvis trygdetidsperioden er opptjent i Norge legges den til sum trygdetid i
            Norge."""
            )
        }
        regel("PeriodeEøsProrata") {
            HVIS { eøskonvensjon }
            OG { !innPeriode.ikkeProRata }
            OG { land != AvtaleLandEnum.NOR }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeEøsProrata")
                adderPeriodeLengde(
                    lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_EØS_PRORATA),
                    innPeriodeLengde
                )
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_UTLAND), innPeriodeLengde)
            }
            kommentar(
                """Hvis trygdetidsperioden er opptjent i EØS-land utenfor Norge og den er
            bemerket som pro-rata 
        legges den til sum EØS prorata."""
            )

        }
        regel("PeriodeEøsTeoretisk") {
            HVIS { eøskonvensjon }
            OG { land != AvtaleLandEnum.NOR }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeEøsTeoretisk")
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_EØS), innPeriodeLengde)
            }
            kommentar(
                """Hvis trygdetidsperioden er opptjent i EØS-land utenfor Norge, uavhengig av
            pro-rata bemerking, 
        legges den til sum EØS teoretisk."""
            )
        }
        regel("PeriodeNordisk") {
            HVIS { nordiskkonvensjon }
            OG { land != AvtaleLandEnum.NOR }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeNordisk")
                adderPeriodeLengde(
                    lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_NORDISK_KONVENSJON),
                    innPeriodeLengde
                )
            }
            kommentar(
                """Hvis trygdetidsperioden er opptjent i Nordisk konvensjonsland utenfor Norge
            legges den til 
        sum trygdetid i Norden."""
            )

        }
        regel("PeriodeAustralia") {
            HVIS { land == AvtaleLandEnum.AUS }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeAustralia")
                adderPeriodeLengde(
                    lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_AUSTRALIA),
                    innPeriodeLengde
                )
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_UTLAND), innPeriodeLengde)
            }
            kommentar("Hvis trygdetidsperioden er opptjent i Australia.")

        }
        regel("PeriodeCanada") {
            HVIS { land == AvtaleLandEnum.CAN }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeCanada")
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_CANADA), innPeriodeLengde)
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_UTLAND), innPeriodeLengde)
            }
            kommentar("Hvis trygdetidsperioden er opptjent i Canada.")

        }
        regel("PeriodeChile") {
            HVIS { land == AvtaleLandEnum.CHL }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeChile")
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_CHILE), innPeriodeLengde)
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_UTLAND), innPeriodeLengde)
            }
            kommentar("Hvis trygdetidsperioden er opptjent i Chile.")

        }
        regel("PeriodeIsrael") {
            HVIS { land == AvtaleLandEnum.ISR }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeIsrael")
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_ISRAEL), innPeriodeLengde)
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_UTLAND), innPeriodeLengde)
            }
            kommentar("Hvis trygdetidsperioden er opptjent i Israel.")

        }
        regel("PeriodeIndia") {
            HVIS { land == AvtaleLandEnum.IND }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeIndia")
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_INDIA), innPeriodeLengde)
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_UTLAND), innPeriodeLengde)
            }
            kommentar("Hvis trygdetidsperioden er opptjent i India.")

        }
        regel("PeriodeSørKorea") {
            HVIS { land == AvtaleLandEnum.KOR }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeSørKorea")
                adderPeriodeLengde(
                    lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_SØR_KOREA),
                    innPeriodeLengde
                )
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_UTLAND), innPeriodeLengde)
            }
            kommentar("Hvis trygdetidsperioden er opptjent i Sør Korea.")

        }
        regel("PeriodeSveits") {
            HVIS { land == AvtaleLandEnum.CHE }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeSveits")
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_SVEITS), innPeriodeLengde)
            }
        }
        regel("PeriodeUSA") {
            HVIS { land == AvtaleLandEnum.USA }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeUSA")
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_USA), innPeriodeLengde)
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_UTLAND), innPeriodeLengde)
            }
            kommentar("Hvis trygdetidsperioden er opptjent i USA.")

        }
        regel("PeriodeFør1967") {
            HVIS { land == AvtaleLandEnum.NOR }
            OG { innPeriodeLengde.fom!!.toLocalDate() < localDate(1967, 1, 1) }
            OG { innPeriodeLengde.tom!!.toLocalDate() < localDate(1967, 1, 1) }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeFør1967")
                adderPeriodeLengde(
                    lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967),
                    innPeriodeLengde
                )
            }
            kommentar(
                """Hvis trygdetidsperioden er opptjent i Norge og perioden er før 1967 legges den
            til sum trygdetid før 1967."""
            )

        }
        regel("PeriodeFørOgEtter1967") {
            HVIS { land == AvtaleLandEnum.NOR }
            OG { innPeriodeLengde.fom!!.toLocalDate() < localDate(1967, 1, 1) }
            OG { innPeriodeLengde.tom!!.toLocalDate() >= localDate(1967, 1, 1) }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeFørOgEtter1967")
                var p: TrygdetidPeriodeLengde = finnPeriodeLengde(innPeriodeLengde.fom, date(1966, 12, 31))
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967), p)
                p = finnPeriodeLengde(date(1967, 1, 1), innPeriodeLengde.tom)
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966), p)
            }
            kommentar(
                """Hvis trygdetidsperioden er opptjent i Norge og perioden krysser 1-jan-1967
            beregnes periodens lengde både før og etter denne dato
        og sum trygdetid før og etter 1967 oppdateres."""
            )

        }
        regel("PeriodeEtter1967") {
            HVIS { land == AvtaleLandEnum.NOR }
            OG { innPeriodeLengde.fom!!.toLocalDate() >= localDate(1967, 1, 1) }
            OG { innPeriodeLengde.tom!!.toLocalDate() >= localDate(1967, 1, 1) }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeEtter1967")
                adderPeriodeLengde(
                    lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966),
                    innPeriodeLengde
                )
            }
            kommentar(
                """Hvis trygdetidsperioden er opptjent i Norge og perioden er etter 1967 leggs
            den til sum trygdetid etter 1967."""
            )
        }
        regel("PeriodeFør2021") {
            HVIS { land == AvtaleLandEnum.NOR }
            OG { innPeriodeLengde.fom!!.toLocalDate() < localDate(2021, 1, 1) }
            OG { innPeriodeLengde.tom!!.toLocalDate() < localDate(2021, 1, 1) }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeFør2021")
                adderPeriodeLengde(
                    lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_FØR_2021),
                    innPeriodeLengde
                )
            }
        }
        regel("PeriodeFørOgEtter2021") {
            HVIS { land == AvtaleLandEnum.NOR }
            OG { innPeriodeLengde.fom!!.toLocalDate() < localDate(2021, 1, 1) }
            OG { innPeriodeLengde.tom!!.toLocalDate() >= localDate(2021, 1, 1) }
            SÅ {
                log_debug("[HIT] AdderTTperiodeRS.PeriodeFørOgEtter2021")
                val p: TrygdetidPeriodeLengde = finnPeriodeLengde(innPeriodeLengde.fom, date(2020, 12, 31))
                adderPeriodeLengde(lagTrygdetidPeriode(innSumTT, FaktiskTrygdetidTypeEnum.TT_FØR_2021), p)
            }
        }

    }
}
