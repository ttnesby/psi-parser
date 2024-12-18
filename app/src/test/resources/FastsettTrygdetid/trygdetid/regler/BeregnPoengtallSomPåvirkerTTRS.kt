package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.function.avrundPoengtall
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.function.beregnPoengtallAvOpptjeningListe
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.function.opprettBeregningsPeriode
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.klasser.PoengrekkeParameter
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.klasser.PoengrekkeVariable
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.regler.BeregnSluttÅrRS
import no.nav.domain.pensjon.regler.repository.komponent.poengrekke.regler.OpprettPoengrekkeResultatRS
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.RegelverkTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidVariable
import no.nav.pensjon.regler.internal.domain.beregning.Beregning
import no.nav.pensjon.regler.internal.domain.beregning.Poengtall
import no.nav.pensjon.regler.internal.domain.beregning.Sluttpoengtall
import no.nav.pensjon.regler.internal.domain.grunnlag.Opptjeningsgrunnlag
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.pensjon.regler.internal.domain.vedtak.VilkarsVedtak
import no.nav.preg.system.helper.*
import java.util.*

class BeregnPoengtallSomPåvirkerTTRS(
    private val innBruker: Persongrunnlag?,
    private val innBeregning: Beregning?,
    private val innParametere: TrygdetidVariable?,
    private val innVirk: Date?
) : AbstractPensjonRuleset<Unit>() {
    private val årFyller67: Int = (innBruker?.fodselsdato!! + 67.år).år
    private val årFyller69: Int = (innBruker?.fodselsdato!! + 69.år).år
    private val årFyller75: Int = (innBruker?.fodselsdato!! + 75.år).år
    private val opptjeningsgrunnlagListe: MutableList<Opptjeningsgrunnlag> = innBruker?.opptjeningsgrunnlagListe!!
    private val poengrekkeParam: PoengrekkeParameter = PoengrekkeParameter(variable = PoengrekkeVariable())
    private var spt: Sluttpoengtall? = null
    private var poengtallArray: List<Poengtall>? = listOf()

    override fun create() {

        regel("InitialiserPoengrekkeGammeltRegelverk") {
            HVIS { innParametere?.regelverkType == RegelverkTypeEnum.G_REG }
            OG { spt == null }
            OG { innBruker?.opptjeningsgrunnlagListe != null }
            OG { opptjeningsgrunnlagListe.minst(1) { it.pi > 0 } }
            SÅ {
                log_debug("[HIT] BeregnPoengtallSomPåvirkerTTRS.InitialiserPoengrekkeGammeltRegelverk")
                OpprettPoengrekkeResultatRS(innBeregning, innBruker, poengrekkeParam).run(this)
                opprettBeregningsPeriode(
                    årFyller67,
                    årFyller69,
                    innBeregning!!,
                    poengrekkeParam,
                    (årFyller67..årFyller69).associateWith { år -> Veiet_grunnbeløp(år) }
                )
                spt = innBeregning.tp?.spt
                poengtallArray = spt?.poengrekke?.poengtallListe
            }
            kommentar(
                """Årskull før 1943: Initialiser en poengrekke for beregning av poengtall for
            årstall fra brukeren fyller 67 frem til 69."""
            )
        }

        regel("InitialiserPoengrekkeNyttRegelverk") {
            HVIS { innParametere?.regelverkType != RegelverkTypeEnum.G_REG }
            OG { spt == null }
            OG { innBruker?.opptjeningsgrunnlagListe != null }
            OG { opptjeningsgrunnlagListe.minst(1) { it.pi > 0 } }
            SÅ {
                log_debug("[HIT] BeregnPoengtallSomPåvirkerTTRS.InitialiserPoengrekkeNyttRegelverk")
                OpprettPoengrekkeResultatRS(innBeregning, innBruker, poengrekkeParam).run(this)
                opprettBeregningsPeriode(
                    årFyller67,
                    årFyller75,
                    innBeregning!!,
                    poengrekkeParam,
                    (årFyller67..årFyller75).associateWith { år -> Veiet_grunnbeløp(år) }
                )
                spt = innBeregning.tp?.spt
            }
            kommentar(
                """Årskull etter 1942: Initialiser en poengrekke for beregning av poengtall for
            årstall fra brukeren fyller 67 frem til 75."""
            )
        }

        regel("BeregnPoengtallGammeltRegelverk") {
            HVIS { innParametere?.regelverkType == RegelverkTypeEnum.G_REG }
            OG { spt?.poengrekke != null }
            OG { innBruker?.opptjeningsgrunnlagListe != null }
            OG { (opptjeningsgrunnlagListe.minst(1) { it.pi > 0 }) }
            SÅ {
                log_debug("[HIT] BeregnPoengtallSomPåvirkerTTRS.BeregnPoengtallGammeltRegelverk ${innParametere?.ytelseType} ${innBruker?.sisteGyldigeOpptjeningsAr}")
                poengtallArray = spt?.poengrekke?.poengtallListe!!
                val vedtaket = VilkarsVedtak(kravlinjeType = innParametere?.ytelseType!!)
                BeregnSluttÅrRS(
                    vedtaket,
                    innBruker!!,
                    innVirk!!,
                    spt?.poengrekke!!,
                    innBeregning,
                    null,
                    innParametere.regelverkType
                ).run(this)
                beregnPoengtallAvOpptjeningListe(
                    opptjeningsgrunnlagListe,
                    spt?.poengrekke?.sluttar,
                    poengrekkeParam.variable,
                    this
                )
                //   apply BeregnFaktiskePoengtallRS(poengtallListe, opptjeningsgrunnlagListe, spt.poengrekke.sluttar);
                avrundPoengtall(poengrekkeParam.variable?.poengtallListe!!)
            }
            kommentar(
                """Årskull før 1943: Beregn poengtall for årstall fra brukeren fyller 67 frem til
            69."""
            )
        }

        regel("BeregnPoengtallNyttRegelverk") {
            HVIS { innParametere?.regelverkType != RegelverkTypeEnum.G_REG }
            OG { spt?.poengrekke != null }
            OG { innBruker?.opptjeningsgrunnlagListe != null }
            OG { (opptjeningsgrunnlagListe.minst(1) { it.pi > 0 }) }
            SÅ {
                log_debug("[HIT] BeregnPoengtallSomPåvirkerTTRS.BeregnPoengtallNyttRegelverk")
                poengtallArray = spt?.poengrekke?.poengtallListe!!
                var sluttar = min(årFyller75, innVirk?.år!! - 1)
                sluttar = max(årFyller67, sluttar)
                sluttar = min(sluttar, innBruker?.sisteGyldigeOpptjeningsAr!!)
                beregnPoengtallAvOpptjeningListe(opptjeningsgrunnlagListe, sluttar, poengrekkeParam.variable, this)
                avrundPoengtall(poengrekkeParam.variable?.poengtallListe!!)
            }
        }

        regel("MidlertidigPoengtallFra68til69årGammeltRegelverk") {
            HVIS { innParametere?.regelverkType == RegelverkTypeEnum.G_REG }
            OG { opptjeningsgrunnlagListe.ingen { it.ar == årFyller69 } }
            OG { poengrekkeParam.variable?.poengtallListe?.get(årFyller69 - 1) != null }
            OG { poengrekkeParam.variable?.poengtallListe?.get(årFyller69) != null }
            SÅ {
                log_debug(
                    "[HIT] BeregnPoengtallSomPåvirkerTTRS.MidlertidigPoengtallFra68til69årGammeltRegelverk    poengtall68: ${
                        poengrekkeParam.variable?.poengtallListe?.get(
                            årFyller69 - 1
                        )?.pp
                    }   til   poengtall69: ${poengrekkeParam.variable?.poengtallListe?.get(årFyller69)?.pp}"
                )
                poengrekkeParam.variable?.poengtallListe?.get(årFyller69)?.pp =
                    poengrekkeParam.variable?.poengtallListe?.get(årFyller69 - 1)?.pp!!
            }
            kommentar(
                """Gamle regler (AP1967): Hvis ingen opptjening er angitt for år fyller 69 så
            kopieres poengtall fra år fyller 68 til år fyller 69"""
            )
        }
    }
}