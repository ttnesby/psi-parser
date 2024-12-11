package no.nav.domain.pensjon.regler.repository.tjeneste.fastsetttrygdetid.function

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.GrunnlagsrolleEnum
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag

fun settEPStilSøker(innParam: TrygdetidParameterType) {
    innParam.grunnlag?.bruker = Persongrunnlag(innParam.grunnlag?.bruker!!)
    innParam.grunnlag?.bruker?.grunnlagsrolle = GrunnlagsrolleEnum.SOKER
}
