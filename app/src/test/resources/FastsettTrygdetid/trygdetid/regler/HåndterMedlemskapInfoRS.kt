package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidGrunnlag
import no.nav.pensjon.regler.internal.domain.beregning2011.BeregningsvilkarPeriode
import no.nav.pensjon.regler.internal.domain.grunnlag.InngangOgEksportGrunnlag
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset

/**
 * PK-6559: I forbindelse med uføretrygd blir informasjon tilhørende inngang og eksportgrunnlag
 * fordelt på to beregningsvilkår: FortsattMedlemskap og ForutgaendeMedlemskap.
 * Dette regelsett gjør om disse beregningsvilkår til et InngangOgEksportgrunnlag og setter denne på
 * bruker.
 */
class HåndterMedlemskapInfoRS(
    private val innGrunnlag: TrygdetidGrunnlag?
) : AbstractPensjonRuleset<Unit>() {
    private var bvpSiste: BeregningsvilkarPeriode? = null

    override fun create() {

        regel("FinnBvpSiste") {
            HVIS { innGrunnlag != null }
            OG { innGrunnlag?.beregningsvilkarsPeriodeListe?.size!! > 0 }
            SÅ {
                bvpSiste =
                    innGrunnlag?.beregningsvilkarsPeriodeListe?.get(innGrunnlag.beregningsvilkarsPeriodeListe.size - 1)
            }
            kommentar("Hjelperegel for å finne siste beregningsvilkårsperiode.")

        }

        regel("SettInngangOgEksport") {
            HVIS { bvpSiste != null }
            OG { (bvpSiste?.fortsattMedlemskap != null || bvpSiste?.forutgaendeMedlemskap != null) }
            SÅ {
                log_debug("[HIT] HåndterMedlemskapInfoRS.SettInngangOgEksport")
                innGrunnlag?.bruker = Persongrunnlag(innGrunnlag.bruker!!)
                innGrunnlag!!.bruker?.inngangOgEksportGrunnlag =
                    InngangOgEksportGrunnlag(bvpSiste?.fortsattMedlemskap, bvpSiste?.forutgaendeMedlemskap)
            }
            kommentar(
                """Hvis beregningsvilkårsperiode inneholder fortsattMedlemskap og/eller
            forutgaendeMedlemskap
        så opprettes et InngangOgEksportgrunnlag på en kopi av bruker."""
            )

        }

    }
}