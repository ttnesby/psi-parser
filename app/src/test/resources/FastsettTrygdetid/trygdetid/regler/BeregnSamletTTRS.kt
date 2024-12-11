package no.nav.domain.pensjon.regler.repository.komponent.trygdetid.regler

import no.nav.domain.pensjon.regler.repository.AbstractPensjonRuleset
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.function.*
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.AvtaletypeEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.UtfallEnum
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.UtfallEnum.*
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.koder.KravlinjeTypeEnum

import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.regler.AvklarEksportrettFlyktningRS
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.regler.AvklarFørsteKravlinjeFremsattDatoErFomDatoRS
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.regler.EvaluerInngangOgEksportOppfyltVedSammenleggingRS
import no.nav.domain.pensjon.regler.repository.komponent.stottefunksjoner.regler.PersonenErFlyktningRS
import no.nav.domain.pensjon.regler.repository.komponent.trygdetid.klasser.TrygdetidVariable
import no.nav.domain.pensjon.regler.repository.merknad.MerknadEnum
import no.nav.pensjon.regler.internal.domain.TTUtlandTrygdeavtale
import no.nav.pensjon.regler.internal.domain.Trygdetid
import no.nav.pensjon.regler.internal.domain.grunnlag.Persongrunnlag
import no.nav.preg.system.helper.*
import no.nav.system.rule.dsl.DslDomainPredicate
import no.nav.system.rule.dsl.pattern.Pattern
import no.nav.system.rule.dsl.pattern.createPattern
import no.nav.system.rule.dsl.rettsregel.Faktum
import no.nav.system.rule.dsl.rettsregel.erLik
import no.nav.system.rule.dsl.rettsregel.erMindreEnn
import java.util.*

/**
 * Hvis ytelse AP og trygdeavtale er EØS, Australia, Canada, Chile, India, Israel, Sveits, Sør-Korea
 * eller USA og oppfylt ved sammenlegging
 * så er det unntatt fra Min3År regelen.
 */
class BeregnSamletTTRS(
    private val innTrygdetid: Trygdetid,
    private val innBruker: Persongrunnlag,
    private val innParametere: TrygdetidVariable,
    private val innVirk: Date,
    private val innFørsteVirk: Date
) : AbstractPensjonRuleset<Unit>() {
    private var over70år: Boolean = (alderÅrMnd(innBruker.fodselsdato, innVirk) >= 7001)
    private var sum_tt: Int = 0
    private var tt_fra_poengår: Int = 0
    private val ttUtlandTrygdeavtaleListe: List<TTUtlandTrygdeavtale> = innTrygdetid.ttUtlandTrygdeavtaler
    private lateinit var erFlyktning: Faktum<UtfallEnum>
    private val kravlinjeFremsattFom2021: Boolean by lazy {
        AvklarFørsteKravlinjeFremsattDatoErFomDatoRS(
            innBruker,
            localDate(2021, 1, 1),
            innParametere.ytelseType
        ).run(this)
    }
    private val oppfyltVedSammenlegging: Boolean by lazy {
        EvaluerInngangOgEksportOppfyltVedSammenleggingRS(
            innBruker,
            innParametere.kapittel20,
            innParametere.ytelseType!!,
            false
        ).run(this)
    }
    private var eksportrettFlyktning: Faktum<Boolean> = Faktum("Eksportrett flyktning", false)
    private val ttUtlandTrygdeavtale: Pattern<TTUtlandTrygdeavtale> = ttUtlandTrygdeavtaleListe.createPattern()

    @OptIn(DslDomainPredicate::class)
    override fun create() {
        regel("AvklarFlyktningKap19") {
            HVIS { innParametere.kapittel20 != null }
            OG { !innParametere.kapittel20!! }
            SÅ {
                erFlyktning = PersonenErFlyktningRS(
                    Persongrunnlag(innBruker).apply { trygdetid = innTrygdetid },
                    innParametere.ytelseType!!,
                    innParametere.kapittel20!!,
                    innVirk
                ).run(this)

                log_debug("[HIT] BeregnSamletTTRS.AvklarFlyktningKap19, $erFlyktning")
            }
        }
        regel("AvklarFlyktningKap20") {
            HVIS { innParametere.kapittel20 != null }
            OG { innParametere.kapittel20!! }
            SÅ {
                erFlyktning = PersonenErFlyktningRS(
                    Persongrunnlag(innBruker).apply { trygdetidKapittel20 = innTrygdetid },
                    innParametere.ytelseType!!,
                    innParametere.kapittel20!!,
                    innVirk
                ).run(this)

                log_debug("[HIT] BeregnSamletTTRS.AvklarFlyktningKap20, $erFlyktning")
            }
        }
        regel("AvklarEksportrettFlyktning") {
            HVIS { true }
            SÅ {
                eksportrettFlyktning = AvklarEksportrettFlyktningRS(innBruker, innVirk).run(this)
                log_debug("[HIT] BeregnSamletTTRS.AvklarEksportrettFlyktning eksportrettFlyktning: $eksportrettFlyktning")
            }
        }
        regel("APOgOppfyltVedSammenLegging") {
            HVIS {
                innParametere.avtaletype == AvtaletypeEnum.EOS_NOR
                        || innParametere.avtaletype == AvtaletypeEnum.AUS
                        || innParametere.avtaletype == AvtaletypeEnum.CAN
                        || innParametere.avtaletype == AvtaletypeEnum.CHE
                        || innParametere.avtaletype == AvtaletypeEnum.CHL
                        || innParametere.avtaletype == AvtaletypeEnum.IND
                        || innParametere.avtaletype == AvtaletypeEnum.ISR
                        || innParametere.avtaletype == AvtaletypeEnum.KOR
                        || innParametere.avtaletype == AvtaletypeEnum.USA
            }
            OG { oppfyltVedSammenlegging }
            OG { innBruker.vilkarsVedtak != null }
            OG { innBruker.vilkarsVedtak?.kravlinjeType != null }
            OG { innBruker.vilkarsVedtak?.kravlinjeType == KravlinjeTypeEnum.AP }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.APOgOppfyltVedSammenLegging")
            }
            kommentar(
                """Hvis ytelse AP og trygdeavtale er EØS, Australia, Canada, Chile, India,
            Israel, Sveits, Sør-Korea eller USA og oppfylt ved sammenlegging 
        så er det unntatt fra Min3År regelen."""
            )
        }
        regel("Etter1991SamletTrygdetid") {
            HVIS { innFørsteVirk.toLocalDate() >= localDate(1991, 1, 1) }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.Etter1991SamletTrygdetid")
                val ttmnd: Int = innTrygdetid.tt_fa_mnd + innTrygdetid.ftt
                sum_tt = avrund(ttmnd / 12.0)
                innTrygdetid.tt_faktisk = innTrygdetid.tt_fa_mnd + innTrygdetid.tt_67_70 * 12
                log_formel_start("Samlet trygdetid")
                log_formel("sum_tt = avrund((tt_fa_mnd + ftt)/12)")
                log_formel("sum_tt = avrund((${innTrygdetid.tt_fa_mnd} + ${innTrygdetid.ftt})/12)")
                log_formel("sum_tt = $sum_tt")
                log_formel_slutt()
            }
            kommentar(
                """Etter 1991: Samlet trygdetid beregnes ved å legge sammen faktisk og framtidig
            trygdetid og 
        runde av til nærmeste hele år. 6 mnd og mer rundes opp, 5 mnd og mindre rundes ned."""
            )

        }
        regel("Etter1991ogTT67til70") {
            HVIS { innFørsteVirk.toLocalDate() >= localDate(1991, 1, 1) }
            OG { innTrygdetid.tt_67_70 > 0 }
            OG { over70år }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.Etter1991ogTT67til70, tt_67_70 = ${innTrygdetid.tt_67_70}")
                log_formel_start("Samlet trygdetid, poengår 67. til 69. året")
                log_formel("sum_tt = sum_tt + tt_67_70")
                log_formel("sum_tt = $sum_tt + ${innTrygdetid.tt_67_70}")
                tt_fra_poengår = innTrygdetid.tt_67_70
                sum_tt += innTrygdetid.tt_67_70
                innTrygdetid.tt_faktisk = innTrygdetid.tt_fa_mnd + innTrygdetid.tt_67_70 * 12
                log_formel("sum_tt = $sum_tt")
                log_formel_slutt()
            }
        }
        regel("Etter1991ogTT67til75") {
            HVIS { innFørsteVirk.toLocalDate() >= localDate(1991, 1, 1) }
            OG { innTrygdetid.tt_67_75 > 0 }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.Etter1991ogTT67til75, tt_67_75 = ${innTrygdetid.tt_67_75}")
                log_formel_start("Samlet trygdetid, poengår 67. til 75. året")
                log_formel("sum_tt = sum_tt + tt_67_75")
                log_formel("sum_tt = $sum_tt + ${innTrygdetid.tt_67_75}")
                tt_fra_poengår = innTrygdetid.tt_67_75
                sum_tt += innTrygdetid.tt_67_75
                innTrygdetid.tt_faktisk = innTrygdetid.tt_fa_mnd + innTrygdetid.tt_67_75 * 12
                log_formel("sum_tt = $sum_tt")
                log_formel_slutt()
            }
        }
        regel("Etter1991ogTT67til70ogGjenlevende") {
            HVIS { innFørsteVirk.toLocalDate() >= localDate(1991, 1, 1) }
            OG { innTrygdetid.tt_67_70 > 0 }
            OG { innBruker.vilkarsVedtak != null }
            OG { innBruker.vilkarsVedtak?.kravlinjeType != null }
            OG { innBruker.vilkarsVedtak?.kravlinjeType == KravlinjeTypeEnum.GJP }
            OG { !over70år }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.Etter1991ogTT67til70ogGjenlevende, tt_67_70 = ${innTrygdetid.tt_67_70}")
                log_formel_start("Samlet trygdetid")
                log_formel("sum_tt = sum_tt + tt_67_70")
                log_formel("sum_tt = $sum_tt + ${innTrygdetid.tt_67_70}")
                tt_fra_poengår = innTrygdetid.tt_67_70
                sum_tt += innTrygdetid.tt_67_70
                innTrygdetid.tt_faktisk = innTrygdetid.tt_fa_mnd + innTrygdetid.tt_67_70 * 12
                log_formel("sum_tt = $sum_tt")
                log_formel_slutt()
            }
            kommentar(
                """Har avdøde hatt poengopptjening i det år han fylte 67, 68 eller 69 år skal
            disse årene regnes som
        faktisk trygdetid straks pensjon til gjenlevende beregnes."""
            )

        }
        regel("Før1991SamletTrygdetid") {
            HVIS { innFørsteVirk.toLocalDate() < localDate(1991, 1, 1) }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.Før1991SamletTrygdetid")
                val tt_e1966 = innTrygdetid.tt_fa_mnd - (innTrygdetid.tt_F67 * 12)
                sum_tt = innTrygdetid.tt_F67 + ceilToInt((tt_e1966 + innTrygdetid.ftt).dbl / 12.0)
                innTrygdetid.tt_faktisk = innTrygdetid.tt_fa_mnd + innTrygdetid.tt_67_70 * 12
                log_formel_start("Samlet trygdetid")
                log_formel("sum_tt = tt_F67 + ceilToInt((tt_e66_mnd + ftt)/12)")
                log_formel("sum_tt = ${innTrygdetid.tt_F67} år + ceilToInt(($tt_e1966 mnd + ${innTrygdetid.ftt} mnd )/12)")
                log_formel("sum_tt = $sum_tt")
                log_formel_slutt()
            }
            kommentar(
                """Før 1991: Trygdetid før og etter 1967 ble avrundet og fastsatt hver for seg. 
        Trygdetiden ble avrundet opp til nærmeste hele år.
        Framtidig trygdetid legges til trygdetid etter 1966 før avrunding."""
            )

        }
        regel("SettMerknadAndreMax40År") {
            HVIS { sum_tt > 40 }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.UT }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.SettMerknadAndreMax40År")
                innTrygdetid.merknadListe.add(MerknadEnum.BeregnSamletTTRS__Max40Ar.lagMerknad())
            }
            kommentar("Merknadstekst : Full trygdetid er 40 år")

        }
        regel("SettMerknadUTMax40År") {
            HVIS { sum_tt > 40 }
            OG { innParametere.ytelseType == KravlinjeTypeEnum.UT }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.SettMerknadUTMax40År")
                innTrygdetid.merknadListe.add(MerknadEnum.BeregnSamletTTRS__SettMerknadUTMax40Ar.lagMerknad())
            }
            kommentar("Merknadstekst : Full trygdetid er 40 år")
        }
        regel("SettMerknadGJTMax40År") {
            HVIS { sum_tt > 40 }
            OG { innParametere.ytelseType == KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.SettMerknadGJTMax40År")
                innTrygdetid.merknadListe.add(MerknadEnum.BeregnSamletTTRS__SettMerknadUTMax40Ar.lagMerknad())
            }
            kommentar("Merknadstekst : Full trygdetid er 40 år")

        }
        regel("Max40År") {
            HVIS { sum_tt > 40 }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.Max40År")
                sum_tt = 40
                log_formel_start("Samlet trygdetid, max 40 år")
                log_formel("sum_tt = 40")
                log_formel_slutt()
            }
            kommentar("Full trygdetid er 40 år.")

        }
        regel("Min3År") {
            HVIS { innTrygdetid.tt_fa_mnd + innTrygdetid.ftt + tt_fra_poengår * 12 < 3 * 12 }
            OG { !kravlinjeFremsattFom2021 }
            OG { "APOgOppfyltVedSammenLegging".harIkkeTruffet() }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.Min3År")
                sum_tt = floorToInt((innTrygdetid.tt_fa_mnd + innTrygdetid.ftt).dbl / 12) + tt_fra_poengår
                log_formel_start("Samlet trygdetid, min 3 år")
                log_formel("sum_tt = floorToInt((tt_fa_mnd + ftt)/12) + tt_fra_poengår")
                log_formel("sum_tt = floorToInt((${innTrygdetid.tt_fa_mnd} + ${innTrygdetid.ftt})/12) + $tt_fra_poengår")
                log_formel("sum_tt = $sum_tt")
                log_formel_slutt()
            }
        }
        regel("Min5År") {
            HVIS { innTrygdetid.tt_fa_mnd + innTrygdetid.ftt + tt_fra_poengår * 12 < 5 * 12 }
            OG { kravlinjeFremsattFom2021 }
            OG { "APOgOppfyltVedSammenLegging".harIkkeTruffet() }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.Min5År")
                sum_tt = floorToInt((innTrygdetid.tt_fa_mnd + innTrygdetid.ftt).dbl / 12.0) + tt_fra_poengår
                log_formel_start("Samlet trygdetid, min 5 år")
                log_formel("sum_tt = floorToInt((tt_fa_mnd + ftt)/12) + tt_fra_poengår")
                log_formel("sum_tt = floorToInt((${innTrygdetid.tt_fa_mnd} + ${innTrygdetid.ftt})/12) + $tt_fra_poengår")
                log_formel("sum_tt = $sum_tt")
                log_formel_slutt()
            }
        }
        regel("SettMerknadAndreMin3År") {
            HVIS { "Min3År".harTruffet() }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.UT }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.SettMerknadAndreMin3År")
                innTrygdetid.merknadListe.add(MerknadEnum.BeregnSamletTTRS__Min3Ar.lagMerknad())
            }
        }
        regel("SettMerknadUTMin3År") {
            HVIS { "Min3År".harTruffet() }
            OG { innParametere.ytelseType == KravlinjeTypeEnum.UT }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.SettMerknadUTMin3År")
                innTrygdetid.merknadListe.add(MerknadEnum.BeregnSamletTTRS__SettMerknadUTMin3Ar.lagMerknad())
            }
        }
        regel("SettMerknadGJTMin3År") {
            HVIS { "Min3År".harTruffet() }
            OG { innParametere.ytelseType == KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.SettMerknadUTMin3År")
                innTrygdetid.merknadListe.add(MerknadEnum.BeregnSamletTTRS__SettMerknadUTMin3Ar.lagMerknad())
            }
        }
        regel("AnvendtFlyktning_IkkeUføretrygd") {
            HVIS { eksportrettFlyktning erLik true }
            OG { Faktum("Faktisk trygdetid", sum_tt) erMindreEnn 40 }
            OG { erFlyktning erLik OPPFYLT }
            OG { innParametere.ytelseType != KravlinjeTypeEnum.UT && innParametere.ytelseType != KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.AnvendtFlyktning_IkkeUføretrygd")
                sum_tt = 40
                innTrygdetid.anvendtFlyktningFaktum.value = OPPFYLT
                innTrygdetid.anvendtFlyktningFaktum.children.add(this)
                log_debug("[   ]    anvendtFlyktningFaktum ${innTrygdetid.anvendtFlyktningFaktum.value}")
                innTrygdetid.merknadListe.add(
                    MerknadEnum.BeregnSamletTTRS__FlyktningBosattNorge.lagMerknad(
                        mutableListOf("§ 3-2 6.ledd")
                    )
                )
                log_formel_start("Samlet trygdetid, flyktning bosatt Norge")
                log_formel("sum_tt = 40")
                log_formel_slutt()
            }
            ELLERS {
                log_debug("[---] BeregnSamletTTRS.AnvendtFlyktning_IkkeUføretrygd")
                if (erFlyktning.value == IKKE_RELEVANT) {
                    innTrygdetid.anvendtFlyktningFaktum.value = IKKE_RELEVANT
                } else {
                    innTrygdetid.anvendtFlyktningFaktum.value = IKKE_OPPFYLT
                }
                innTrygdetid.anvendtFlyktningFaktum.children.add(this)
                log_debug("[   ]    anvendtFlyktningFaktum ${innTrygdetid.anvendtFlyktningFaktum.value}")
            }
        }
        regel("FlyktningBosattNorgeOgUføretrygd") {
            HVIS { eksportrettFlyktning erLik true }
            OG { Faktum("Faktisk trygdetid", sum_tt) erMindreEnn 40 }
            OG { erFlyktning erLik OPPFYLT }
            OG { innParametere.ytelseType == KravlinjeTypeEnum.UT || innParametere.ytelseType == KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.FlyktningBosattNorgeOgUføretrygd")
                innTrygdetid.anvendtFlyktningFaktum.value = IKKE_OPPFYLT
                innTrygdetid.anvendtFlyktningFaktum.children.add(this)
                innTrygdetid.merknadListe.add(MerknadEnum.BeregnSamletTTRS__FlyktningBosattNorgeOgUforetrygd.lagMerknad())
            }
        }
        regel("FlyktningBosattEØSOgUføretrygd") {
            HVIS { eksportrettFlyktning erLik false }
            OG { Faktum("Faktisk trygdetid", sum_tt) erMindreEnn 40 }
            OG { erFlyktning erLik OPPFYLT }
            OG { innParametere.ytelseType == KravlinjeTypeEnum.UT || innParametere.ytelseType == KravlinjeTypeEnum.UT_GJT }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.FlyktningBosattEØSOgUføretrygd")
                innTrygdetid.anvendtFlyktningFaktum.value = IKKE_OPPFYLT
                innTrygdetid.anvendtFlyktningFaktum.children.add(this)
                innTrygdetid.merknadListe.add(MerknadEnum.BeregnSamletTTRS__FlyktningBosattEOSOgUforetrygd.lagMerknad())
            }
        }
        regel("NordiskTrygdetidTeller") {
            HVIS { innTrygdetid.ttUtlandKonvensjon != null }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.NordiskTrygdetidTeller")
                innTrygdetid.ttUtlandKonvensjon?.tt_A10_teller = min(480, innTrygdetid.tt_fa_mnd)
                log_formel_start("Trygdetid Nordisk konvensjon artikkel 10, teller")
                log_formel("tt_A10_teller = min(480, tt_fa_mnd)")
                log_formel("tt_A10_teller = min(480, ${innTrygdetid.tt_fa_mnd})")
                log_formel("tt_A10_teller = ${innTrygdetid.ttUtlandKonvensjon?.tt_A10_teller}")
                log_formel_slutt()
            }
        }
        regel("NordiskTrygdetidNevner") {
            HVIS { innTrygdetid.ttUtlandKonvensjon != null }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.NordiskTrygdetidNevner")
                innTrygdetid.ttUtlandKonvensjon?.tt_A10_nevner =
                    min(480, (innTrygdetid.tt_fa_mnd + innTrygdetid.ttUtlandKonvensjon?.tt_A10_fa_mnd!!))
                log_formel_start("Trygdetid Nordisk konvensjon artikkel 10, nevner")
                log_formel("tt_A10_nevner = min(480, (tt_fa_mnd + tt_A10_fa_mnd))")
                log_formel("tt_A10_nevner = min(480, (${innTrygdetid.tt_fa_mnd} + ${innTrygdetid.ttUtlandKonvensjon?.tt_A10_fa_mnd}))")
                log_formel("tt_A10_nevner = ${innTrygdetid.ttUtlandKonvensjon?.tt_A10_nevner}")
                log_formel_slutt()
            }
        }
        regel("NordiskTrygdetidFør1991") {
            HVIS { innFørsteVirk.toLocalDate() < localDate(1991, 1, 1) }
            OG { innTrygdetid.ttUtlandKonvensjon != null }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.NordiskTrygdetidFør1991")
                innTrygdetid.ttUtlandKonvensjon?.tt_A10_anv_aar =
                    min(
                        40,
                        ceilToInt((innTrygdetid.tt_fa_mnd + innTrygdetid.ttUtlandKonvensjon?.ftt_A10_netto!!) / 12.0)
                    )
                log_formel_start("Trygdetid Nordisk konvensjon artikkel 10, anvendt trygdetid")
                log_formel("tt_A10_anv_aar = min(40, ceilToInt((tt_fa_mnd + ftt_A10_netto)/12))")
                log_formel("tt_A10_anv_aar = min(40, ceilToInt((${innTrygdetid.tt_fa_mnd} + ${innTrygdetid.ttUtlandKonvensjon?.ftt_A10_netto})/12))")
                log_formel("tt_A10_anv_aar = ${innTrygdetid.ttUtlandKonvensjon?.tt_A10_anv_aar}")
                log_formel_slutt()
            }
        }
        regel("NordiskTrygdetidEtter1991") {
            HVIS { innFørsteVirk.toLocalDate() >= localDate(1991, 1, 1) }
            OG { innTrygdetid.ttUtlandKonvensjon != null }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.NordiskTrygdetidEtter1991")
                innTrygdetid.ttUtlandKonvensjon?.tt_A10_anv_aar =
                    min(
                        40,
                        avrund((innTrygdetid.tt_fa_mnd + innTrygdetid.ttUtlandKonvensjon?.ftt_A10_netto!!) / 12.0)
                    )
                log_formel_start("Trygdetid Nordisk konvensjon artikkel 10, anvendt trygdetid")
                log_formel("tt_A10_anv_aar = min(40, avrund((tt_fa_mnd + ftt_A10_netto)/12))")
                log_formel("tt_A10_anv_aar = min(40, avrund((${innTrygdetid.tt_fa_mnd} + ${innTrygdetid.ttUtlandKonvensjon?.ftt_A10_netto})/12))")
                log_formel("tt_A10_anv_aar = ${innTrygdetid.ttUtlandKonvensjon?.tt_A10_anv_aar}")
                log_formel_slutt()
            }
        }
        regel("EøsTrygdetid") {
            HVIS { innTrygdetid.ttUtlandEos != null }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.EøsTrygdetid")
                innTrygdetid.ttUtlandEos?.tt_eos_teller = min(480, innTrygdetid.tt_fa_mnd)
                innTrygdetid.ttUtlandEos?.tt_eos_nevner =
                    min(480, (innTrygdetid.tt_fa_mnd + innTrygdetid.ttUtlandEos?.tt_eos_pro_rata_mnd!!))
                innTrygdetid.ttUtlandEos?.tt_eos_anv_mnd =
                    min(
                        480,
                        (innTrygdetid.tt_fa_mnd + tt_fra_poengår * 12 + innTrygdetid.ttUtlandEos?.tt_eos_teoretisk_mnd!! + innTrygdetid.ttUtlandEos?.ftt_eos!!)
                    )
                innTrygdetid.ttUtlandEos?.tt_eos_anv_ar =
                    min(40, avrund(innTrygdetid.ttUtlandEos?.tt_eos_anv_mnd!! / 12.0))
                log_formel_start("Trygdetid EØS avtale, teller")
                log_formel("tt_eos_teller = min(480, tt_fa_mnd)")
                log_formel("tt_eos_teller = min(480, ${innTrygdetid.tt_fa_mnd}")
                log_formel("tt_eos_teller = ${innTrygdetid.ttUtlandEos?.tt_eos_teller}")
                log_formel_slutt()
                log_formel_start("Trygdetid EØS avtale, nevner")
                log_formel("tt_eos_nevner = min(480, (tt_fa_mnd + tt_eos_pro_rata_mnd))")
                log_formel("tt_eos_nevner = min(480, (${innTrygdetid.tt_fa_mnd} + ${innTrygdetid.ttUtlandEos?.tt_eos_pro_rata_mnd}))")
                log_formel("tt_eos_nevner = ${innTrygdetid.ttUtlandEos?.tt_eos_nevner}")
                log_formel_slutt()
                log_formel_start("Trygdetid EØS avtale, anvendt trygdetid måneder")
                log_formel("tt_eos_anv_mnd = min(480, (tt_fa_mnd + tt_eos_teoretisk_mnd + ftt_eos))")
                log_formel("tt_eos_anv_mnd = min(480, (${innTrygdetid.tt_fa_mnd} + ${innTrygdetid.ttUtlandEos?.tt_eos_teoretisk_mnd} + ${innTrygdetid.ttUtlandEos?.ftt_eos}))")
                log_formel("tt_eos_anv_mnd = ${innTrygdetid.ttUtlandEos?.tt_eos_anv_mnd}")
                log_formel_slutt()
                log_formel_start("Trygdetid EØS avtale, anvendt trygdetid år")
                log_formel("tt_eos_anv_ar = min(40, avrund(tt_eos_anv_mnd/12))")
                log_formel("tt_eos_anv_ar = min(40, avrund(${innTrygdetid.ttUtlandEos?.tt_eos_anv_mnd}/12))")
                log_formel("tt_eos_anv_ar = ${innTrygdetid.ttUtlandEos?.tt_eos_anv_ar}")
                log_formel_slutt()
            }
        }
        regel("TrygdeavtaleTrygdetid", ttUtlandTrygdeavtale) {
            HVIS { true }
            SÅ {
                log_debug("[HIT] BeregnSamletTTRS.TrygdeavtaleTrygdetid, ${it.avtaleland}")
                it.pro_rata_teller = min(480, innTrygdetid.tt_fa_mnd)
                it.pro_rata_nevner = min(480, (innTrygdetid.tt_fa_mnd + it.tt_fa_mnd))
                it.tt_anv_mnd = min(480, (innTrygdetid.tt_fa_mnd + it.tt_fa_mnd + it.ftt))
                it.tt_anv_ar = min(40, avrund(it.tt_anv_mnd / 12.0))
                log_formel_start("Trygdetid trygdeavtale ${it.avtaleland}, teller")
                log_formel("pro_rata_teller = min(480, tt_fa_mnd)")
                log_formel("pro_rata_teller = min(480, ${innTrygdetid.tt_fa_mnd}")
                log_formel("pro_rata_teller = ${it.pro_rata_teller}")
                log_formel_slutt()
                log_formel_start("Trygdetid trygdeavtale ${it.avtaleland}, nevner")
                log_formel("pro_rata_nevner = min(480, (tt_fa_mnd + trygdeavtale.tt_fa_mnd))")
                log_formel("pro_rata_nevner = min(480, (${innTrygdetid.tt_fa_mnd} + ${it.tt_fa_mnd}))")
                log_formel("pro_rata_nevner = ${it.pro_rata_nevner}")
                log_formel_slutt()
                log_formel_start("Trygdetid trygdeavtale ${it.avtaleland}, anvendt trygdetid måneder")
                log_formel("tt_anv_mnd = min(480, (tt_fa_mnd + trygdeavtale.tt_fa_mnd + trygdeavtale.ftt))")
                log_formel("tt_anv_mnd = min(480, (${innTrygdetid.tt_fa_mnd} + ${it.tt_fa_mnd} + ${it.ftt}))")
                log_formel("tt_anv_mnd = ${it.tt_anv_mnd}")
                log_formel_slutt()
                log_formel_start("Trygdetid trygdeavtale ${it.avtaleland}, anvendt trygdetid år")
                log_formel("tt_anv_ar = min(40, avrund(trygdeavtale.tt_anv_mnd/12))")
                log_formel("tt_anv_ar = min(40, avrund(${it.tt_anv_mnd}/12))")
                log_formel("tt_anv_ar = ${it.tt_anv_ar}")
                log_formel_slutt()
            }
        }
        regel("SamletTrygdetid") {
            HVIS { true }
            SÅ {
                innTrygdetid.tt = sum_tt
                log_formel_start("Samlet trygdetid Norge")
                log_formel("tt = ${innTrygdetid.tt}")
                log_formel_slutt()
            }
        }

    }
}

