package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidParameterType
import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset

/**
 * PK-18583: Returnerte trygdetidobjekter fra tjenesten BEF010 skal få virkFom og virkTom satt fra
 * request.
 * Dette for å støtte periodisering av trygdetid for AP2011, 2016 og 2025.
 * Merk at virkFom og virkTom på grunnlag er hentet fra virkTom og virkFom på TrygdetidRequest.
 */
class SettVirkFomOgTomPåTrygdetidResultatRS(
    private var innTrygdetidParameter: TrygdetidParameterType?
) : AbstractPensjonRuleset<Unit>() {

    override fun create() {
        regel("trygdetidKapittel19") {
            HVIS { innTrygdetidParameter?.resultat?.trygdetid != null }
            SÅ {
                log_debug("[HIT] SettVirkFomOgTomPåTrygdetidResultatRS.TrygdetidKapittel19")
                innTrygdetidParameter?.resultat?.trygdetid?.virkFom = innTrygdetidParameter?.grunnlag?.virkFom
                innTrygdetidParameter?.resultat?.trygdetid?.virkTom = innTrygdetidParameter?.grunnlag?.virkTom
                log_debug("[   ]    resultat.trygdetid.virkFom = virkFom fra request = ${innTrygdetidParameter?.grunnlag?.virkFom}")
                log_debug("[   ]    resultat.trygdetid.virkFom = virkTom fra request = ${innTrygdetidParameter?.grunnlag?.virkTom}")
            }

        }

        regel("trygdetidKapittel20") {
            HVIS { innTrygdetidParameter?.resultat?.trygdetidKapittel20 != null }
            SÅ {
                log_debug("[HIT] SettVirkFomOgTomPåTrygdetidResultatRS.TrygdetidKapittel20")
                innTrygdetidParameter?.resultat?.trygdetidKapittel20?.virkFom = innTrygdetidParameter?.grunnlag?.virkFom
                innTrygdetidParameter?.resultat?.trygdetidKapittel20?.virkTom = innTrygdetidParameter?.grunnlag?.virkTom
                log_debug("[   ]    resultat.trygdetidKapittel20.virkFom = virkFom fra request = ${innTrygdetidParameter?.grunnlag?.virkFom}")
                log_debug("[   ]    resultat.trygdetidKapittel20.virkTom = virkFom fra request = ${innTrygdetidParameter?.grunnlag?.virkTom}")
            }

        }
    }

}
