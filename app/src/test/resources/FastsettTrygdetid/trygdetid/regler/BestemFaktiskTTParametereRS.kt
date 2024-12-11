package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.AvtaletypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.LandkodeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.regler.AvklarUnntakHalvMinstepensjonRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidVariable
import no.nav.domain.pensjon.regler.repository.merknad.MerknadEnum
import no.nav.pensjon.regler.internal.domain.Trygdetid
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.preg.system.helper.*
import java.util.*

/**
 * PK-5610: Restdager skal rundes ned når det er unntak halv minstepensjon ( etter 1991 )
 */
class BestemFaktiskTTParametereRS(
    private val innParametere: TrygdetidVariable?,
    private val innBruker: Persongrunnlag?,
    private val innFørsteVirk: Date?,
    private val innTrygdetid: Trygdetid?
) : AbstractPensjonRuleset<Unit>() {
    private var unntakHalvMinstepensjon: Boolean = false

    override fun create() {

        regel("BestemUnntakHalvMinstepensjon") {
            HVIS { innBruker?.inngangOgEksportGrunnlag != null }
            OG { innBruker?.inngangOgEksportGrunnlag?.unntakFraForutgaendeMedlemskap != null }
            SÅ {
                unntakHalvMinstepensjon =
                    AvklarUnntakHalvMinstepensjonRS(innBruker!!, innParametere?.ytelseType).run(this)
            }
            kommentar(
                """Avgjør om bruker har unntak halv minstepensjon. Dette påvirker hvordan
            avrunding skal gjøres ved uførepensjon."""
            )
        }

        regel("Årskull") {
            HVIS { innBruker?.fodselsdato != null }
            OG { innParametere?.årskull == null }
            SÅ {
                innParametere?.årskull = (innBruker?.fodselsdato)?.år
            }
            kommentar("Finn årskull")
        }

        regel("SekstenÅr") {
            HVIS { innParametere?.datoFyller16 == null }
            SÅ {
                innParametere?.datoFyller16 = (innBruker?.fodselsdato)!! + 16.år
                log_debug("[HIT] BestemFaktiskTTParametereRS.SekstenÅr, datoFyller16=${innParametere?.datoFyller16}")
            }
            kommentar("Finner dato vedkommende fylte 16 år.")
        }

        regel("TrygdetidFra16År") {
            HVIS { innParametere?.datoFyller16 != null }
            SÅ {
                innParametere?.ttFaktiskRegnesFra = innParametere?.datoFyller16
                log_debug("[HIT] BestemFaktiskTTParametereRS.TrygdetidFra16År, TT fra ${innParametere?.ttFaktiskRegnesFra}")
            }
            kommentar("Trygdetid regnes fra og med den dagen vedkommende fylte 16 år.")
        }

        regel("SettMerknadAndreTrygdetidTidligstFra1937") {
            HVIS { innParametere?.ttFaktiskRegnesFra!!.toLocalDate() < localDate(1937, 1, 1) }
            OG {
                !(innFørsteVirk!!.toLocalDate() < localDate(1991, 1, 1)
                        && innBruker?.bosattLand == LandkodeEnum.NOR)
            }
            OG { innParametere?.ytelseType != KravlinjeTypeEnum.UT }
            OG { innParametere?.ytelseType != KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BestemFaktiskTTParametereRS.SettMerknadAndreTrygdetidTidligstFra1937")
                innTrygdetid?.merknadListe?.add(MerknadEnum.BestemFaktiskTTParametereRS__TrygdetidTidligstFra1937.lagMerknad())
            }

            kommentar(
                """Merkandstekst:
        "Trygdetid regnes fra fylte 16 år men tidligst fra og med 1. januar 1937 selv om dette er
            etter fylte 16 år. Før 1. januar 1991 kunne personer bosatt i Norge få medregnet
            trygdetid også for tidsrom før 1. januar 1937.""""
            )

        }

        regel("SettMerknadUTTrygdetidTidligstFra1937") {
            HVIS { innParametere?.ttFaktiskRegnesFra!!.toLocalDate() < localDate(1937, 1, 1) }
            OG {
                !(innFørsteVirk!!.toLocalDate() < localDate(1991, 1, 1)
                        && innBruker?.bosattLand == LandkodeEnum.NOR)
            }
            OG { innParametere?.ytelseType == KravlinjeTypeEnum.UT }
            SÅ {
                log_debug("[HIT] BestemFaktiskTTParametereRS.SettMerknadUTTrygdetidTidligstFra1937")
                innTrygdetid?.merknadListe?.add(MerknadEnum.BestemFaktiskTTParametereRS__SettMerknadUTTrygdetidTidligstFra1937.lagMerknad())
            }

            kommentar(
                """Merkandstekst:
        "Trygdetid regnes fra fylte 16 år men tidligst fra og med 1. januar 1937 selv om dette er
            etter fylte 16 år. Før 1. januar 1991 kunne personer bosatt i Norge få medregnet
            trygdetid også for tidsrom før 1. januar 1937.""""
            )

        }

        regel("SettMerknadGJTTrygdetidTidligstFra1937") {
            HVIS { innParametere?.ttFaktiskRegnesFra!!.toLocalDate() < localDate(1937, 1, 1) }
            OG {
                !(innFørsteVirk!!.toLocalDate() < localDate(1991, 1, 1)
                        && innBruker?.bosattLand == LandkodeEnum.NOR)
            }
            OG { innParametere?.ytelseType == KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BestemFaktiskTTParametereRS.SettMerknadGJTTrygdetidTidligstFra1937")
                innTrygdetid?.merknadListe?.add(MerknadEnum.BestemFaktiskTTParametereRS__SettMerknadUTTrygdetidTidligstFra1937.lagMerknad())
            }

            kommentar(
                """Merkandstekst:
        "Trygdetid regnes fra fylte 16 år men tidligst fra og med 1. januar 1937 selv om dette er
            etter fylte 16 år. Før 1. januar 1991 kunne personer bosatt i Norge få medregnet
            trygdetid også for tidsrom før 1. januar 1937.""""
            )

        }

        regel("TrygdetidTidligstFra1937") {
            HVIS { innParametere?.ttFaktiskRegnesFra!!.toLocalDate() < localDate(1937, 1, 1) }
            OG {
                !(innFørsteVirk!!.toLocalDate() < localDate(1991, 1, 1)
                        && innBruker?.bosattLand == LandkodeEnum.NOR)
            }
            SÅ {
                innParametere?.ttFaktiskRegnesFra = date(1937, 1, 1)
                log_debug("[HIT] BestemFaktiskTTParametereRS.TrygdetidTidligstFra1937, TT fra ${innParametere?.ttFaktiskRegnesFra}")
            }
            kommentar(
                """Trygdetid regnes fra fylte 16 år men tidligst fra og med 1. januar 1937 selv
            om dette er etter fylte 16 år.
        Før 1. januar 1991 kunne personer bosatt i Norge få medregnet trygdetid også for tidsrom før
            1. januar 1937.
        TODO: her kommer konverteringsflagg istedenfor bosattLand."""
            )

        }

        regel("Etter1991MaksGrense") {
            HVIS { innFørsteVirk!!.toLocalDate() >= localDate(1991, 1, 1) }
            SÅ {
                innParametere?.ttFaktiskMaksGrense = date(innBruker?.fodselsdato?.år!! + 66, 12, 31)
                log_debug("[HIT] BestemFaktiskTTParametereRS.Etter1991MaksGrense, ${innParametere?.ttFaktiskMaksGrense}")
            }
            kommentar(
                """For førstevirk 1991 eller senere regnes faktisk trygdetid ut året fyller
            66."""
            )

        }

        regel("Før1991MaksGrense") {
            HVIS { innFørsteVirk!!.toLocalDate() < localDate(1991, 1, 1) }
            OG { innFørsteVirk!!.toLocalDate() >= localDate(1973, 1, 1) }
            SÅ {
                innParametere?.ttFaktiskMaksGrense = (innBruker?.fodselsdato)!! + 67.år
                log_debug("[HIT] BestemFaktiskTTParametereRS.Før1991MaksGrense, ${innParametere?.ttFaktiskMaksGrense}")
            }
            kommentar(
                """For førstevirk 1973 eller senere og før 1991 regnes faktisk trygdetid til dato
            fyller 67."""
            )

        }

        regel("Før1973MaksGrense") {
            HVIS { innFørsteVirk!!.toLocalDate() < localDate(1973, 1, 1) }
            SÅ {
                innParametere?.ttFaktiskMaksGrense = (innBruker?.fodselsdato)!! + 70.år
                log_debug("[HIT] BestemFaktiskTTParametereRS.Før1973MaksGrense, ${innParametere?.ttFaktiskMaksGrense}")
            }
            kommentar("For førstevirk før 1973 regnes faktisk trygdetid til dato fyller 70.")

        }

        regel("BestemFttBeregnes") {
            HVIS { innParametere?.fttBeregnes == null }
            SÅ {
                innParametere?.fttBeregnes = BestemFramtidigTTBeregnesRS(innBruker, innParametere).run(this)
            }
            kommentar("Bestem om framtidig trygdetid beregnes")

        }

        regel("RestDagerRundesOppVirkFør1991") {
            HVIS { innFørsteVirk!!.toLocalDate() < localDate(1991, 1, 1) }
            SÅ {
                log_debug("[HIT] BestemFaktiskTTParametereRS.RestDagerRundesOppVirkFør1991")
                innParametere?.restDagerRundesOpp = true
            }
            kommentar("Hvis VIRK er før 1991 så rundes rest dager opp.")

        }

        regel("RestDagerRundesOpp") {
            HVIS { innParametere?.fttBeregnes!! }
            OG { !unntakHalvMinstepensjon }
            OG { innFørsteVirk!!.toLocalDate() >= localDate(1991, 1, 1) }
            SÅ {
                log_debug("[HIT] BestemFaktiskTTParametereRS.RestDagerRundesOpp")
                innParametere?.restDagerRundesOpp = true
            }
            kommentar("Restdager skal rundes ned når det er unntak halv minstepensjon.")

        }

        regel("RestDagerRundesNed") {
            HVIS { innFørsteVirk!!.toLocalDate() >= localDate(1991, 1, 1) }
            OG { "RestDagerRundesOpp".harIkkeTruffet() }
            SÅ {
                log_debug("[HIT] BestemFaktiskTTParametereRS.RestDagerRundesNed")
                innParametere?.restDagerRundesOpp = false
            }
            kommentar(
                """Hvis ikke ftt beregnes og VIRK etter 1991 så rundes ikke rest dager opp til
            nærmeste måned."""
            )

        }

        regel("Trygdeavtale") {
            HVIS { innBruker?.trygdeavtale != null }
            OG { innBruker?.trygdeavtale?.avtaleType != null }
            SÅ {
                innParametere?.avtaletype = innBruker?.trygdeavtale?.avtaleType
            }
            kommentar("Finn avtaletype for trygdeavtale")

        }

    }
}