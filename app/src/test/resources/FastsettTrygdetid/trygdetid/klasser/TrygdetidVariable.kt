package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.*
import java.util.*

/**
 * Beskrivelse av felter:
 * =======================
 * datoFyller16Dato bruker fyller 16 år
 * ttFaktiskRegnesFraFaktisk trygdetid regnes fra dato
 * ttFaktiskRegnesTilFaktisk trygdetid regnes til dato
 * ttFaktiskMaksGrenseMaksimal grense for faktisk trygdetid. Svarer til dato for overgang
 * alderspensjon.
 * ttFramtidigRegnesFraFramtidig trygdetid regnes fra dato
 * ttFramtidigRegnesTilFramtidig trygdetid regnes til dato
 * opptjeningRegnesFraOpptjening regnes fra dato
 * opptjeningRegnesTilOpptjening renges til dato
 * firefemtedelskravKrav til faktisk trygdetid i forhold til 4/5 regelen
 * ytelseTypeHvilken pensjonsytelse trygdetid skal fastsettes for
 * fttBeregnesOm framtidig trygdetid skal beregnes
 * restDagerRundesOppOm rest dager skal rundes opp til nærmeste måned
 * avtaletypeHvilken trygdeavtale som gjelder
 * årskullHvilket årskull bruker tilhører
 * regelverkTypeHvilket regelverk som gjelder for AP
 * sisteUFTSiste uføretidspunkt
 * kapittel20Om trygdetid kapittel 20 skal beregnes
 * garantiTypeOm uførehistorikk (§3-5 eller §3-7) påvirker fastsettelse av trygdetid
 * prorataBeregningTypeHvilken type prorata beregnet uførepensjon ved overgang alder
 * konvertertTilUTHar blitt konvertert fra UP til UT
 * uftUTUføretidspunkt for UT
 */
class TrygdetidVariable(
    var datoFyller16: Date? = null,
    var ttFaktiskBeregnes: Boolean? = null,
    var ttFaktiskRegnesFra: Date? = null,
    var ttFaktiskRegnesTil: Date? = null,
    var ttFaktiskMaksGrense: Date? = null,
    var ttFramtidigRegnesFra: Date? = null,
    var ttFramtidigRegnesTil: Date? = null,
    var opptjeningRegnesFra: Date? = null,
    var opptjeningRegnesTil: Date? = null,
    var firefemtedelskrav: Int? = null,
    var ytelseType: KravlinjeTypeEnum? = null,
    var fttBeregnes: Boolean? = null,
    var restDagerRundesOpp: Boolean? = null,
    var avtaletype: AvtaletypeEnum? = null,
    var årskull: Int? = null,
    var regelverkType: RegelverkTypeEnum? = null,
    var sisteUFT: Date? = null,
    var kapittel20: Boolean? = null,
    var garantiType: TrygdetidGarantitypeEnum? = null,
    var prorataBeregningType: ProRataBeregningTypeEnum? = null,
    var konvertertTilUT: Boolean? = null,
    var uftUT: Date? = null
) : java.io.Serializable
