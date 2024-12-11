package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.*
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.avrundTilMåneder
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.finnPeriodeLengde
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidPeriodeLengde
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidVariable
import no.nav.domain.pensjon.regler.repository.merknad.MerknadEnum
import no.nav.pensjon.regler.internal.domain.TTUtlandTrygdeavtale
import no.nav.pensjon.regler.internal.domain.Trygdetid
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.preg.system.helper.*
import no.nav.system.rule.dsl.pattern.Pattern
import no.nav.system.rule.dsl.pattern.createPattern
import java.util.*

/**
 * Dato framtidig trygdetid regnes fra skal ikke være større enn dato framtidig trygdetid regnes
 * til.
 */
class BeregnFramtidigTTRS(
    private val innTrygdetid: Trygdetid,
    private val innParametere: TrygdetidVariable,
    private val innBruker: Persongrunnlag,
    private val innFørsteVirk: Date
) : AbstractPensjonRuleset<Unit>() {
    private val ttUtlandTrygdeavtaleListe: List<TTUtlandTrygdeavtale> = innTrygdetid.ttUtlandTrygdeavtaler
    private val ttUtlandTrygdeavtale: Pattern<TTUtlandTrygdeavtale> = ttUtlandTrygdeavtaleListe.createPattern()

    override fun create() {

        regel("feilFramtidigPeriode") {
            HVIS { innParametere.ttFramtidigRegnesFra != null }
            OG { innParametere.ttFramtidigRegnesTil != null }
            OG { innParametere.ttFramtidigRegnesFra!! > innParametere.ttFramtidigRegnesTil }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.FeilFramtidigPeriode")
                innTrygdetid.merknadListe.add(MerknadEnum.BeregnFramtidigTTRS__FeilFramtidigPeriode.lagMerknad())
                throw IllegalArgumentException("feilFramtidigPeriode")
            }
            kommentar(
                """Dato framtidig trygdetid regnes fra skal ikke være større enn dato framtidig
            trygdetid regnes til."""
            )

        }

        regel("fttNorge") {
            HVIS { innFørsteVirk.toLocalDate() >= localDate(1991, 1, 1) }
            OG { innTrygdetid.tt_fa_mnd >= innParametere.firefemtedelskrav!! }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.AFP }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.FttNorge")
                val p: TrygdetidPeriodeLengde =
                    finnPeriodeLengde(innParametere.ttFramtidigRegnesFra, innParametere.ttFramtidigRegnesTil)
                innTrygdetid.ftt = avrundTilMåneder(p, true)
                innTrygdetid.ftt_fom = innParametere.ttFramtidigRegnesFra
                innTrygdetid.ftt_redusert = false
                log_formel_start("Framtidig trygdetid, tt_fa_mnd ${innTrygdetid.tt_fa_mnd} &gt;= ${innParametere.firefemtedelskrav}")
                log_formel("ftt = avrundTilMåneder(ttFramtidigRegnesFra, ttFramtidigRegnesTil)")
                log_formel("ftt = avrundTilMåneder(${innParametere.ttFramtidigRegnesFra.toString()}, ${innParametere.ttFramtidigRegnesTil.toString()})")
                log_formel("ftt = ${innTrygdetid.ftt}")
                log_formel_slutt()
            }
            kommentar(
                """Dersom faktisk trygdetid i Norge er større enn 4/5 av opptjeningstiden skal
            all framtidig trygdetid medregnes."""
            )

        }

        regel("fttNorgeRedusert") {
            HVIS { innFørsteVirk.toLocalDate() >= localDate(1991, 1, 1) }
            OG { innTrygdetid.tt_fa_mnd < innParametere.firefemtedelskrav!! }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.AFP }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.FttNorgeRedusert")
                innTrygdetid.ftt = max(0, 480 - innParametere.firefemtedelskrav!!)
                innTrygdetid.ftt_fom = innParametere.ttFramtidigRegnesFra
                innTrygdetid.ftt_redusert = true
                log_formel_start("Framtidig trygdetid, tt_fa_mnd ${innTrygdetid.tt_fa_mnd} &lt; ${innParametere.firefemtedelskrav}")
                log_formel("ftt = max(0, 480 - firefemtedelskrav)")
                log_formel("ftt = max(0, 480 - ${innParametere.firefemtedelskrav})")
                log_formel("ftt = ${innTrygdetid.ftt}")
                log_formel_slutt()
            }
            kommentar(
                """Dersom faktisk trygdetid i Norge er mindre enn 4/5 av opptjeningstiden skal
            den framtidige trygdetiden 
        utgjøre 40 år med fradrag av 4/5 av opptjeningstiden.
        Hvis firefemtedelskrav er større enn 480 blir svaret negativt. I så fall settes ftt til
            0."""
            )

        }

        regel("settMerknadAndreFttNorgeRedusert") {
            HVIS { "fttNorgeRedusert".harTruffet() }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.UT }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.SettMerknadAndreFttNorgeRedusert")
                innTrygdetid.merknadListe.add(MerknadEnum.BeregnFramtidigTTRS__FttNorgeRedusert.lagMerknad())
            }
            kommentar(
                """Merknadstekst:
        "Dersom faktisk trygdetid i Norge er mindre enn 4/5 av opptjeningstiden skal den fremtidige
            trygdetiden utgjøre 40 år med fradrag av 4/5 av opptjeningstiden.""""
            )

        }

        regel("settMerknadUTFttNorgeRedusert") {
            HVIS { "fttNorgeRedusert".harTruffet() }
            OG { innParametere.ytelseType == KravlinjeTypeEnum.UT }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.SettMerknadUTFttNorgeRedusert")
                innTrygdetid.merknadListe.add(MerknadEnum.BeregnFramtidigTTRS__SettMerknadUTFttNorgeRedusert.lagMerknad())
            }
            kommentar(
                """Merknadstekst:
        "Dersom faktisk trygdetid i Norge er mindre enn 4/5 av opptjeningstiden skal den fremtidige
            trygdetiden utgjøre 40 år med fradrag av 4/5 av opptjeningstiden.""""
            )

        }

        regel("settMerknadGJTFttNorgeRedusert") {
            HVIS { "fttNorgeRedusert".harTruffet() }
            OG { innParametere.ytelseType == KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.SettMerknadGJTFttNorgeRedusert")
                innTrygdetid.merknadListe.add(MerknadEnum.BeregnFramtidigTTRS__SettMerknadUTFttNorgeRedusert.lagMerknad())
            }
            kommentar(
                """Merknadstekst:
        "Dersom faktisk trygdetid i Norge er mindre enn 4/5 av opptjeningstiden skal den fremtidige
            trygdetiden utgjøre 40 år med fradrag av 4/5 av opptjeningstiden.""""
            )

        }

        regel("fttNorgeOgAFP") {
            HVIS { innParametere.ytelseType == KravlinjeTypeEnum.AFP }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.FttNorgeOgAFP")
                val p: TrygdetidPeriodeLengde =
                    finnPeriodeLengde(innParametere.ttFramtidigRegnesFra, innParametere.ttFramtidigRegnesTil)
                innTrygdetid.ftt = avrundTilMåneder(p, true)
                innTrygdetid.ftt_fom = innParametere.ttFramtidigRegnesFra
                innTrygdetid.ftt_redusert = false
                log_formel_start("Framtidig trygdetid")
                log_formel("ftt = avrundTilMåneder(ttFramtidigRegnesFra, ttFramtidigRegnesTil)")
                log_formel("ftt = avrundTilMåneder(${innParametere.ttFramtidigRegnesFra.toString()}, ${innParametere.ttFramtidigRegnesTil.toString()})")
                log_formel("ftt = ${innTrygdetid.ftt}")
                log_formel_slutt()
            }
            kommentar("AFP regnes alltid med full framtidig trygdetid.")

        }

        regel("før1991OgFullFttNorge") {
            HVIS { innFørsteVirk.toLocalDate() < localDate(1991, 1, 1) }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.Før1991OgFullFttNorge")
                val p: TrygdetidPeriodeLengde =
                    finnPeriodeLengde(innParametere.ttFramtidigRegnesFra, innParametere.ttFramtidigRegnesTil)
                innTrygdetid.ftt = avrundTilMåneder(p, true)
                innTrygdetid.ftt_redusert = false
                log_formel_start("Framtidig trygdetid")
                log_formel("ftt = avrundTilMåneder(ttFramtidigRegnesFra, ttFramtidigRegnesTil)")
                log_formel("ftt = avrundTilMåneder(${innParametere.ttFramtidigRegnesFra.toString()}, ${innParametere.ttFramtidigRegnesTil.toString()})")
                log_formel("ftt = ${innTrygdetid.ftt}")
                log_formel_slutt()
            }
            kommentar("Før 1991 regnes full framtidig trygdetid.")

        }

        regel("fttEos") {
            HVIS { innTrygdetid.ttUtlandEos != null }
            OG { (innTrygdetid.tt_fa_mnd + innTrygdetid.ttUtlandEos?.tt_eos_teoretisk_mnd!!) >= innParametere.firefemtedelskrav!! }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.FttEos")
                val p: TrygdetidPeriodeLengde = finnPeriodeLengde(innParametere.ttFramtidigRegnesFra, innParametere.ttFramtidigRegnesTil)
                innTrygdetid.ttUtlandEos?.ftt_eos = avrundTilMåneder(p, true)
                innTrygdetid.ttUtlandEos?.ftt_eos_redusert = false
                log_formel_start("Framtidig trygdetid EØS, tt_fa_mnd ${innTrygdetid.tt_fa_mnd} + tt_eos_teoretisk_mnd ${innTrygdetid.ttUtlandEos?.tt_eos_teoretisk_mnd} &gt;= ${innParametere.firefemtedelskrav}")
                log_formel("ftt_eos = avrundTilMåneder(ttFramtidigRegnesFra, ttFramtidigRegnesTil)")
                log_formel("ftt_eos = avrundTilMåneder(${innParametere.ttFramtidigRegnesFra.toString()}, ${innParametere.ttFramtidigRegnesTil.toString()})")
                log_formel("ftt_eos = ${innTrygdetid.ttUtlandEos?.ftt_eos}")
                log_formel_slutt()
            }
            kommentar(
                """Dersom faktisk trygdetid medregnet tid i Norge og EØS er større enn 4/5 av
            opptjeningstiden skal framtidig
        trygdetid EØS beregnes uten reduksjon."""
            )

        }

        regel("fttEosRedusert") {
            HVIS { innTrygdetid.ttUtlandEos != null }
            OG { (innTrygdetid.tt_fa_mnd + innTrygdetid.ttUtlandEos?.tt_eos_teoretisk_mnd!!) < innParametere.firefemtedelskrav!! }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.FttEosRedusert")
                innTrygdetid.ttUtlandEos?.ftt_eos = max(0, 480 - innParametere.firefemtedelskrav!!)
                innTrygdetid.ttUtlandEos?.ftt_eos_redusert = true
                log_formel_start("Framtidig trygdetid EØS, tt_fa_mnd ${innTrygdetid.tt_fa_mnd} + tt_eos_teoretisk_mnd ${innTrygdetid.ttUtlandEos?.tt_eos_teoretisk_mnd} &lt; ${innParametere.firefemtedelskrav}")
                log_formel("ftt_eos = max(0, 480 - firefemtedelskrav)")
                log_formel("ftt_eos = max(0, 480 - ${innParametere.firefemtedelskrav})")
                log_formel("ftt_eos = ${innTrygdetid.ttUtlandEos?.ftt_eos}")
                log_formel_slutt()
            }
            kommentar(
                """Dersom faktisk trygdetid medregnet tid i Norge og EØS er mindre enn 4/5 av
            opptjeningstiden
        skal framtidig trygdetid for EØS beregnes med reduksjon.
        Hvis firefemtedelskrav er større enn 480 blir svaret negativt. I så fall settes ftt til
            0."""
            )

        }

        regel("settMerknadAndreFttEosRedusert") {
            HVIS { "fttEosRedusert".harTruffet() }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.UT }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.SettMerknadAndreFttEosRedusert")
                innTrygdetid.ttUtlandEos?.merknadListe?.add(MerknadEnum.BeregnFramtidigTTRS__FttEosRedusert.lagMerknad())
            }
            kommentar(
                """Merknadstekst:
        "Dersom faktisk trygdetid medregnet tid i Norge og EØS er mindre enn 4/5 av opptjeningstiden
            skal fremtidig trygdetid for EØS beregnes med reduksjon""""
            )

        }

        regel("settMerknadUTFttEosRedusert") {
            HVIS { "fttEosRedusert".harTruffet() }
            OG { innParametere.ytelseType == KravlinjeTypeEnum.UT }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.SettMerknadUTFttEosRedusert")
                innTrygdetid.ttUtlandEos?.merknadListe?.add(MerknadEnum.BeregnFramtidigTTRS__SettMerknadUTFttEosRedusert.lagMerknad())
            }
            kommentar(
                """Merknadstekst:
        "Dersom faktisk trygdetid medregnet tid i Norge og EØS er mindre enn 4/5 av opptjeningstiden
            skal fremtidig trygdetid for EØS beregnes med reduksjon""""
            )

        }

        regel("settMerknadGJTFttEosRedusert") {
            HVIS { "fttEosRedusert".harTruffet() }
            OG { innParametere.ytelseType == KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.SettMerknadGJTFttEosRedusert")
                innTrygdetid.ttUtlandEos?.merknadListe?.add(MerknadEnum.BeregnFramtidigTTRS__SettMerknadUTFttEosRedusert.lagMerknad())
            }
            kommentar(
                """Merknadstekst:
        "Dersom faktisk trygdetid medregnet tid i Norge og EØS er mindre enn 4/5 av opptjeningstiden
            skal fremtidig trygdetid for EØS beregnes med reduksjon""""
            )

        }

        regel("fttA10") {
            HVIS { innTrygdetid.ttUtlandKonvensjon != null }
            OG { innTrygdetid.ftt > 0 }
            OG {
                !(innBruker.trygdeavtaledetaljer != null
                        && innBruker.trygdeavtaledetaljer?.ftt_annetNordiskLand != null)
            }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.FttA10")
                innTrygdetid.ttUtlandKonvensjon?.tt_A10_teller = min(480, innTrygdetid.tt_fa_mnd)
                innTrygdetid.ttUtlandKonvensjon?.tt_A10_nevner =
                    min(480, (innTrygdetid.tt_fa_mnd + innTrygdetid.ttUtlandKonvensjon?.tt_A10_fa_mnd!!))
                innTrygdetid.ttUtlandKonvensjon?.ftt_A10_brutto = innTrygdetid.ftt
                innTrygdetid.ttUtlandKonvensjon?.ftt_A10_redusert = innTrygdetid.ftt_redusert
                innTrygdetid.ttUtlandKonvensjon?.ftt_A10_netto =
                    avrund((innTrygdetid.ttUtlandKonvensjon?.ftt_A10_brutto!! * innTrygdetid.ttUtlandKonvensjon?.tt_A10_teller!!.dbl / innTrygdetid.ttUtlandKonvensjon?.tt_A10_nevner!!))
                log_formel_start("Nordisk konvensjon artikkel 10, Teller")
                log_formel("tt_A10_teller = min(480, tt_fa_mnd)")
                log_formel("tt_A10_teller = min(480, ${innTrygdetid.tt_fa_mnd})")
                log_formel("tt_A10_teller = ${innTrygdetid.ttUtlandKonvensjon?.tt_A10_teller}")
                log_formel_slutt()
                log_formel_start("Nordisk konvensjon artikkel 10, Nevner")
                log_formel("tt_A10_nevner = min(480, tt_fa_mnd + tt_A10_fa_mnd)")
                log_formel("tt_A10_nevner = min(480, ${innTrygdetid.tt_fa_mnd} + ${innTrygdetid.ttUtlandKonvensjon?.tt_A10_fa_mnd})")
                log_formel("tt_A10_nevner = ${innTrygdetid.ttUtlandKonvensjon?.tt_A10_nevner}")
                log_formel_slutt()
                log_formel_start("Nordisk konvensjon artikkel 10, Framtidig trygdetid brutto")
                log_formel("ftt_A10_brutto = ftt")
                log_formel("ftt_A10_brutto = ${innTrygdetid.ttUtlandKonvensjon?.ftt_A10_brutto}")
                log_formel_slutt()
                log_formel_start("Nordisk konvensjon artikkel 10, Framtidig trygdetid netto")
                log_formel("ftt_A10_netto = avrund(ftt_A10_brutto * tt_A10_teller/tt_A10_nevner)")
                log_formel("ftt_A10_netto = avrund(${innTrygdetid.ttUtlandKonvensjon?.ftt_A10_brutto} * ${innTrygdetid.ttUtlandKonvensjon?.tt_A10_teller}/${innTrygdetid.ttUtlandKonvensjon?.tt_A10_nevner})")
                log_formel("ftt_A10_netto = ${innTrygdetid.ttUtlandKonvensjon?.ftt_A10_netto}")
                log_formel_slutt()
            }
            kommentar("Framtidig trygdetid iht. Nordisk konvensjon.")

        }

        regel("fttA10OgAngittTTannetNordiskLand") {
            HVIS { innTrygdetid.ttUtlandKonvensjon != null }
            OG { innTrygdetid.ftt > 0 }
            OG { innBruker.trygdeavtaledetaljer != null }
            OG { innBruker.trygdeavtaledetaljer?.ftt_annetNordiskLand != null }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.FttA10OgAngittTTannetNordiskLand")
                val angitt_tt_A10_fa_mnd: Int =
                    innBruker.trygdeavtaledetaljer?.ftt_annetNordiskLand?.antallAr!! * 12 + innBruker.trygdeavtaledetaljer?.ftt_annetNordiskLand?.antallMnd!!
                innTrygdetid.ttUtlandKonvensjon?.tt_A10_teller = min(480, innTrygdetid.tt_fa_mnd)
                innTrygdetid.ttUtlandKonvensjon?.tt_A10_nevner =
                    min(480, (innTrygdetid.tt_fa_mnd + angitt_tt_A10_fa_mnd))
                innTrygdetid.ttUtlandKonvensjon?.ftt_A10_brutto = innTrygdetid.ftt
                innTrygdetid.ttUtlandKonvensjon?.ftt_A10_redusert = innTrygdetid.ftt_redusert
                innTrygdetid.ttUtlandKonvensjon?.ftt_A10_netto =
                    if (innTrygdetid.ttUtlandKonvensjon?.tt_A10_nevner == 0) {
                        0
                    } else {
                        avrund(innTrygdetid.ttUtlandKonvensjon?.ftt_A10_brutto!! * innTrygdetid.ttUtlandKonvensjon?.tt_A10_teller!!.dbl / innTrygdetid.ttUtlandKonvensjon?.tt_A10_nevner!!)
                    }

                log_formel_start("Nordisk konvensjon artikkel 10, Teller")
                log_formel("tt_A10_teller = min(480, tt_fa_mnd)")
                log_formel("tt_A10_teller = min(480, ${innTrygdetid.tt_fa_mnd})")
                log_formel("tt_A10_teller = ${innTrygdetid.ttUtlandKonvensjon?.tt_A10_teller}")
                log_formel_slutt()
                log_formel_start("Nordisk konvensjon artikkel 10, Nevner")
                log_formel("tt_A10_nevner = min(480, tt_fa_mnd + angitt_tt_A10_fa_mnd)")
                log_formel("tt_A10_nevner = min(480, ${innTrygdetid.tt_fa_mnd} + $angitt_tt_A10_fa_mnd)")
                log_formel("tt_A10_nevner = ${innTrygdetid.ttUtlandKonvensjon?.tt_A10_nevner}")
                log_formel_slutt()
                log_formel_start("Nordisk konvensjon artikkel 10, Framtidig trygdetid brutto")
                log_formel("ftt_A10_brutto = ftt")
                log_formel("ftt_A10_brutto = ${innTrygdetid.ttUtlandKonvensjon?.ftt_A10_brutto}")
                log_formel_slutt()
                log_formel_start("Nordisk konvensjon artikkel 10, Framtidig trygdetid netto")
                log_formel("ftt_A10_netto = avrund(ftt_A10_brutto * tt_A10_teller/tt_A10_nevner)")
                log_formel("ftt_A10_netto = avrund(${innTrygdetid.ttUtlandKonvensjon?.ftt_A10_brutto} * ${innTrygdetid.ttUtlandKonvensjon?.tt_A10_teller}/${innTrygdetid.ttUtlandKonvensjon?.tt_A10_nevner})")
                log_formel("ftt_A10_netto = ${innTrygdetid.ttUtlandKonvensjon?.ftt_A10_netto}")
                log_formel_slutt()
            }
            kommentar(
                """Framtidig trygdetid iht. Nordisk konvensjon. Her er faktisk trygdetid annet
            Nordisk land angitt av saksbehandler og 
        brukes fremfor den beregnede verdi."""
            )

        }

        regel("fttTrygdeavtale",  ttUtlandTrygdeavtale) {
            HVIS { (innTrygdetid.tt_fa_mnd + it.tt_fa_mnd) >= innParametere.firefemtedelskrav!! }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.FttTrygdeavtale")
                log_debug("[   ]    kode${it.avtaleland}, tt_fa_mnd ${innTrygdetid.tt_fa_mnd} + ${it.tt_fa_mnd} < ${innParametere.firefemtedelskrav.toString()}")
                val p: TrygdetidPeriodeLengde =
                    finnPeriodeLengde(innParametere.ttFramtidigRegnesFra, innParametere.ttFramtidigRegnesTil)
                it.ftt = avrundTilMåneder(p, true)
                it.ftt_redusert = false
                log_formel_start("Framtidig trygdetid trygdeavtale, tt_fa_mnd ${innTrygdetid.tt_fa_mnd} + trygdeavtale.tt_fa_mnd ${it.tt_fa_mnd} &gt;= ${innParametere.firefemtedelskrav}")
                log_formel("ftt = avrundTilMåneder(ttFramtidigRegnesFra, ttFramtidigRegnesTil)")
                log_formel("ftt = avrundTilMåneder(${innParametere.ttFramtidigRegnesFra.toString()}, ${innParametere.ttFramtidigRegnesTil.toString()})")
                log_formel("ftt = ${it.ftt}")
                log_formel_slutt()
            }
            kommentar("Framtidig trygdetid for trygdeavtale.")
        }

        regel("fttTrygdeavtaleRedusert",  ttUtlandTrygdeavtale) {
            HVIS { (innTrygdetid.tt_fa_mnd + it.tt_fa_mnd) < innParametere.firefemtedelskrav!! }
            SÅ {
                log_debug("[HIT] BeregnFramtidigTTRS.FttTrygdeavtaleRedusert")
                it.merknadListe.add(MerknadEnum.BeregnFramtidigTTRS__FttTrygdeavtaleRedusert.lagMerknad())
                it.ftt = max(0, 480 - innParametere.firefemtedelskrav!!)
                innTrygdetid.ftt_fom = innParametere.ttFramtidigRegnesFra
                it.ftt_redusert = true
                log_formel_start("Framtidig trygdetid trygdeavtale, tt_fa_mnd ${innTrygdetid.tt_fa_mnd} + trygdeavtale.tt_fa_mnd ${it.tt_fa_mnd} &lt; ${innParametere.firefemtedelskrav}")
                log_formel("ftt = max(0, 480 - firefemtedelskrav)")
                log_formel("ftt = max(0, 480 - ${innParametere.firefemtedelskrav})")
                log_formel("ftt = ${it.ftt}")
                log_formel_slutt()
            }
            kommentar("Redusert framtidig trygdetid for trygdeavtale.")
        }
    }

}
