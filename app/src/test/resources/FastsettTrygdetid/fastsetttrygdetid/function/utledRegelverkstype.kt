package no.nav.domain.pensjon.regler.repository.tjeneste.fastsetttrygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.alderÅrMnd
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.RegelverkTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.RegelverkTypeEnum.*
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.preg.system.helper.år

fun utledRegelverkstype(bruker: Persongrunnlag, ytelsetypeEnum: KravlinjeTypeEnum): RegelverkTypeEnum {
    log_debug("[FUN] utledRegelverkstype")

    val avdødeHaddeAP = bruker.forsteVirkningsdatoGrunnlagListe.any {
        it.bruker?.penPersonId == bruker.penPerson?.penPersonId
                && it.kravlinjeType == KravlinjeTypeEnum.AP
    }

    /**
     * Avklart at dette skal være alder fom 67 år og 0 måneder.
     */
    val dødFom67 = alderÅrMnd(bruker.fodselsdato, bruker.dodsdato) >= 6700

    if (ytelsetypeEnum == KravlinjeTypeEnum.GJR && (avdødeHaddeAP || dødFom67)) {
        val fødselsår = bruker.fodselsdato!!.år
        return when (fødselsår) {
            in 0..1942 -> G_REG
            in 1943..1953 -> N_REG_G_OPPTJ
            in 1954..1962 -> N_REG_G_N_OPPTJ
            else -> N_REG_N_OPPTJ
        }.also {
            log_debug("[   ]    GJR: kull $fødselsår gir utledet regelverkstype: $it")
        }
    } else {
        return (G_REG).also {
            log_debug("[   ]    $ytelsetypeEnum gir default regelverkstype $it")
        }
    }
}