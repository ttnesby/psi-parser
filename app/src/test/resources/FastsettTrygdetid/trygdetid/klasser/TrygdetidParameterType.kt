package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser

class TrygdetidParameterType {
    var grunnlag: TrygdetidGrunnlag? = null
    var resultat: TrygdetidResultat? = null
    var variable: TrygdetidVariable? = null

    constructor(
        grunnlag: TrygdetidGrunnlag? = null,
        resultat: TrygdetidResultat? = null,
        variable: TrygdetidVariable? = null
    ) {
        this.grunnlag = grunnlag
        this.resultat = resultat
        this.variable = variable
    }

    constructor()
}
