package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.finnPeriodeLengde
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidPeriodeLengde
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidVariable
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.koder.FaktiskTrygdetidTypeEnum
import no.nav.domain.pensjon.regler.repository.merknad.MerknadEnum
import no.nav.pensjon.regler.internal.domain.Trygdetid
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.preg.system.helper.date
import no.nav.preg.system.helper.localDate
import no.nav.preg.system.helper.toLocalDate

/**
 * Hvis ingen trygdetidsperioder er oppgitt antas at all faktisk trygdetid er opptjent i Norge.
 */
class BareTTNorgeRS(
    private val innSumTT: MutableMap<FaktiskTrygdetidTypeEnum, TrygdetidPeriodeLengde>,
    private val innParametere: TrygdetidVariable?,
    private val innTrygdetid: Trygdetid?
) : AbstractPensjonRuleset<Unit>() {
    override fun create() {

        regel("PeriodeNorge_faktiskTrygdetid") {
            HVIS { innParametere != null }
            OG { innParametere?.ttFaktiskBeregnes!! }
            SÅ {
                log_debug("[HIT] BareTTNorgeRS.PeriodeNorge_faktiskTrygdetid")
                innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE] =
                    finnPeriodeLengde(innParametere?.ttFaktiskRegnesFra, innParametere?.ttFaktiskRegnesTil)
                innTrygdetid?.merknadListe?.add(
                    MerknadEnum.BareTTNorgeRS__PeriodeNorge.lagMerknad(
                        mutableListOf(
                            innParametere?.ttFaktiskRegnesFra.toString(),
                            innParametere?.ttFaktiskRegnesTil.toString()
                        )
                    )
                )
            }
            kommentar("Trygdetidsperioden legges til sum trygdetid i Norge.")
        }

        regel("PeriodeNorge_ingenFaktiskTrygdetid") {
            HVIS { innParametere != null }
            OG { !innParametere?.ttFaktiskBeregnes!! }
            SÅ {
                log_debug("[HIT] BareTTNorgeRS.PeriodeNorge_ingenFaktiskTrygdetid")
                innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE] = TrygdetidPeriodeLengde(
                    dager = 0,
                    måneder = 0,
                    år = 0
                )
                innTrygdetid?.merknadListe?.add(
                    MerknadEnum.BareTTNorgeRS__PeriodeNorge.lagMerknad(
                        mutableListOf(
                            innParametere?.ttFaktiskRegnesFra.toString(),
                            innParametere?.ttFaktiskRegnesTil.toString()
                        )
                    )
                )
            }
            kommentar("Ingen faktisk trygdetid. Lengde må settes til 0.")
        }

        regel("PeriodeFør1967") {
            HVIS { innParametere != null }
            OG { innParametere?.ttFaktiskRegnesFra!!.toLocalDate() < localDate(1967, 1, 1) }
            OG { innParametere?.ttFaktiskRegnesTil!!.toLocalDate() < localDate(1967, 1, 1) }
            SÅ {
                log_debug("[HIT] BareTTNorgeRS.PeriodeFør1967")
                innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967] =
                    finnPeriodeLengde(innParametere?.ttFaktiskRegnesFra, innParametere?.ttFaktiskRegnesTil)
            }
            kommentar("Beregn trygdetid før 1967.")
        }

        regel("PeriodeFørOgEtter1967") {
            HVIS { innParametere != null }
            OG { innParametere?.ttFaktiskRegnesFra!!.toLocalDate() < localDate(1967, 1, 1) }
            OG { innParametere?.ttFaktiskRegnesTil!!.toLocalDate() >= localDate(1967, 1, 1) }
            SÅ {
                log_debug("[HIT] BareTTNorgeRS.PeriodeFørOgEtter1967")
                innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_FØR_1967] =
                    finnPeriodeLengde(innParametere?.ttFaktiskRegnesFra, date(1966, 12, 31))
                innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966] =
                    finnPeriodeLengde(date(1967, 1, 1), innParametere?.ttFaktiskRegnesTil)
            }
            kommentar("Beregn trygdetid før og etter 1-jan-1967.")
        }

        regel("PeriodeEtter1967") {
            HVIS { innParametere != null }
            OG { innParametere?.ttFaktiskRegnesFra!!.toLocalDate() >= localDate(1967, 1, 1) }
            OG { innParametere?.ttFaktiskRegnesTil!!.toLocalDate() >= localDate(1967, 1, 1) }
            SÅ {
                log_debug("[HIT] BareTTNorgeRS.PeriodeEtter1967")
                innSumTT[FaktiskTrygdetidTypeEnum.TT_NORGE_ETTER_1966] =
                    finnPeriodeLengde(innParametere?.ttFaktiskRegnesFra, innParametere?.ttFaktiskRegnesTil)
            }
            kommentar("Beregn trygdetid etter 1-jan-1967.")
        }
    }
}