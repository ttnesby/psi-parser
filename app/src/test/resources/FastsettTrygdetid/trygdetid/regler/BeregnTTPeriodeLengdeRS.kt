package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.function.finnPeriodeLengde
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidPeriodeLengde
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidVariable
import no.nav.domain.pensjon.regler.repository.merknad.MerknadEnum
import no.nav.pensjon.regler.internal.domain.TTPeriode
import no.nav.pensjon.regler.internal.domain.Trygdetid
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.preg.system.helper.toLocalDate
import java.util.*

/**
 * Hvis åpen periode så brukes sluttdato lik øvre grense for opptjening av faktisk trygdetid.
 */
class BeregnTTPeriodeLengdeRS(
    private val innPeriode: TTPeriode?,
    private val innTrygdetid: Trygdetid?,
    private val innParametere: TrygdetidVariable?
) : AbstractPensjonRuleset<TrygdetidPeriodeLengde>() {
    private var fom: Date? = innPeriode?.fom
    private var tom: Date? = innPeriode?.tom

    override fun create() {

        regel("ÅpenPeriode") {
            HVIS { innPeriode?.tom == null }
            SÅ {
                log_debug("[HIT] BeregnTTperiodeRS.ÅpenPeriode")
                tom = innParametere?.ttFaktiskRegnesTil!!
            }
            kommentar(
                """Hvis åpen periode så brukes sluttdato lik øvre grense for opptjening av faktisk trygdetid."""
            )
        }

        regel("SettMerknadAndrePeriodeStartAvkortes") {
            HVIS { fom!!.toLocalDate() < innParametere?.ttFaktiskRegnesFra!!.toLocalDate() }
            OG { innParametere?.ytelseType != KravlinjeTypeEnum.UT }
            OG { innParametere?.ytelseType != KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnTTperiodeRS.SettMerknadAndrePeriodeStartAvkortes")
                innTrygdetid?.merknadListe?.add(MerknadEnum.BeregnTTperiodeRS__PeriodeStartAvkortes.lagMerknad())
            }
            kommentar(
                """Merknadstekst: "Hvis trygdetidperiode går over nedre grense for opptjening av trygdetid avkortes denne.""""
            )
        }

        regel("SettMerknadUTPeriodeStartAvkortes") {
            HVIS { fom!!.toLocalDate() < innParametere?.ttFaktiskRegnesFra!!.toLocalDate() }
            OG { innParametere?.ytelseType == KravlinjeTypeEnum.UT }
            SÅ {
                log_debug("[HIT] BeregnTTperiodeRS.SettMerknadUTPeriodeStartAvkortes")
                innTrygdetid?.merknadListe?.add(MerknadEnum.BeregnTTperiodeRS__SettMerknadUTPeriodeStartAvkortes.lagMerknad())
            }
            kommentar(
                """Merknadstekst: "Hvis trygdetidperiode går over nedre grense for opptjening av trygdetid avkortes denne.""""
            )
        }

        regel("SettMerknadGJTPeriodeStartAvkortes") {
            HVIS { fom!!.toLocalDate() < innParametere?.ttFaktiskRegnesFra!!.toLocalDate() }
            OG { innParametere?.ytelseType == KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnTTperiodeRS.SettMerknadGJTPeriodeStartAvkortes")
                innTrygdetid?.merknadListe?.add(MerknadEnum.BeregnTTperiodeRS__SettMerknadUTPeriodeStartAvkortes.lagMerknad())
            }
            kommentar(
                """Merknadstekst: "Hvis trygdetidperiode går over nedre grense for opptjening av trygdetid avkortes denne.""""
            )
        }

        regel("PeriodeStartAvkortes") {
            HVIS { fom!!.toLocalDate() < innParametere?.ttFaktiskRegnesFra!!.toLocalDate() }
            SÅ {
                log_debug("[HIT] BeregnTTperiodeRS.PeriodeStartAvkortes")
                fom = innParametere?.ttFaktiskRegnesFra!!
                log_debug("[   ]    innParametere.ytelseType : ${innParametere.ytelseType}")
            }
            kommentar(
                """Hvis trygdetidperiode går over nedre grense for opptjening av trygdetid avkortes denne."""
            )
        }

        regel("SettMerknadAndrePeriodeSluttAvkortes") {
            HVIS { tom != null } // nullsjekk ny i v2
            OG { innParametere?.ttFaktiskRegnesTil != null } // nullsjekk ny i v2
            OG { tom!!.toLocalDate() > innParametere?.ttFaktiskRegnesTil!!.toLocalDate() }
            OG { innParametere?.ytelseType != KravlinjeTypeEnum.UT }
            OG { innParametere?.ytelseType != KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnTTperiodeRS.SettMerknadAndrePeriodeSluttAvkortes")
                innTrygdetid?.merknadListe?.add(MerknadEnum.BeregnTTperiodeRS__PeriodeSluttAvkortes.lagMerknad())
            }
            kommentar(
                """Merknadstekst: "Hvis trygdetidperiode går over øvre grense for opptjening av trygdetid avkortes denne.""""
            )
        }

        regel("SettMerknadUTPeriodeSluttAvkortes") {
            HVIS { tom != null } // nullsjekk ny i v2
            OG { innParametere?.ttFaktiskRegnesTil != null } // nullsjekk ny i v2
            OG { tom!!.toLocalDate() > innParametere?.ttFaktiskRegnesTil!!.toLocalDate() }
            OG { innParametere?.ytelseType == KravlinjeTypeEnum.UT }
            SÅ {
                log_debug("[HIT] BeregnTTperiodeRS.SettMerknadUTPeriodeSluttAvkortes")
                innTrygdetid?.merknadListe?.add(MerknadEnum.BeregnTTperiodeRS__SettMerknadUTPeriodeSluttAvkortes.lagMerknad())
            }
            kommentar(
                """Merknadstekst: "Hvis trygdetidperiode går over øvre grense for opptjening av trygdetid avkortes denne.""""
            )
        }

        regel("SettMerknadGJTPeriodeSluttAvkortes") {
            HVIS { tom != null } // nullsjekk ny i v2
            OG { innParametere?.ttFaktiskRegnesTil != null } // nullsjekk ny i v2
            HVIS { tom!!.toLocalDate() > innParametere?.ttFaktiskRegnesTil!!.toLocalDate() }
            OG { innParametere?.ytelseType == KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnTTperiodeRS.SettMerknadGJTPeriodeSluttAvkortes")
                innTrygdetid?.merknadListe?.add(MerknadEnum.BeregnTTperiodeRS__SettMerknadUTPeriodeSluttAvkortes.lagMerknad())
            }
            kommentar(
                """Merknadstekst: "Hvis trygdetidperiode går over øvre grense for opptjening av trygdetid avkortes denne.""""
            )
        }

        regel("PeriodeSluttAvkortes") {
            HVIS { tom != null } // nullsjekk ny i v2
            OG { innParametere?.ttFaktiskRegnesTil != null } // nullsjekk ny i v2
            HVIS { tom!!.toLocalDate() > innParametere?.ttFaktiskRegnesTil!!.toLocalDate() }
            SÅ {
                log_debug("[HIT] BeregnTTperiodeRS.PeriodeSluttAvkortes")
                tom = innParametere?.ttFaktiskRegnesTil!!
            }
            kommentar(
                """Hvis trygdetidperiode går over øvre grense for opptjening av trygdetid avkortes denne."""
            )
        }

        regel("PeriodeLengde") {
            HVIS { fom != null }
            OG { tom != null }
            SÅ {
                log_debug("[HIT] BeregnTTperiodeRS.PeriodeLengde")
                RETURNER(finnPeriodeLengde(fom, tom))
            }
            kommentar("Returner periodens lengde etter eventuelle justeringer.")

        }
    }
}