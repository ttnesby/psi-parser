package no.nav.pensjon.regler.internal.to

import no.nav.pensjon.regler.internal.domain.Pakkseddel
import no.nav.pensjon.regler.internal.domain.Trygdetid

class TrygdetidResponse(
    /**
     * Fastsatt trygdetid.
     */
    var trygdetid: Trygdetid? = null,

    /**
     * Fastsatt trygdetid for AP2016 iht. kapittel 20 og AP2025.
     */
    var trygdetidKapittel20: Trygdetid? = null,

    /**
     * Fastsatt trygdetid for annet uf�retidspunkt.
     */
    var trygdetidAlternativ: Trygdetid? = null,
    override val pakkseddel: Pakkseddel = Pakkseddel()
) : ServiceResponse(pakkseddel)