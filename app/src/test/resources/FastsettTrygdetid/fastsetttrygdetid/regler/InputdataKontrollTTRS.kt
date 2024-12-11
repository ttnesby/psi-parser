package no.nav.domain.pensjon.regler.repository.tjeneste.fastsetttrygdetid.regler

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.log_debug
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.GrunnlagsrolleEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.RegelverkTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.regler.FinnPersonensFørsteVirkRS
import no.nav.domain.pensjon.regler.repository.merknad.MerknadEnum
import no.nav.pensjon.regler.internal.domain.Merknad
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.pensjon.regler.internal.domain.grunnlag.Uttaksgrad
import java.util.*

/**
 * Sikkerhetsnett for å fange feil i inndata innen det fører til feilsituasjoner i beregningsflyten.
 */
class InputdataKontrollTTRS(
    private val innBruker: Persongrunnlag?,
    private val innYtelseType: KravlinjeTypeEnum?,
    private val innVirk: Date?,
    private val innMerknadListe: MutableList<Merknad>,
    private val innBrukersForsteVirk: Date?,
    private val innBoddEllerArbeidetIUtlandet: Boolean?,
    private val innRegelverkType: RegelverkTypeEnum?,
    private val innYtelsesType: KravlinjeTypeEnum?,
    private val innUttaksgradListe: MutableList<Uttaksgrad>
) : AbstractPensjonRuleset<Unit>() {
    private var grunnlagsrolle: GrunnlagsrolleEnum? = null
    private var ytelseTypeKodeGyldig: Boolean? = null
    private var avtaleTypeKodeGyldig: Boolean? = null
    private var avtaleDatoKodeGyldig: Boolean? = null
    private var fastsattFørstevirk: Boolean = false
    private var førstevirk: Date? = null
    private var regelverkTypeKodeGyldig: Boolean? = null

    override fun create() {

        regel("FinnGrunnlagsrolle") {
            HVIS { innBruker != null }
            OG { innBruker?.personDetaljListe?.size!! > 0 }
            SÅ {
                grunnlagsrolle = innBruker?.personDetaljListe?.get(0)?.grunnlagsrolle
            }
            kommentar("Hjelperegel for å finne grunnlagsrolle på bruker")

        }

        regel("BrukerMangler") {
            HVIS { innBruker == null }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.BrukerMangler")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__BrukerMangler.lagMerknad())
            }
            kommentar("Bruker mangler")

        }

        regel("PersonDetaljListeErTom") {
            HVIS { innBruker != null }
            OG { innBruker?.personDetaljListe?.size == 0 }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.PersonDetaljListeErTom")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__PersonDetaljListeErTom.lagMerknad())
            }
            kommentar("Persondetaljliste er tom")

        }

        regel("GrunnlagsrolleMangler") {
            HVIS { innBruker != null }
            OG { innBruker?.personDetaljListe?.size!! > 0 }
            OG { innBruker?.personDetaljListe!![0].grunnlagsrolle == null }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.GrunnlagsrolleMangler")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__GrunnlagsrolleMangler.lagMerknad())
            }
            kommentar("Grunnlagsrolle mangler")
        }

        regel("VirkMangler") {
            HVIS { innVirk == null }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.VirkMangler")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__VirkMangler.lagMerknad())
            }
            kommentar("VIRK mangler")

        }

        regel("YtelseTypeMangler") {
            HVIS {
                innYtelseType == null
            }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.YtelseTypeMangler")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__YtelseTypeMangler.lagMerknad())
            }
            kommentar("Ytelsetype mangler")

        }

        // TODO ytelse.valid
//        regel("ValiderYtelseTypeKode") {
//            HVIS { "YtelseTypeMangler".harIkkeTruffet() }
//            OG { ytelseTypeKodeGyldig == null }
//            SÅ {
//                ytelseTypeKodeGyldig = innYtelseType?.valid()
//            }
//            kommentar("Hjelperegel for å validere ytelsetype")
//
//        }

//        regel("YtelseTypeUgyldig") {
//            HVIS { "YtelseTypeMangler".harIkkeTruffet() }
//            OG { !ytelseTypeKodeGyldig!! }
//            SÅ {
//                log_debug("[HIT] InputdataKontrollTTRS.YtelseTypeUgyldig")
//                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__YtelseTypeUgyldig.lagMerknad())
//            }
//            kommentar("Ytelsetype er ugyldig")
//
//        }

        regel("FødselsdatoMangler") {
            HVIS { innBruker != null }
            OG { innBruker?.fodselsdato == null }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.FødselsdatoMangler")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__FodselsdatoMangler.lagMerknad())
            }
            kommentar("Fødselsdato mangler")

        }

        regel("DødsdatoManglerGJP") {
            HVIS { innBruker != null }
            OG { "YtelseTypeMangler".harIkkeTruffet() }
            OG { innYtelseType == KravlinjeTypeEnum.GJP }
            OG { innBruker?.dodsdato == null }
            OG { grunnlagsrolle != null }
            OG { grunnlagsrolle == GrunnlagsrolleEnum.AVDOD }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.DødsdatoManglerGJP")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__DodsdatoManglerGJP.lagMerknad())
            }
            kommentar("Ytelsetype er GJP og avdøde mangler dødsdato.")

        }

        regel("DødsdatoManglerGJR") {
            HVIS { innBruker != null }
            OG { "YtelseTypeMangler".harIkkeTruffet() }
            OG { innYtelseType == KravlinjeTypeEnum.GJR }
            OG { innBruker?.dodsdato == null }
            OG { grunnlagsrolle != null }
            OG { grunnlagsrolle == GrunnlagsrolleEnum.AVDOD }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.DødsdatoManglerGJR")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__DodsdatoManglerGJR.lagMerknad())
            }
            kommentar("Ytelsetype er GJR og avdøde mangler dødsdato.")

        }

        regel("UføredatoMangler") {
            HVIS { innBruker != null }
            OG { "YtelseTypeMangler".harIkkeTruffet() }
            OG { innYtelseType == KravlinjeTypeEnum.UP }
            OG {
                (innBruker?.uforegrunnlag == null || (innBruker.uforegrunnlag?.uft == null))
            }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.UføredatoMangler")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__UforedatoMangler.lagMerknad())
            }
            kommentar("Ytelsetype er UP og uføredato mangler")
        }

        regel("AvtaleTypeMangler") {
            HVIS { innBruker != null }
            OG {
                !(innBruker?.trygdeavtale != null
                        && innBruker.trygdeavtale?.avtaleType != null)
            }
            SÅ {
            }
            kommentar("Avtaletype mangler")
        }

        // TODO Kan vi slette 'valid' regel?
//        regel("ValiderAvtaleTypeKode") {
//            HVIS { "AvtaleTypeMangler".harIkkeTruffet() }
//            OG { avtaleTypeKodeGyldig == null }
//            OG { innBruker != null }
//            SÅ {
//                avtaleTypeKodeGyldig = innBruker?.trygdeavtale?.avtaleType?.valid()
//            }
//            kommentar("Valider avtaletype")
//
//        }

        regel("UgyldigAvtaleType") {
            HVIS { "AvtaleTypeMangler".harIkkeTruffet() }
            OG { avtaleTypeKodeGyldig != null }
            OG { !avtaleTypeKodeGyldig!! }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.UgyldigAvtaleType")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__UgyldigAvtaleType.lagMerknad())
            }
            kommentar("Ugyldig avtaletype")
        }

        regel("AvtaleDatoMangler") {
            HVIS { innBruker != null }
            OG {
                !(innBruker?.trygdeavtale != null
                        && innBruker.trygdeavtale?.avtaledato != null)
            }
            SÅ {
            }
            kommentar("Avtaledato mangler")
        }

        // TODO Kan vi slette 'valid' regler?
//        regel("ValiderAvtaleDatoKode") {
//            HVIS { "AvtaleDatoMangler".harIkkeTruffet() }
//            OG { avtaleDatoKodeGyldig == null }
//            OG { innBruker != null }
//            SÅ {
//                avtaleDatoKodeGyldig = innBruker?.trygdeavtale?.avtaledato?.valid()
//            }
//            kommentar("Valider avtaledato")
//
//        }
//
//        regel("UgyldigAvtaleDato") {
//            HVIS { "AvtaleDatoMangler".harIkkeTruffet() }
//            OG { avtaleDatoKodeGyldig != null }
//            OG { !avtaleDatoKodeGyldig!! }
//            SÅ {
//                log_debug("[HIT] InputdataKontrollTTRS.UgyldigAvtaleDato")
//                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__UgyldigAvtaleDato.lagMerknad())
//            }
//            kommentar("Ugyldig avtaledato")
//
//        }

        regel("YtelseUPOgIkkeSøker") {
            HVIS { grunnlagsrolle != null }
            OG { grunnlagsrolle != GrunnlagsrolleEnum.SOKER }
//            OG { "YtelseTypeUgyldig".harIkkeTruffet() }
            OG { innYtelseType == KravlinjeTypeEnum.UP }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.YtelseUPOgIkkeSøker")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__YtelseUPOgIkkeSoker.lagMerknad())
            }
            kommentar("Hvis UP så regnes trygdetid bare for søker.")

        }

        regel("YtelseGJPOgIkkeSøkerEllerAvdød") {
            HVIS { grunnlagsrolle != null }
            OG { grunnlagsrolle != GrunnlagsrolleEnum.SOKER }
            OG { grunnlagsrolle != GrunnlagsrolleEnum.AVDOD }
//            OG { "YtelseTypeUgyldig".harIkkeTruffet() }
            OG { innYtelseType == KravlinjeTypeEnum.GJP }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.YtelseGJPOgIkkeSøkerEllerAvdød")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__YtelseGJPOgIkkeSokerEllerAvdod.lagMerknad())
            }
            kommentar("Hvis GJP så regnes trygdetid bare for søker og avdød.")

        }

        regel("YtelseGJROgIkkeSøkerEllerAvdød") {
            HVIS { grunnlagsrolle != null }
            OG { grunnlagsrolle != GrunnlagsrolleEnum.SOKER }
            OG { grunnlagsrolle != GrunnlagsrolleEnum.AVDOD }
//            OG { "YtelseTypeUgyldig".harIkkeTruffet() }
            OG { innYtelseType == KravlinjeTypeEnum.GJR }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.YtelseGJROgIkkeSøkerEllerAvdød")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__YtelseGJROgIkkeSokerEllerAvdod.lagMerknad())
            }
            kommentar("Hvis GJR så regnes trygdetid bare for søker og avdød.")

        }

        regel("YtelseUT_GJTOgIkkeAvdød") {
            HVIS { grunnlagsrolle != null }
            OG { grunnlagsrolle != GrunnlagsrolleEnum.SOKER }
            OG { grunnlagsrolle != GrunnlagsrolleEnum.AVDOD }
//            OG { "YtelseTypeUgyldig".harIkkeTruffet() }
            OG { innYtelseType == KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.YtelseUT_GJTOgIkkeAvdød")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__YtelseUT_GJTOgIkkeAvdod.lagMerknad())
            }
            kommentar("Hvis UT_GJT så regnes trygdetid bare for avdød.")

        }

        regel("YtelseAFPOgIkkeSøker") {
            HVIS { grunnlagsrolle != null }
            OG { grunnlagsrolle != GrunnlagsrolleEnum.SOKER }
//            OG { "YtelseTypeUgyldig".harIkkeTruffet() }
            OG { innYtelseType == KravlinjeTypeEnum.AFP }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.YtelseAFPOgIkkeSøker")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__YtelseAFPOgIkkeSoker.lagMerknad())
            }
            kommentar("Hvis AFP så regnes trygdetid bare for søker.")

        }

        regel("YtelseFPOgIkkeSøker") {
            HVIS { grunnlagsrolle != null }
            OG { grunnlagsrolle != GrunnlagsrolleEnum.SOKER }
//            OG { "YtelseTypeUgyldig".harIkkeTruffet() }
            OG { innYtelseType == KravlinjeTypeEnum.FP }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.YtelseFPOgIkkeSøker")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__YtelseFPOgIkkeSoker.lagMerknad())
            }
            kommentar("Hvis FP så regnes trygdetid bare for søker.")

        }

        regel("YtelseBPOgIkkeSøkerEllerForelder") {
            HVIS { grunnlagsrolle != null }
            OG { grunnlagsrolle != GrunnlagsrolleEnum.SOKER }
            OG { grunnlagsrolle != GrunnlagsrolleEnum.FAR }
            OG { grunnlagsrolle != GrunnlagsrolleEnum.MOR }
//            OG { "YtelseTypeUgyldig".harIkkeTruffet() }
            OG { innYtelseType == KravlinjeTypeEnum.BP }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.YtelseBPOgIkkeSøkerEllerForelder")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__YtelseBPOgIkkeSokerEllerForelder.lagMerknad())
            }
            kommentar("Hvis BP så regnes trygdetid bare for søker eller forelder.")

        }

        regel("YtelseAPOgIkkeSøkerEPS") {
            HVIS { grunnlagsrolle != null }
            OG {
                grunnlagsrolle != GrunnlagsrolleEnum.SOKER
                        && grunnlagsrolle != GrunnlagsrolleEnum.EKTEF
                        && grunnlagsrolle != GrunnlagsrolleEnum.PARTNER
                        && grunnlagsrolle != GrunnlagsrolleEnum.SAMBO
            }
//            OG { "YtelseTypeUgyldig".harIkkeTruffet() }
            OG { innYtelseType == KravlinjeTypeEnum.AP }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.YtelseAPOgIkkeSøker")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__YtelseAPOgIkkeSoker.lagMerknad())
            }
            kommentar("Hvis AP så regnes trygdetid bare for søker. Iflg CR 165527")

        }

        regel("BrukersFørsteVirkMangler") {
            HVIS { grunnlagsrolle != null }
            OG { grunnlagsrolle == GrunnlagsrolleEnum.SOKER }
            OG { (innBrukersForsteVirk?.toString() == null) }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.BrukersFørsteVirkMangler")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__BrukersForsteVirkMangler.lagMerknad())
            }
            kommentar("Hvis AP så regnes trygdetid bare for søker.")

        }

        regel("EPSFørsteVirkMangler") {
            HVIS { grunnlagsrolle != null }
            OG {
                grunnlagsrolle == GrunnlagsrolleEnum.EKTEF
                        || grunnlagsrolle != GrunnlagsrolleEnum.PARTNER
                        || grunnlagsrolle != GrunnlagsrolleEnum.SAMBO
            }
//            OG { "YtelseTypeUgyldig".harIkkeTruffet() }
            OG { innYtelseType == KravlinjeTypeEnum.AP }
            OG { (innBrukersForsteVirk == null) }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.BrukersFørsteVirkMangler")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__BrukersForsteVirkMangler.lagMerknad())
            }
            kommentar("Hvis AP så regnes trygdetid bare for søker.")

        }

        regel("IkkeBoddUtlandOgSimAngitt") {
            HVIS { innBoddEllerArbeidetIUtlandet == false }
            OG { innBruker != null }
            OG { innBruker?.sistMedlITrygden != null }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.IkkeBoddUtlandOgSimAngitt")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__IkkeBoddUtlandOgSimAngitt.lagMerknad())
            }
            kommentar(
                """Har ikke bodd i utlandet og samtidig er sim (sist innmeldt i folketrygden)
            angitt. 
        Hvis sim er angitt så betyr dette at søker har hatt opphold i utlandet."""
            )

        }

        regel("FinnFørstevirkAnnenPerson") {
            HVIS { grunnlagsrolle != null }
            OG { grunnlagsrolle != GrunnlagsrolleEnum.SOKER }
            OG {
                !((grunnlagsrolle == GrunnlagsrolleEnum.EKTEF
                        || grunnlagsrolle == GrunnlagsrolleEnum.PARTNER
                        || grunnlagsrolle == GrunnlagsrolleEnum.SAMBO)
                        && innYtelseType == KravlinjeTypeEnum.AP)
            }
            OG { !fastsattFørstevirk }
            SÅ {
                førstevirk =
                    FinnPersonensFørsteVirkRS(
                        innBruker!!,
                        innBrukersForsteVirk,
                        innVirk!!,
                        innYtelsesType!!,
                        innUttaksgradListe
                    ).run(this)
                fastsattFørstevirk = true
            }
            kommentar("Finn førstevirk for annen person enn søker, ektefelle, partner eller sambo")

        }

        regel("RegelverkTypeMangler") {
            HVIS {
                innRegelverkType == null
            }
            SÅ {
                log_debug("[HIT] InputdataKontrollTTRS.RegelverkTypeMangler")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__RegelverkTypeMangler.lagMerknad())
            }
            kommentar("Regelverktype mangler.")

        }

        regel("SisteGyldigeOpptjeningsArIkkeAngitt") {
            HVIS { (innBruker?.sisteGyldigeOpptjeningsAr == null || innBruker.sisteGyldigeOpptjeningsAr < 2000) }
            OG { innRegelverkType != RegelverkTypeEnum.G_REG }
            SÅ {
                log_debug("[HIT] KontrollerSisteGyldigeOpptjeningsArRS.SisteGyldingOpptjeningsArIkkeSatt")
                innMerknadListe.add(MerknadEnum.InputdataKontrollTTRS__SisteGyldigeOpptjeningsArMangler.lagMerknad())
            }
        }
    }
}