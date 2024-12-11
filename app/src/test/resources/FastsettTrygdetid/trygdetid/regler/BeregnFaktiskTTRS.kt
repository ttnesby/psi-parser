package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.*
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.AvtaleLandEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.AvtaletypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.avrundTilMåneder
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.lagTrygdetidEOS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.lagTrygdetidNordisk
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.lagTrygdetidTrygdeavtale
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidPeriodeLengde
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidVariable
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.koder.FaktiskTrygdetidTypeEnum
import no.nav.domain.pensjon.regler.repository.merknad.MerknadEnum
import no.nav.pensjon.regler.internal.domain.TTUtlandTrygdeavtale
import no.nav.pensjon.regler.internal.domain.Trygdetid
import no.nav.pensjon.regler.internal.domain.grunnlag.AntallArMndDag
import no.nav.preg.system.helper.*
import java.util.*

/**
 * Oppretter tt_fa for trygdetid
 */
class BeregnFaktiskTTRS(
    private val innSumTT: MutableMap<FaktiskTrygdetidTypeEnum, TrygdetidPeriodeLengde>,
    private val innTrygdetid: Trygdetid,
    private val innParametere: TrygdetidVariable,
    private val innFørsteVirk: Date
) : AbstractPensjonRuleset<Unit>() {
    private var tt_f1967: Int = 0
    private var tt_e1966: Int = 0
    private var tt_e1966VkAp: Int = 0
    private var tt_f1967VkAp: Int = 0
    private var tt_VkAp: Int = 0
    private var tt_f1967VkApÅr: Int = 0
    private var tt_faktiskUtland: Int? = null

    override fun create() {

        regel("OpprettFaktiskTrygdetid") {
            HVIS { true }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.OpprettFaktiskTrygdetid")
                innTrygdetid.tt_fa = AntallArMndDag(
                    antallAr = 0,
                    antallMnd = 0,
                    antallDager = 0
                )
            }
            kommentar("Oppretter tt_fa for trygdetid")

        }

        regel("RestDagerNorgeOgEøs") {
            HVIS { innParametere.avtaletype == AvtaletypeEnum.EOS_NOR }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! + innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS]?.dager!! <= 30 }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.RestDagerNorgeOgEøs")
                innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS]?.dager = 0
            }
            kommentar(
                """Implementasjon av NAV-Utland praksis: "telle antall løse dager i Norge og løse
            dager i EØS-land, 
        og runde opp kun i Norge dersom de løse dagene summerer seg til færre enn 30.""""
            )

        }

        regel("RestDagerNorgeOgEøsProrata") {
            HVIS { innParametere.avtaletype == AvtaletypeEnum.EOS_NOR }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS_PRORATA] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS_PRORATA]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! + innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS_PRORATA]?.dager!! <= 30 }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.RestDagerNorgeOgEøsProrata")
                innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS_PRORATA]?.dager = 0
            }
            kommentar(
                """Implementasjon av NAV-Utland praksis: "telle antall løse dager i Norge og løse
            dager i EØS-land, 
        og runde opp kun i Norge dersom de løse dagene summerer seg til færre enn 30.""""
            )

        }

        regel("RestDagerNorgeOgAustralia") {
            HVIS { innParametere.avtaletype == AvtaletypeEnum.AUS }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_AUSTRALIA] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_AUSTRALIA]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! + innSumTT[FaktiskTrygdetidTypeEnum.TT_AUSTRALIA]?.dager!! <= 30 }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.RestDagerNorgeOgAustralia")
                innSumTT[FaktiskTrygdetidTypeEnum.TT_AUSTRALIA]?.dager = 0
            }
            kommentar(
                """Implementasjon av NAV-Utland praksis: "telle antall løse dager i Norge og løse
            dager i avtaleland, 
        og runde opp kun i Norge dersom de løse dagene summerer seg til færre enn 30.""""
            )

        }

        regel("RestDagerNorgeOgCanada") {
            HVIS { innParametere.avtaletype == AvtaletypeEnum.CAN }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_CANADA] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_CANADA]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! + innSumTT[FaktiskTrygdetidTypeEnum.TT_CANADA]?.dager!! <= 30 }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.RestDagerNorgeOgCanada")
                innSumTT[FaktiskTrygdetidTypeEnum.TT_CANADA]?.dager = 0
            }
            kommentar(
                """Implementasjon av NAV-Utland praksis: "telle antall løse dager i Norge og løse
            dager i avtaleland, 
        og runde opp kun i Norge dersom de løse dagene summerer seg til færre enn 30.""""
            )

        }

        regel("RestDagerNorgeOgChile") {
            HVIS { innParametere.avtaletype == AvtaletypeEnum.CHL }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_CHILE] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_CHILE]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! + innSumTT[FaktiskTrygdetidTypeEnum.TT_CHILE]?.dager!! <= 30 }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.RestDagerNorgeOgChile")
                innSumTT[FaktiskTrygdetidTypeEnum.TT_CHILE]?.dager = 0
            }
            kommentar(
                """Implementasjon av NAV-Utland praksis: "telle antall løse dager i Norge og løse
            dager i avtaleland, 
        og runde opp kun i Norge dersom de løse dagene summerer seg til færre enn 30.""""
            )

        }

        regel("RestDagerNorgeOgIsrael") {
            HVIS { innParametere.avtaletype == AvtaletypeEnum.ISR }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_ISRAEL] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_ISRAEL]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! + innSumTT[FaktiskTrygdetidTypeEnum.TT_ISRAEL]?.dager!! <= 30 }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.RestDagerNorgeOgIsrael")
                innSumTT[FaktiskTrygdetidTypeEnum.TT_ISRAEL]?.dager = 0
            }
            kommentar(
                """Implementasjon av NAV-Utland praksis: "telle antall løse dager i Norge og løse
            dager i avtaleland, 
        og runde opp kun i Norge dersom de løse dagene summerer seg til færre enn 30.""""
            )

        }

        regel("RestDagerNorgeOgIndia") {
            HVIS { innParametere.avtaletype == AvtaletypeEnum.IND }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_INDIA] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_INDIA]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! + innSumTT[FaktiskTrygdetidTypeEnum.TT_INDIA]?.dager!! <= 30 }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.RestDagerNorgeOgIndia")
                innSumTT[FaktiskTrygdetidTypeEnum.TT_INDIA]?.dager = 0
            }
            kommentar(
                """Implementasjon av NAV-Utland praksis: "telle antall løse dager i Norge og løse
            dager i avtaleland, 
        og runde opp kun i Norge dersom de løse dagene summerer seg til færre enn 30.""""
            )

        }

        regel("RestDagerNorgeOgKorea") {
            HVIS { innParametere.avtaletype == AvtaletypeEnum.KOR }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_SØR_KOREA] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_SØR_KOREA]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! + innSumTT[FaktiskTrygdetidTypeEnum.TT_SØR_KOREA]?.dager!! <= 30 }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.RestDagerNorgeOgKorea")
                innSumTT[FaktiskTrygdetidTypeEnum.TT_SØR_KOREA]?.dager = 0
            }
            kommentar(
                """Implementasjon av NAV-Utland praksis: "telle antall løse dager i Norge og løse
            dager i avtaleland, 
        og runde opp kun i Norge dersom de løse dagene summerer seg til færre enn 30.""""
            )

        }

        regel("RestDagerNorgeOgSveits") {
            HVIS { innParametere.avtaletype == AvtaletypeEnum.CHE }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_SVEITS] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_SVEITS]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! + innSumTT[FaktiskTrygdetidTypeEnum.TT_SVEITS]?.dager!! <= 30 }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.RestDagerNorgeOgSveits")
                innSumTT[FaktiskTrygdetidTypeEnum.TT_SVEITS]?.dager = 0
            }
            kommentar(
                """Implementasjon av NAV-Utland praksis: "telle antall løse dager i Norge og løse
            dager i avtaleland, 
        og runde opp kun i Norge dersom de løse dagene summerer seg til færre enn 30.""""
            )

        }

        regel("RestDagerNorgeOgUSA") {
            HVIS { innParametere.avtaletype == AvtaletypeEnum.USA }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_USA] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_USA]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! + innSumTT[FaktiskTrygdetidTypeEnum.TT_USA]?.dager!! <= 30 }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.RestDagerNorgeOgUSA")
                innSumTT[FaktiskTrygdetidTypeEnum.TT_USA]?.dager = 0
            }
            kommentar(
                """Implementasjon av NAV-Utland praksis: "telle antall løse dager i Norge og løse
            dager i avtaleland, 
        og runde opp kun i Norge dersom de løse dagene summerer seg til færre enn 30.""""
            )

        }

        regel("RestDagerNorgeOgUTLAND") {
            HVIS { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_UTLAND] != null }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_UTLAND]?.dager!! > 0 }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! + innSumTT[FaktiskTrygdetidTypeEnum.TT_UTLAND]?.dager!! <= 30 }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.RestDagerNorgeOgUTLAND")
                innSumTT[FaktiskTrygdetidTypeEnum.TT_UTLAND]?.dager = 0
            }
            kommentar(
                """Implementasjon av NAV-Utland praksis: "telle antall løse dager i Norge og løse
            dager i avtaleland, 
        og runde opp kun i Norge dersom de løse dagene summerer seg til færre enn 30." Gjelder også
            ved summering for vilkårsprøving AP Nye regler"""
            )

        }

        regel("TTAustralia") {
            HVIS { innSumTT[FaktiskTrygdetidTypeEnum.TT_AUSTRALIA] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTAustralia")
                val tt: TTUtlandTrygdeavtale = lagTrygdetidTrygdeavtale(innTrygdetid, AvtaleLandEnum.AUS)
                tt.tt_fa_mnd =
                    avrundTilMåneder(innSumTT[FaktiskTrygdetidTypeEnum.TT_AUSTRALIA], innParametere.restDagerRundesOpp)
                log_formel_start("Faktisk trygdetid avtaleland Australia")
                log_formel("tt_fa_mnd = avrundTilMåneder( ${innSumTT[FaktiskTrygdetidTypeEnum.TT_AUSTRALIA]?.år} år +  ${innSumTT[FaktiskTrygdetidTypeEnum.TT_AUSTRALIA]?.måneder} mnd + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_AUSTRALIA]?.dager}  dager)")
                log_formel("tt_fa_mnd = ${tt.tt_fa_mnd}")
                log_formel_slutt()
            }
            kommentar("Faktisk trygdetid opptjent i Australia.")

        }

        regel("TTCanada") {
            HVIS { innSumTT[FaktiskTrygdetidTypeEnum.TT_CANADA] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTCanada")
                val tt: TTUtlandTrygdeavtale = lagTrygdetidTrygdeavtale(innTrygdetid, AvtaleLandEnum.CAN)
                tt.tt_fa_mnd =
                    avrundTilMåneder(innSumTT[FaktiskTrygdetidTypeEnum.TT_CANADA], innParametere.restDagerRundesOpp)
                log_formel_start("Faktisk trygdetid avtaleland Canada")
                log_formel("tt_fa_mnd = avrundTilMåneder( ${innSumTT[FaktiskTrygdetidTypeEnum.TT_CANADA]?.år} år +  ${innSumTT[FaktiskTrygdetidTypeEnum.TT_CANADA]?.måneder} mnd + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_CANADA]?.dager}  dager)")
                log_formel("tt_fa_mnd = ${tt.tt_fa_mnd}")
                log_formel_slutt()
            }
            kommentar("Faktisk trygdetid opptjent i Canada.")

        }

        regel("TTChile") {
            HVIS { innSumTT[FaktiskTrygdetidTypeEnum.TT_CHILE] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTChile")
                val tt: TTUtlandTrygdeavtale = lagTrygdetidTrygdeavtale(innTrygdetid, AvtaleLandEnum.CHL)
                tt.tt_fa_mnd =
                    avrundTilMåneder(innSumTT[FaktiskTrygdetidTypeEnum.TT_CHILE], innParametere.restDagerRundesOpp)
                log_formel_start("Faktisk trygdetid avtaleland Chile")
                log_formel("tt_fa_mnd = avrundTilMåneder( ${innSumTT[FaktiskTrygdetidTypeEnum.TT_CHILE]?.år} år +  ${innSumTT[FaktiskTrygdetidTypeEnum.TT_CHILE]?.måneder} mnd + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_CHILE]?.dager}  dager)")
                log_formel("tt_fa_mnd = ${tt.tt_fa_mnd}")
                log_formel_slutt()
            }
            kommentar("Faktisk trygdetid opptjent i Chile.")

        }

        regel("TTIsrael") {
            HVIS { innSumTT[FaktiskTrygdetidTypeEnum.TT_ISRAEL] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTIsrael")
                val tt: TTUtlandTrygdeavtale = lagTrygdetidTrygdeavtale(innTrygdetid, AvtaleLandEnum.ISR)
                tt.tt_fa_mnd =
                    avrundTilMåneder(innSumTT[FaktiskTrygdetidTypeEnum.TT_ISRAEL], innParametere.restDagerRundesOpp)
                log_formel_start("Faktisk trygdetid avtaleland Israel")
                log_formel("tt_fa_mnd = avrundTilMåneder( ${innSumTT[FaktiskTrygdetidTypeEnum.TT_ISRAEL]?.år} år +  ${innSumTT[FaktiskTrygdetidTypeEnum.TT_ISRAEL]?.måneder} mnd + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_ISRAEL]?.dager}  dager)")
                log_formel("tt_fa_mnd = ${tt.tt_fa_mnd}")
                log_formel_slutt()
            }
            kommentar("Faktisk trygdetid opptjent i Israel.")

        }

        regel("TTUSA") {
            HVIS { innSumTT[FaktiskTrygdetidTypeEnum.TT_USA] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTUSA")
                val tt: TTUtlandTrygdeavtale = lagTrygdetidTrygdeavtale(innTrygdetid, AvtaleLandEnum.USA)
                tt.tt_fa_mnd =
                    avrundTilMåneder(innSumTT[FaktiskTrygdetidTypeEnum.TT_USA], innParametere.restDagerRundesOpp)
                log_formel_start("Faktisk trygdetid avtaleland USA")
                log_formel("tt_fa_mnd = avrundTilMåneder( ${innSumTT[FaktiskTrygdetidTypeEnum.TT_USA]?.år} år +  ${innSumTT[FaktiskTrygdetidTypeEnum.TT_USA]?.måneder} mnd + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_USA]?.dager}  dager)")
                log_formel("tt_fa_mnd = ${tt.tt_fa_mnd}")
                log_formel_slutt()
            }
            kommentar("Faktisk trygdetid opptjent i USA.")

        }

        regel("TTSørKorea") {
            HVIS { innSumTT[FaktiskTrygdetidTypeEnum.TT_SØR_KOREA] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTSørKorea")
                val tt: TTUtlandTrygdeavtale = lagTrygdetidTrygdeavtale(innTrygdetid, AvtaleLandEnum.KOR)
                tt.tt_fa_mnd =
                    avrundTilMåneder(innSumTT[FaktiskTrygdetidTypeEnum.TT_SØR_KOREA], innParametere.restDagerRundesOpp)
                log_formel_start("Faktisk trygdetid avtaleland SørKorea")
                log_formel("tt_fa_mnd = avrundTilMåneder( ${innSumTT[FaktiskTrygdetidTypeEnum.TT_SØR_KOREA]?.år} år +  ${innSumTT[FaktiskTrygdetidTypeEnum.TT_SØR_KOREA]?.måneder} mnd + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_SØR_KOREA]?.dager}  dager)")
                log_formel("tt_fa_mnd = ${tt.tt_fa_mnd}")
                log_formel_slutt()
            }
            kommentar("Faktisk trygdetid opptjent i Sør Korea.")

        }

        regel("TTSveits") {
            HVIS { innSumTT[FaktiskTrygdetidTypeEnum.TT_SVEITS] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTSveits")
                val tt: TTUtlandTrygdeavtale = lagTrygdetidTrygdeavtale(innTrygdetid, AvtaleLandEnum.CHE)
                tt.tt_fa_mnd =
                    avrundTilMåneder(innSumTT[FaktiskTrygdetidTypeEnum.TT_SVEITS], innParametere.restDagerRundesOpp)
                log_formel_start("Faktisk trygdetid avtaleland Sveits")
                log_formel("tt_fa_mnd = avrundTilMåneder( ${innSumTT[FaktiskTrygdetidTypeEnum.TT_SVEITS]?.år} år +  ${innSumTT[FaktiskTrygdetidTypeEnum.TT_SVEITS]?.måneder} mnd + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_SVEITS]?.dager}  dager)")
                log_formel("tt_fa_mnd = ${tt.tt_fa_mnd}")
                log_formel_slutt()
            }
            kommentar("Faktisk trygdetid opptjent i Sveits.")

        }

        regel("TTIndia") {
            HVIS { innSumTT[FaktiskTrygdetidTypeEnum.TT_INDIA] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTIndia")
                val tt: TTUtlandTrygdeavtale = lagTrygdetidTrygdeavtale(innTrygdetid, AvtaleLandEnum.IND)
                tt.tt_fa_mnd =
                    avrundTilMåneder(innSumTT[FaktiskTrygdetidTypeEnum.TT_INDIA], innParametere.restDagerRundesOpp)
                log_formel_start("Faktisk trygdetid avtaleland India")
                log_formel("tt_fa_mnd = avrundTilMåneder( ${innSumTT[FaktiskTrygdetidTypeEnum.TT_INDIA]?.år} år +  ${innSumTT[FaktiskTrygdetidTypeEnum.TT_INDIA]?.måneder} mnd + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_INDIA]?.dager}  dager)")
                log_formel("tt_fa_mnd = ${tt.tt_fa_mnd}")
                log_formel_slutt()
            }
            kommentar("Faktisk trygdetid opptjent i India.")

        }

        regel("TTEøsProrata") {
            HVIS { innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS_PRORATA] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTEøsProrata")
                lagTrygdetidEOS(innTrygdetid)
                innTrygdetid.ttUtlandEos?.tt_eos_pro_rata_mnd = avrundTilMåneder(
                    innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS_PRORATA],
                    innParametere.restDagerRundesOpp
                )
                log_formel_start("Faktisk trygdetid EØS")
                log_formel("tt_eos_pro_rata_mnd = avrundTilMåneder( ${innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS_PRORATA]?.år} år  + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS_PRORATA]?.måneder} mnd + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS_PRORATA]?.dager} dager)")
                log_formel("tt_eos_pro_rata_mnd = ${innTrygdetid.ttUtlandEos?.tt_eos_pro_rata_mnd}")
                log_formel_slutt()
            }
            kommentar(
                """Faktisk trygdetid opptjent i Eøs land utenfor Norge som er bemerket
            pro-rata."""
            )

        }

        regel("TTEøsTeoretisk") {
            HVIS { innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTEøsTeoretisk")
                lagTrygdetidEOS(innTrygdetid)
                innTrygdetid.ttUtlandEos?.tt_eos_teoretisk_mnd =
                    avrundTilMåneder(innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS], innParametere.restDagerRundesOpp)
                log_formel_start("Faktisk trygdetid EØS")
                log_formel("tt_eos_teoretisk_mnd = avrundTilMåneder(${innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS]?.år}  år + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS]?.måneder}  mnd + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_EØS]?.dager} dager)")
                log_formel("tt_eos_teoretisk_mnd = ${innTrygdetid.ttUtlandEos?.tt_eos_teoretisk_mnd}")
                log_formel_slutt()
            }
            kommentar(
                """Faktisk trygdetid opptjent i Eøs land utenfor Norge, uavhengig av pro-rata
            bemerking."""
            )

        }

        regel("TTNordisk") {
            HVIS { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORDISK_KONVENSJON] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTNordisk")
                lagTrygdetidNordisk(innTrygdetid)
                innTrygdetid.ttUtlandKonvensjon?.tt_A10_fa_mnd = avrundTilMåneder(
                    innSumTT[FaktiskTrygdetidTypeEnum.TT_NORDISK_KONVENSJON],
                    innParametere.restDagerRundesOpp
                )
                log_formel_start("Faktisk trygdetid Nordisk konvensjon")
                log_formel("tt_A10_fa_mnd = avrundTilMåneder( ${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORDISK_KONVENSJON]?.år}  år  + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORDISK_KONVENSJON]?.måneder} mnd + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORDISK_KONVENSJON]?.dager} dager)")
                log_formel("tt_A10_fa_mnd = ${innTrygdetid.ttUtlandKonvensjon?.tt_A10_fa_mnd}")
                log_formel_slutt()
            }
            kommentar("Faktisk trygdetid opptjent i land tilhørende Nordisk konvensjon.")

        }

        regel("TTFør1967VirkFør1991") {
            HVIS { innFørsteVirk.toLocalDate() < localDate(1991, 1, 1) }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTFør1967VirkFør1991")
                tt_f1967 = avrundTilMåneder(
                    innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967],
                    innParametere.restDagerRundesOpp
                )
                innTrygdetid.tt_F67 = min(40, ceilToInt(tt_f1967.dbl / 12))
                tt_f1967VkAp = avrundTilMåneder(innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967], true)
                tt_f1967VkApÅr = min(40, ceilToInt(tt_f1967VkAp.dbl / 12))
                log_formel_start("Faktisk trygdetid før 1967")
                log_formel("tt_F67 = avrundTilMåneder( ${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967]?.år} år  + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967]?.måneder}  mnd +  ${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967]?.dager}  dager)")
                log_formel("tt_F67 = min(40, ceilToInt($tt_f1967/12))")
                log_formel("tt_F67 = ${innTrygdetid.tt_F67}")
                log_formel_slutt()
                innTrygdetid.tt_fa?.antallAr =
                    innTrygdetid.tt_fa?.antallAr?.plus(innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967]?.år!!)!!
                innTrygdetid.tt_fa?.antallMnd =
                    innTrygdetid.tt_fa?.antallMnd?.plus(innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967]?.måneder!! + innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967]?.dager!! / 30)!!  // Deliberate Integer division
                innTrygdetid.tt_fa?.antallDager =
                    innTrygdetid.tt_fa?.antallDager?.plus(innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967]?.dager!! % 30)!!
                log_debug("[   ]    tt_fa år = ${innTrygdetid.tt_fa?.antallAr} tt_fa mnd= ${innTrygdetid.tt_fa?.antallMnd} tt_fa dager = ${innTrygdetid.tt_fa?.antallDager}")
            }
            kommentar(
                """Virk før 1.1.1991. Faktisk trygdetid opptjent før 1967 i antall år. Rest
            måneder rundes opp. Max 40 år.
        Legger til  faktisk trygdetid(år, måned, restdager) for tiden før 1967 i tt_fa"""
            )

        }

        regel("TTFør1967VirkEtter1991") {
            HVIS { innFørsteVirk.toLocalDate() >= localDate(1991, 1, 1) }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTFør1967VirkEtter1991 ${innFørsteVirk.toString()}")
                tt_f1967 = avrundTilMåneder(
                    innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967],
                    innParametere.restDagerRundesOpp
                )
                innTrygdetid.tt_F67 = 40.coerceAtMost(avrund(tt_f1967.dbl / 12.dbl))
                tt_f1967VkAp = avrundTilMåneder(innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967], true)
                tt_f1967VkApÅr = 40.coerceAtMost(avrund(tt_f1967VkAp.dbl / 12.dbl))
                log_formel_start("Faktisk trygdetid før 1967")
                log_formel("tt_F67 = avrundTilMåneder( ${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967]?.år} år  + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967]?.måneder} mnd + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967]?.dager} dager)")
                log_formel("tt_F67 = min(40, avrund($tt_f1967/12))")
                log_formel("tt_F67 = ${innTrygdetid.tt_F67}")
                log_formel_slutt()
            }
            kommentar(
                """Virk etter 1.1.1991. Faktisk trygdetid opptjent før 1967 i antall år. Rest
            måneder rundes av etter vanlige avrundingsregler. Max 40 år."""
            )

        }

        regel("SettMerknadAndreTTFør1967Begrensning") {
            HVIS { innFørsteVirk.toLocalDate() < localDate(1991, 1, 1) }
            OG { innParametere.datoFyller16!!.toLocalDate() < localDate(1967, 1, 1) }
            OG { innTrygdetid.tt_F67 > 0 }
            OG { innTrygdetid.tt_F67 > (1967 - innParametere.datoFyller16?.år!!) }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.UT }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.SettMerknadAndreTTFør1967Begrensning")
                innTrygdetid.merknadListe.add(MerknadEnum.BeregnFaktiskTTRS__TTFor1967Begrensning.lagMerknad())
            }
            kommentar(
                """Merknadstekst:
        "Før 1991: Trygdetiden før 1967 kan ikke være større enn: 1967 - (FÅ + 16)""""
            )

        }

        regel("SettMerknadUTTTFør1967Begrensning") {
            HVIS { innFørsteVirk.toLocalDate() < localDate(1991, 1, 1) }
            OG { innParametere.datoFyller16!!.toLocalDate() < localDate(1967, 1, 1) }
            OG { innTrygdetid.tt_F67 > 0 }
            OG { innTrygdetid.tt_F67 > (1967 - innParametere.datoFyller16?.år!!) }
            OG { innParametere.ytelseType == KravlinjeTypeEnum.UT }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.SettMerknadUTTTFør1967Begrensning")
                innTrygdetid.merknadListe.add(MerknadEnum.BeregnFaktiskTTRS__SettMerknadUTTTFor1967Begrensning.lagMerknad())
            }
            kommentar(
                """Merknadstekst:
        "Før 1991: Trygdetiden før 1967 kan ikke være større enn: 1967 - (FÅ + 16)""""
            )

        }

        regel("SettMerknadGJTTTFør1967Begrensning") {
            HVIS { innFørsteVirk.toLocalDate() < localDate(1991, 1, 1) }
            OG { innParametere.datoFyller16!!.toLocalDate() < localDate(1967, 1, 1) }
            OG { innTrygdetid.tt_F67 > 0 }
            OG { innTrygdetid.tt_F67 > (1967 - innParametere.datoFyller16?.år!!) }
            OG { innParametere.ytelseType == KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.SettMerknadGJTTTFør1967Begrensning")
                innTrygdetid.merknadListe.add(MerknadEnum.BeregnFaktiskTTRS__SettMerknadUTTTFor1967Begrensning.lagMerknad())
            }
            kommentar(
                """Merknadstekst:
        "Før 1991: Trygdetiden før 1967 kan ikke være større enn: 1967 - (FÅ + 16)""""
            )

        }

        regel("TTFør1967Begrensning") {
            HVIS { innFørsteVirk.toLocalDate() < localDate(1991, 1, 1) }
            OG { innParametere.datoFyller16!!.toLocalDate() < localDate(1967, 1, 1) }
            OG { innTrygdetid.tt_F67 > 0 }
            OG { innTrygdetid.tt_F67 > (1967 - innParametere.datoFyller16?.år!!) }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTFør1967Begrensning")
                innTrygdetid.tt_F67 = (1967 - innParametere.datoFyller16?.år!!)
                tt_f1967 = innTrygdetid.tt_F67 * 12
                tt_f1967VkAp = tt_f1967
                tt_f1967VkApÅr = innTrygdetid.tt_F67
                log_formel_start("Faktisk trygdetid før 1967")
                log_formel("tt_F67 = 1967 - årFyller16")
                log_formel("tt_F67 = 1967 - ${innParametere.datoFyller16?.år}")
                log_formel("tt_F67 = ${innTrygdetid.tt_F67}")
                log_formel_slutt()
            }
            kommentar("Før 1991: Trygdetiden før 1967 kan ikke være større enn: 1967 - (FÅ + 16)")

        }

        regel("TTEtter1966VirkFør1991") {
            HVIS { innFørsteVirk.toLocalDate() < localDate(1991, 1, 1) }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTEtter1966VirkFør1991")
                tt_e1966 = avrundTilMåneder(
                    innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966],
                    innParametere.restDagerRundesOpp
                )
                innTrygdetid.tt_E66 = min(40, ceilToInt(tt_e1966.dbl / 12.dbl))
                tt_e1966VkAp = avrundTilMåneder(innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966], true)
                log_formel_start("Faktisk trygdetid etter 1966")
                log_formel("tt_E66 = avrundTilMåneder(${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966]?.år} år  + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966]?.måneder} mnd + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966]?.dager} dager)")
                log_formel("tt_E66 = min(40, ceilToInt($tt_e1966/12))")
                log_formel("tt_E66 = ${innTrygdetid.tt_E66}")
                log_formel_slutt()
                innTrygdetid.tt_fa?.antallAr =
                    innTrygdetid.tt_fa?.antallAr?.plus(innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966]?.år!!)!!
                innTrygdetid.tt_fa?.antallMnd =
                    innTrygdetid.tt_fa?.antallMnd?.plus(innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966]?.måneder!! + innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966]?.dager!! / 30)!!  // Deliberate Integer division
                innTrygdetid.tt_fa?.antallDager =
                    innTrygdetid.tt_fa?.antallDager?.plus(innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966]?.dager!! % 30)!!
                log_debug("[   ]    tt_fa år= ${innTrygdetid.tt_fa?.antallAr} tt_fa mnd= ${innTrygdetid.tt_fa?.antallMnd} tt_fa dager = ${innTrygdetid.tt_fa?.antallDager}")
            }
            kommentar(
                """Virk før 1.1.1991. Faktisk trygdetid opptjent etter 1966 i antall år. Rest
            måneder rundes opp. Max 40 år.
        Legger til  faktisk trygdetid(år, måned, restdager) for tiden fom 1966 i tt_fa"""
            )

        }

        regel("TTEtter1966VirkEtter1991") {
            HVIS { innFørsteVirk.toLocalDate() >= localDate(1991, 1, 1) }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTEtter1966VirkEtter1991")
                tt_e1966 = avrundTilMåneder(
                    innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966],
                    innParametere.restDagerRundesOpp
                )
                innTrygdetid.tt_E66 = min(40, avrund(tt_e1966.dbl / 12.dbl))
                tt_e1966VkAp = avrundTilMåneder(innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966], true)
                log_formel_start("Faktisk trygdetid etter 1966")
                log_formel("tt_E66 = avrundTilMåneder(${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966]?.år} år  + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966]?.måneder} mnd + ${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966]?.dager} dager)")
                log_formel("tt_E66 = min(40, avrund($tt_e1966/12))")
                log_formel("tt_E66 = ${innTrygdetid.tt_E66}")
                log_formel_slutt()
            }
            kommentar(
                """Virk etter 1.1.1991. Faktisk trygdetid opptjent etter 1966 i antall år. Rest
            måneder rundes av etter vanlige avrundingsregler. Max 40 år."""
            )

        }

        regel("TTFør2021") {
            HVIS { innSumTT[FaktiskTrygdetidTypeEnum.TT_FØR_2021] != null }
            SÅ {
                log_formel_start("[HIT] Faktisk trygdetid Norge før 2021")
                innTrygdetid.tt_fa_F2021 = AntallArMndDag(
                    antallAr = innSumTT[FaktiskTrygdetidTypeEnum.TT_FØR_2021]!!.år,
                    antallMnd = innSumTT[FaktiskTrygdetidTypeEnum.TT_FØR_2021]!!.måneder + innSumTT[FaktiskTrygdetidTypeEnum.TT_FØR_2021]!!.dager / 30,  // Deliberate Integer division
                    antallDager = innSumTT[FaktiskTrygdetidTypeEnum.TT_FØR_2021]!!.dager % 30
                )
                log_formel("tt_fa_F2021 = ${innTrygdetid.tt_fa_F2021?.antallAr} år + ${innTrygdetid.tt_fa_F2021?.antallMnd} mnd + ${innTrygdetid.tt_fa_F2021?.antallDager} dager")
                log_formel_slutt()
            }
        }

        regel("TTNorge") {
            HVIS { innFørsteVirk.toLocalDate() >= localDate(1991, 1, 1) }
            OG { innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTNorge")
                innTrygdetid.tt_fa_mnd =
                    avrundTilMåneder(innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE], innParametere.restDagerRundesOpp)
                tt_VkAp = avrundTilMåneder(innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE], true)
                innTrygdetid.tt_fa?.antallAr = innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.år!!
                innTrygdetid.tt_fa?.antallMnd =
                    innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.måneder!! + innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! / 30 // Deliberate Integer division
                innTrygdetid.tt_fa?.antallDager = innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager!! % 30
                log_debug("[   ]    tt_fa = ${innTrygdetid.tt_fa?.antallAr} år, ${innTrygdetid.tt_fa?.antallMnd} mnd, ${innTrygdetid.tt_fa?.antallDager} dager")
                log_debug("[   ]    tt_fa_mnd = ${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.år.toString()} år ${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.måneder.toString()} mnd ${innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE]?.dager.toString()} dg = ${innTrygdetid.tt_fa_mnd.toString()} mnd")
            }
            kommentar("Faktisk trygdetid opptjent i Norge.")

        }

        regel("TTNorgeVirkFør1991") {
            HVIS { innFørsteVirk.toLocalDate() < localDate(1991, 1, 1) }
            OG { (tt_f1967 + tt_e1966) > 0 }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.TTNorgeVirkFør1991")
                innTrygdetid.tt_fa_mnd = innTrygdetid.tt_F67 * 12 + tt_e1966
                tt_VkAp = tt_f1967VkApÅr * 12 + tt_e1966VkAp
                log_formel_start("Faktisk trygdetid Norge, førstevirk før 1991")
                log_formel("tt_fa_mnd = tt_F67 * 12 + tt_e1966")
                log_formel("tt_fa_mnd = ${innTrygdetid.tt_F67} * 12 + $tt_e1966")
                log_formel("tt_fa_mnd = ${innTrygdetid.tt_fa_mnd}")
                log_formel_slutt()
            }
            kommentar(
                """Faktisk trygdetid opptjent i Norge.
        Virk før 1.1.1991. Faktisk trygdetid opptjent før 1967 og etter 1966 adderes og avrundes
            hver for seg. 
        Trygdetid før 1967 avrundes til år og trygdetid etter 1966 avrundes til måneder. """
            )

        }

        regel("TrygdetidUtland") {
            HVIS { innSumTT[FaktiskTrygdetidTypeEnum.TT_UTLAND] != null }
            SÅ {
                log_debug("[HIT] BeregnFaktiskTTRS.UTLAND")
                tt_faktiskUtland = avrundTilMåneder(innSumTT[FaktiskTrygdetidTypeEnum.TT_UTLAND], true)
            }

        }

        regel("SettProrataVkAP") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.AP }
            OG { tt_VkAp > 0 }
            OG { tt_faktiskUtland != null && tt_faktiskUtland!! > 0 }
            SÅ {
                innTrygdetid.prorataTellerVKAP = tt_VkAp
                innTrygdetid.prorataNevnerVKAP = tt_VkAp + tt_faktiskUtland!!
                log_debug("[HIT] BeregnFaktiskTTRS.SettProrataVkAP, prorataTellerVKAP: ${innTrygdetid.prorataTellerVKAP} prorataNevnerVKAP: ${innTrygdetid.prorataNevnerVKAP}")
            }
            kommentar(
                """Prorata brøk for vilkårsprøving alderspensjon (AP2011, AP2016, AP2025):
        Dersom regelverktype ikke er G_REG og det finnes faktisk trygdetid i avtaleland så beregnes
            en prorata brøk hvor
        teller er lik faktisk trygdetid i Norge og nevner er lik sum av faktisk trygdetid i Norge og
            faktisk trygdetid i avtaleland.
        For både teller og nevner rundes rest dager opp til nærmeste hele måned. Normalt vil rest
            dager under 30 rundes ned for alderspensjon."""
            )
        }
    }
}