package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.function.avrundPoengtall
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.function.beregnPoengtallAvOpptjeningListe
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.function.opprettBeregningsPeriode
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.klasser.PoengrekkeParameter
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.klasser.PoengrekkeVariable
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.regler.OpprettPoengrekkeResultatRS
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.AvtaleLandEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.LandkodeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidVariable
import no.nav.domain.pensjon.regler.repository.merknad.MerknadEnum
import no.nav.pensjon.regler.internal.domain.TTPeriode
import no.nav.pensjon.regler.internal.domain.Trygdetid
import no.nav.pensjon.regler.internal.domain.beregning.Beregning
import no.nav.pensjon.regler.internal.domain.beregning.Poengtall
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.preg.system.helper.*
import no.nav.system.rule.dsl.pattern.SinglePattern
import no.nav.system.rule.dsl.pattern.createPattern

/**
 * CR 182991:
 * Ved fastsetting av trygdetiden, gi en merknad dersom det ikke er registrert trygdetidsperiode
 * for en periode hvor det finnes poengår i opptjeningsregisteret. Det er kun norske
 * trygdetidsperioder
 * og norske poengår som sammenlignes og som kan gi slik merknad.
 */
class SammenlignPerioderOgPoengårRS(
    private val innTTPeriodeListe: List<TTPeriode>,
    private val innBruker: Persongrunnlag,
    private val innParam: TrygdetidVariable,
    private val innTrygdetid: Trygdetid?,
    private val innBeregning: Beregning?
) : AbstractPensjonRuleset<Unit>() {

    private var poengrekkeParam: PoengrekkeParameter =
        PoengrekkeParameter(variable = PoengrekkeVariable())
    private val noenPoengårNorge: SinglePattern<Poengtall> = mutableListOf<Poengtall>().createPattern { it.pp > 0.0 }
    private val noenPoengårPeriodeNorge: SinglePattern<TTPeriode> = mutableListOf<TTPeriode>().createPattern()

    override fun create() {

        regel("BeregnFaktiskePoengår") {
            HVIS { innBeregning != null }
            OG { innBruker.opptjeningsgrunnlagListe.minst(1) { it.pi > 0 } }
            SÅ {
                log_debug("[HIT] SammenlignPerioderOgPoengårRS.BeregnFaktiskePoengår")
                val beregning = Beregning(innBeregning)
                OpprettPoengrekkeResultatRS(beregning, innBruker, poengrekkeParam).run(this)
                opprettBeregningsPeriode(
                    innParam.ttFaktiskRegnesFra?.år!!,
                    innParam.ttFaktiskRegnesTil?.år!!,
                    beregning,
                    poengrekkeParam,
                    (innParam.ttFaktiskRegnesFra!!.år..innParam.ttFaktiskRegnesTil!!.år).associateWith { år ->
                        Veiet_grunnbeløp(
                            år
                        )
                    }
                )
                noenPoengårNorge.clearAndAddAll(beregning.tp?.spt?.poengrekke?.poengtallListe!!)
                beregnPoengtallAvOpptjeningListe(
                    innBruker.opptjeningsgrunnlagListe,
                    innParam.ttFaktiskRegnesTil?.år,
                    poengrekkeParam.variable,
                    this
                )
                avrundPoengtall(poengrekkeParam.variable?.poengtallListe!!)
            }
            kommentar("""Beregn faktiske norske poengår for det samme tidsrom som faktisk trygdetid beregnes.""")
        }

        regel("BestemPoengårPerioder", noenPoengårNorge) {
            HVIS { it.ar < innParam.ttFaktiskRegnesTil?.år!! }
            SÅ {
                noenPoengårPeriodeNorge.add(
                    TTPeriode(
                        fom = date(it.ar, 1, 1),
                        tom = date(it.ar, 12, 31)
                    )
                )
            }
            kommentar("""Opprett poengårPeriodeListe ved å legge til en TTperiode på ett år for hvert poengår.""")
        }

        regel("BestemPoengårPerioder1", noenPoengårNorge) {
            HVIS { it.ar == innParam.ttFaktiskRegnesTil?.år }
            SÅ {
                noenPoengårPeriodeNorge.add(
                    TTPeriode(
                        fom = date(it.ar, 1, 1),
                        tom = innParam.ttFaktiskRegnesTil
                    )
                )
            }
            kommentar(
                """Opprett poengårPeriodeListe ved å legge til en TTperiode på ett år for hvert poengår.
        Periode skal ikke overstige dato faktisk trygdetid regnes til."""
            )
        }

        regel("PoengårOgIkkeTrygdetidsperiode", noenPoengårPeriodeNorge) { noenPoengårPeriodeNorgeIterator ->
            HVIS {
                innTTPeriodeListe.akkurat(0) { TTPeriodeListe ->
                    TTPeriodeListe.land == LandkodeEnum.NOR
                            && (noenPoengårPeriodeNorgeIterator.fom?.toLocalDate())!! >= (TTPeriodeListe.fom?.toLocalDate())
                            && (TTPeriodeListe.tom != null && (noenPoengårPeriodeNorgeIterator.tom?.toLocalDate())!! <= (TTPeriodeListe.tom?.toLocalDate()))
                }
            }
            SÅ {
                val poengår: Int = (noenPoengårPeriodeNorgeIterator.fom)?.år!!
                log_debug("[HIT] SammenlignPerioderOgPoengårRS.PoengårOgIkkeTrygdetidsperiode, poengår = $poengår")
                innTrygdetid?.merknadListe?.add(
                    MerknadEnum.SammenlignPerioderOgPoengårRS__PoengårOgIkkeTrygdetidsperiode.lagMerknad(
                        mutableListOf(poengår.toString())
                    )
                )
            }
            kommentar(
                """Gi merknad dersom det finnes poengår i Norge som ikke er helt dekket av en
            norsk trygdetidsperiode."""
            )
        }
    }
}
