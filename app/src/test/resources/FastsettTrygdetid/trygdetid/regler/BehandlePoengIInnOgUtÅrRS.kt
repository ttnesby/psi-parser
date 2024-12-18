package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.merknad.MerknadEnum
import no.nav.pensjon.regler.internal.domain.TTPeriode
import no.nav.pensjon.regler.internal.domain.Trygdetid
import no.nav.pensjon.regler.internal.domain.beregning.Poengtall
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.preg.system.helper.*
import java.time.LocalDate
import java.util.*

/**
 * Hjelperegel for å finne dato for årets slutt for trygdetidsperioden.
 */
class BehandlePoengIInnOgUtÅrRS(
    private val innPeriode: TTPeriode?,
    private val innKopiliste: MutableList<TTPeriode>,
    private val innKonvUFT: Date?,
    private val innPoengtallListe: MutableMap<Int, Poengtall>?,
    private val innTrygdetid: Trygdetid?
) : AbstractPensjonRuleset<Unit>() {
    private var periode: TTPeriode = TTPeriode(innPeriode!!)
    private var datoÅrStart: LocalDate = localDate((periode.fom)?.år!!, 1, 1)
    private var datoÅrSlutt: Date? = null

    override fun create() {

        regel("FinnDatoÅrSlutt") {
            HVIS { innPeriode?.tom != null }
            SÅ {
                datoÅrSlutt = date((periode.tom)?.år!!, 12, 31)
            }
            kommentar("Hjelperegel for å finne dato for årets slutt for trygdetidsperioden.")

        }

        regel("PoengIInnÅr") {
            HVIS { periode.poengIInnAr }
            OG { periode.fom?.toLocalDate() != datoÅrStart }
            OG { innKonvUFT == null }
            SÅ {
                log_debug("[HIT] BehandlePoengIInnOgUtÅrRS.PoengIInnÅr")
                periode.fom = datoÅrStart.toDate()
                log_debug("[   ]    periode.fom = ${periode.fom.toString()}")
            }
            kommentar(
                """Hvis det er angitt poeng i innår og trygdetidsperioden ikke starter 1-januar i
            året så endres perioden til å starte 1-januar."""
            )

        }

        regel("PoengIInnÅrFørKonvUFT") {
            HVIS { periode.poengIInnAr }
            OG { innKonvUFT != null }
            OG { periode.fom?.toLocalDate() != datoÅrStart }
            OG { periode.fom!!.toLocalDate() < innKonvUFT!!.toLocalDate() }
            SÅ {
                log_debug("[HIT] BehandlePoengIInnOgUtÅrRS.PoengIInnÅrFørKonvUFT")
                periode.fom = datoÅrStart.toDate()
                innTrygdetid?.merknadListe?.add(
                    MerknadEnum.BehandlePoengIInnOgUtArRS__PoengIInnArForKonvUFT.lagMerknad(
                        mutableListOf(innKonvUFT.toString())
                    )
                )
                log_debug("[   ]    periode.fom = ${periode.fom.toString()}")
            }
            kommentar(
                """Hvis det er angitt poeng i innår og trygdetidsperioden ikke starter 1-januar i
            året og periodens fra og med dato er før konverteringsuføretidspunktet
        så endres perioden til å starte 1-januar."""
            )

        }

        regel("PoengIInnÅrFraPoengtallListe") {
            HVIS { !periode.poengIInnAr }
            OG { periode.fom?.toLocalDate() != datoÅrStart }
            OG { innPoengtallListe != null }
            OG { innPoengtallListe!!.contains(periode.fom?.år!!) }
            OG { innPoengtallListe!![periode.fom?.år]!!.pp > 1.00 }
            SÅ {
                log_debug("[HIT] BehandlePoengIInnOgUtÅrRS.PoengIInnÅrFraPoengtallListe")
                periode.fom = datoÅrStart.toDate()
                innTrygdetid?.merknadListe?.add(MerknadEnum.BehandlePoengIInnOgUtArRS__PoengIInnArFraPoengtallListe.lagMerknad())
                log_debug("[   ]    periode.fom = ${periode.fom.toString()}")
            }
            kommentar(
                """For AP med overgang fra UT og hvor det er gitt full fremtidig trygdetid i
            uføretrygden
        blir det regnet tilsvarende som for poeng i innår dersom det finnes pensjonspoeng større enn
            1.0 i året
        trygdetidsperioden starter."""
            )

        }

        regel("PoengIUtÅr") {
            HVIS { periode.poengIUtAr }
            OG { periode.tom?.toLocalDate() != datoÅrSlutt?.toLocalDate() }
            OG { innKonvUFT == null }
            SÅ {
                log_debug("[HIT] BehandlePoengIInnOgUtÅrRS.PoengIUtÅr")
                periode.tom = datoÅrSlutt
                log_debug("[   ]    periode.tom = ${periode.tom.toString()}")
            }
            kommentar(
                """Hvis det er angitt poeng i utår og trygdetidsperioden ikke slutter ved årets
            slutt så endres perioden til å slutte ved årets slutt."""
            )

        }

        regel("PoengIUtÅrFørKonvUFT") {
            HVIS { periode.poengIUtAr }
            OG { innKonvUFT != null }
            OG { periode.tom?.toLocalDate() != datoÅrSlutt?.toLocalDate() }
            OG { periode.tom!!.toLocalDate() < innKonvUFT!!.toLocalDate() }
            SÅ {
                log_debug("[HIT] BehandlePoengIInnOgUtÅrRS.PoengIUtÅrFørKonvUFT")
                periode.tom = datoÅrSlutt
                innTrygdetid?.merknadListe?.add(
                    MerknadEnum.BehandlePoengIInnOgUtArRS__PoengIUtArForKonvUFT.lagMerknad(
                        mutableListOf(innKonvUFT.toString())
                    )
                )
                log_debug("[   ]    periode.tom = ${periode.tom.toString()}")
            }
            kommentar(
                """Hvis det er angitt poeng i utår og trygdetidsperiode ikke slutter ved årets
            slutt
        og periodens til og med dato er før konverteringsuføretidspunktet
        så endres perioden til å slutte ved årets slutt."""
            )

        }

        regel("PoengIUtÅrFraPoengtallListe") {
            HVIS { periode.tom?.toLocalDate() != datoÅrSlutt?.toLocalDate() }
            OG { innPoengtallListe != null }
            OG { innPoengtallListe!!.contains(periode.tom?.år!!) }
            OG { innPoengtallListe!![periode.tom?.år]!!.pp > 1.00 }
            SÅ {
                log_debug("[HIT] BehandlePoengIInnOgUtÅrRS.PoengIUtÅrFraPoengtallListe")
                periode.tom = datoÅrSlutt
                innTrygdetid?.merknadListe?.add(MerknadEnum.BehandlePoengIInnOgUtArRS__PoengIUtArFraPoengtallListe.lagMerknad())
                log_debug("[   ]    periode.tom = ${periode.tom.toString()}")
            }
            kommentar(
                """For AP med overgang fra UT og hvor det er gitt full fremtidig trygdetid i
            uføretrygden
        blir det regnet tilsvarende som for poeng i utår dersom det finnes pensjonspoeng større enn
            1.0 i året
        trygdetidsperioden slutter."""
            )

        }

        regel("LeggTilKopiliste") {
            HVIS { true }
            SÅ {
                innKopiliste.add(periode)
            }
            kommentar("Legg den (eventuelt) modifiserte trygdetidsperiode til kopiliste.")

        }

    }
}