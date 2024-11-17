/*
 * This source file was generated by the Gradle 'init' task
 */
package org.example

import java.io.File
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AppTest {

    private lateinit var disposable: Disposable
    private lateinit var context: CompilerContext

    private fun createTestCompilerContext(): CompilerContext {
        return createCompilerContext(File(System.getProperty("java.home")), disposable).getOrThrow()
    }

    private fun analyzeKotlinCode(
            code: List<SourceCode>,
    ): Result<List<RuleServiceDoc>> =
            code
                    .map { (sourceCode, fileName) ->
                        context.psiFactory.createFileFromText(
                                fileName,
                                KotlinFileType.INSTANCE,
                                sourceCode
                        ) as
                                KtFile
                    }
                    .let { ktFiles ->
                        getBindingContext(ktFiles, context).map { bctx ->
                            analyzeSourceFilesForRuleServices(ktFiles, bctx)
                        }
                    }

    @BeforeEach
    fun setUp() {
        disposable = Disposer.newDisposable()
        context = createTestCompilerContext()
    }

    @AfterEach
    fun tearDown() {
        disposable.dispose()
    }

    /** Test Types.kt::RuleServiceDoc */
    @Test
    @DisplayName("Should extract RuleService from KtFile, without KDoc, with file reference")
    fun testExtractRuleService() {
        val ruleServiceName = "TestRuleService"
        val ruleService =
                SourceCode(
                        """
            class ${ruleServiceName}() : AbstractPensjonRuleService<Dummy>() {}
        """.trimIndent()
                )

        analyzeKotlinCode(listOf(ruleService)).map { ruleServices ->
            assert(ruleServices.isNotEmpty())
            assertEquals(ruleServiceName, ruleServices.first().navn)
            assertEquals(URI(FILE_NAME), ruleServices.first().gitHubUri)
            assert(ruleServices.first().beskrivelse.isEmpty())
            assert(ruleServices.first().gitHubUri.toString().isNotEmpty())
        }
    }

    @Test
    @DisplayName("Should handle KtFile with no RuleServices")
    fun testExtractRuleServiceEmpty() {
        val testCode =
                SourceCode(
                        """
            class RegularClass() {
                fun someFunction() {}
            }
        """.trimIndent()
                )

        analyzeKotlinCode(listOf(testCode)).map { ruleServices ->
            assertTrue(ruleServices.isEmpty())
        }
    }

    @Test
    @DisplayName("Should extract KDoc for RuleServices")
    fun testExtractKDocForRuleService() {
        val request =
                SourceCode(
                        """
            class BeregnPoengtallBatchRequest(
                val dummy: String
            ) : ServiceRequest()
            """.trimIndent(),
                        "${DEFAULT_PATH + "BeregnPoengtallBatchRequest.kt"}"
                )
        val response =
                SourceCode(
                        """
                    class BeregnPoengtallBatchResponse(
                        val dummy: String
                    ) : ServiceResponse()
                    """.trimIndent(),
                        "${DEFAULT_PATH + "BeregnPoengtallBatchResponse.kt"}"
                )

        val ruleService =
                SourceCode(
                        """
                        /**
                         * Behøver ikke sjekke her. ejb-laget fanger en krasj og kaster exception videre.Huskeliste for å få
                         * ønsket ytelse på beregning av poengtall i
                         * batch.---------------------------------------------------------------------1.
                         * Regeltjenesteprosjektet heter BeregnPoengtallBatchProj, testprosjektet heter
                         * BeregnPoengtallbatchTestProj2. Følgende execution mode flagg må settes:startBatchBeregnPoengtall -
                         * compiledpoengrekke-tekBeregnFaktiskePoengtallRS - compiled sequential (opprinnelig
                         * sequential)beregnPoengtallAvOpptjening - compiled(opprinnelig
                         * Default)BeregnPoengtallAvOpptjeningsgrunnlagRS - compiled sequential (default eller
                         * compiled?)støttefunksjoner-tekVeiet grunnbeløp TATempl - compiled sequential (opprinnelig
                         * sequential)3.Regeltjenesteprosjektet BeregnPoengtallBatch må ha  Project Settings-> Object Life
                         * Cycle ->Explicit deletion satt 4. Ved deployment til BeregnPoengtallBatch.server må Recycle policy
                         * settes til Reinitializeog maximum iterations til 1.5. Før kompilering må
                         * UseDeploymentClassLoadingContext på BeregnPoengtallBatch.serversettes til trueMålt ytelse er 700 ms
                         * for 10000 elementer
                         */
                        class BeregnPoengtallBatchService(
                            private val innBeregnPoengtallBatchRequest: BeregnPoengtallBatchRequest
                        ) : AbstractPensjonRuleService<BeregnPoengtallBatchResponse>(innBeregnPoengtallBatchRequest) {

                            override val ruleService: () -> BeregnPoengtallBatchResponse = {
                                BeregnPoengtallAvOpptjeningsgrunnlagRS(innBeregnPoengtallBatchRequest.personOpptjeningsgrunnlagListe).run(this)
                                BeregnPoengtallBatchResponse(innBeregnPoengtallBatchRequest.personOpptjeningsgrunnlagListe)
                            }
                        }
        """.trimIndent()
                )

        analyzeKotlinCode(listOf(ruleService, request, response)).map { ruleServices ->
            assertTrue(ruleServices.count() == 1)
            assert(ruleServices.first().beskrivelse.isNotEmpty())
            assertTrue(ruleServices.first().inndata.isNotEmpty())
            assertTrue(ruleServices.first().utdata.isNotEmpty())
        }
    }

    @Test
    @DisplayName("Should extract ServiceRequest from RuleService")
    fun testExtractRequest() {
        val requestTypeName = "TestRequest"
        val requestName = "request"
        val var1Name = "att1"
        val var1Type = "String"
        val var2Name = "att2"
        val var2Type = "Boolean"
        val request =
                SourceCode(
                        """
            class ${requestTypeName}(
                var ${var1Name}: ${var1Type} = "test"
                var ${var2Name}: ${var2Type} = true
            ) : ServiceRequest()
        """.trimIndent(),
                        DEFAULT_PATH + requestTypeName + ".kt"
                )

        val ruleService =
                SourceCode(
                        """
            class TestRuleService(private val ${requestName}: ${requestTypeName}) : AbstractPensjonRuleService<Dummy>() {}
        """.trimIndent()
                )

        analyzeKotlinCode(listOf(request, ruleService)).map { ruleServices ->
            assert(ruleServices.count() == 1)
            // 3 props: request, att1, att2
            assertEquals(ruleServices.first().inndata.count(), 3)
            assertEquals(
                    ruleServices.first().inndata.first().navn,
                    requestName,
            )
            assertEquals(ruleServices.first().inndata.first().type, requestTypeName)
            assertEquals(ruleServices.first().inndata.last().navn, var2Name)
            assertEquals(ruleServices.first().inndata.last().type, var2Type)
        }
    }

    @Test
    @DisplayName("Should handle RuleService without ServiceRequest")
    fun testExtractRequestEmpty() {
        val requestTypeName = "TestRequest"
        val requestName = "request"
        val var1Name = "att1"
        val var1Type = "String"
        val var2Name = "att2"
        val var2Type = "Boolean"
        val somethingElse =
                SourceCode(
                        """
                class ${requestTypeName}(
                    var ${var1Name}: ${var1Type} = "test"
                    var ${var2Name}: ${var2Type} = true
                ) : SomethingElse()
        """.trimIndent(),
                        DEFAULT_PATH + requestTypeName + ".kt"
                )
        val ruleService =
                SourceCode(
                        """
            class TestRuleService(private val ${requestName}: ${requestTypeName}) : AbstractPensjonRuleService<Dummy>() {}
        """.trimIndent()
                )

        analyzeKotlinCode(listOf(ruleService, somethingElse)).map { ruleServices ->
            assert(ruleServices.count() == 1)
            assert(ruleServices.first().inndata.isEmpty())
        }
    }

    @Test
    @DisplayName("Should extract ServiceResponse from RuleService")
    fun testExtractResponse() {
        val responseTypeName = "TestResponse"
        val var1Name = "att1"
        val var1Type = "String"
        val var2Name = "att2"
        val var2Type = "Boolean"
        val response =
                SourceCode(
                        """
            class ${responseTypeName}(
                var ${var1Name}: ${var1Type} = "test"
                var ${var2Name}: ${var2Type} = true
            ) : ServiceResponse()
        """.trimIndent(),
                        DEFAULT_PATH + responseTypeName + ".kt"
                )
        val ruleService =
                SourceCode(
                        """
            class TestRuleService() : AbstractPensjonRuleService<${responseTypeName}>() {}
        """.trimIndent()
                )

        analyzeKotlinCode(listOf(response, ruleService)).map { ruleServices ->
            assert(ruleServices.count() == 1)
            // 3 props: response, att1, att2
            assertEquals(ruleServices.first().utdata.count(), 3)
            assertEquals(ruleServices.first().utdata.first().navn, responseTypeName)
            assertEquals(ruleServices.first().utdata.first().type, responseTypeName)
            assertEquals(ruleServices.first().utdata.last().navn, var2Name)
            assertEquals(ruleServices.first().utdata.last().type, var2Type)
        }
    }

    @DisplayName("Should handle RuleService without ServiceResponse")
    fun testExtractResponseEmpty() {
        val responseTypeName = "TestResponse"
        val var1Name = "att1"
        val var1Type = "String"
        val var2Name = "att2"
        val var2Type = "Boolean"
        val somethingElse =
                SourceCode(
                        """
            class ${responseTypeName}(
                var ${var1Name}: ${var1Type} = "test"
                var ${var2Name}: ${var2Type} = true
            ) : SomethingElse()
        """.trimIndent(),
                        DEFAULT_PATH + responseTypeName + ".kt"
                )
        val ruleService =
                SourceCode(
                        """
            class TestRuleService() : AbstractPensjonRuleService<${responseTypeName}>() {}
        """.trimIndent()
                )

        analyzeKotlinCode(listOf(somethingElse, ruleService)).map { ruleServices ->
            assert(ruleServices.count() == 1)
            assert(ruleServices.first().utdata.isEmpty())
        }
    }

    // @Test
    // @DisplayName("Should extract RuleFlowStart from RuleService")
    // @Disabled("Not implemented yet")
    // fun testExtractRuleFlowStart() {
    //     val request =
    //             SourceCode(
    //                     """
    //             class TrygdetidRequest(
    //                 /**
    //                  * Virkningstidspunktets fom. for �nsket ytelse.
    //                  */
    //                 var virkFom: Date? = null,

    //                 /**
    //                  * Tom for trygdetiden som skal beregnes. Kun for AP2011, AP2016 og AP2025.
    //                  */
    //                 var virkTom: Date? = null,

    //                 /**
    //                  * F�rste virkningstidspunkt,denne m� v�re satt dersom personen er SOKER i
    // persongrunnlaget.
    //                  */
    //                 var brukerForsteVirk: Date? = null,

    //                 /**
    //                  * Type ytelse (AP,UP osv)
    //                  */
    //                 var hovedKravlinjeType: KravlinjeTypeEnum? = null,

    //                 /**
    //                  * Persongrunnlag for personen.
    //                  * Dersom ytelsesType er UP m� uforegrunnlag og uforehistorikk v�re utfylt.
    //                  */
    //                 var persongrunnlag: Persongrunnlag? = null,

    //                 /**
    //                  * Angir om personen har bodd eller arbeidet i utlandet.
    //                  */
    //                 var boddEllerArbeidetIUtlandet: Boolean = false,

    //                 /**
    //                  * Regelverktype bestemmer om trygdetid skal regnes etter gamle eller nye
    // regler.
    //                  */
    //                 var regelverkType: RegelverkTypeEnum? = null,

    //                 var uttaksgradListe: MutableList<Uttaksgrad> = mutableListOf(),

    //                 var redusertFTTUT: Boolean? = null,
    //                 /**
    //                  * Liste av beregningsvilkarPerioder, p�krevd ved uf�retrygd.
    //                  */
    //                 var beregningsvilkarPeriodeListe: MutableList<BeregningsvilkarPeriode> =
    // mutableListOf()
    //             ) : ServiceRequest() {
    //             )
    //     """.trimIndent(),
    //                     DEFAULT_PATH + "TrygdeTidRequest.kt"
    //             )
    //     val response =
    //             SourceCode(
    //                     """
    //                     class TrygdetidResponse(
    //                         /**
    //                          * Fastsatt trygdetid.
    //                          */
    //                         var trygdetid: Trygdetid? = null,

    //                         /**
    //                          * Fastsatt trygdetid for AP2016 iht. kapittel 20 og AP2025.
    //                          */
    //                         var trygdetidKapittel20: Trygdetid? = null,

    //                         /**
    //                          * Fastsatt trygdetid for annet uf�retidspunkt.
    //                          */
    //                         var trygdetidAlternativ: Trygdetid? = null,
    //                         override val pakkseddel: Pakkseddel = Pakkseddel()
    //                     ) : ServiceResponse(pakkseddel)        """.trimIndent(),
    //                     DEFAULT_PATH + "TrygdeTidResponse.kt"
    //             )
    //     val testCode =
    //             SourceCode(
    //                     """
    //             class FastsettTrygdetidService(
    //                 private val innTrygdetidRequest: TrygdetidRequest
    //             ) : AbstractPensjonRuleService<TrygdetidResponse>(innTrygdetidRequest) {
    //                 override val ruleService: () -> TrygdetidResponse = {
    //                     log_debug("[FUN] startFastsettTrygdetid")

    //                     /**
    //                      * Test
    //                      * Etabler grunnlag for fastsettelse av trygdetid.
    //                      */
    //                     val trygdetidParametere = TrygdetidParameterType(
    //                         grunnlag = TrygdetidGrunnlag(
    //                             bruker = innTrygdetidRequest.persongrunnlag,
    //                             boddEllerArbeidetIUtlandet =
    // innTrygdetidRequest.boddEllerArbeidetIUtlandet,
    //                             førsteVirk = innTrygdetidRequest.brukerForsteVirk,
    //                             virkFom = innTrygdetidRequest.virkFom,
    //                             virkTom = innTrygdetidRequest.virkTom,
    //                             ytelseType = innTrygdetidRequest.hovedKravlinjeType,
    //                             regelverkType = innTrygdetidRequest.regelverkType,
    //                             uttaksgradListe = innTrygdetidRequest.uttaksgradListe,
    //                             beregningsvilkarsPeriodeListe =
    // innTrygdetidRequest.sortedBeregningssvilkarPeriodeListe(),
    //                             redusertFTTUT = innTrygdetidRequest.redusertFTTUT,
    //                             beregning = null
    //                         )
    //                     )

    //                     /**
    //                      * Utled regelverkstype hvis ikke satt i request.
    //                      * Default er G_REG.
    //                      */
    //                     if (trygdetidParametere.grunnlag?.regelverkType == null
    //                         && trygdetidParametere.grunnlag?.bruker != null
    //                         && trygdetidParametere.grunnlag?.ytelseType != null) {
    //                         trygdetidParametere.grunnlag!!.regelverkType = utledRegelverkstype(
    //                             trygdetidParametere.grunnlag?.bruker!!,
    //                             trygdetidParametere.grunnlag?.ytelseType!!
    //                         )
    //                     }

    //                     trygdetidParametere.resultat = TrygdetidResultat(pakkseddel =
    // Pakkseddel())

    //                     // Kjør reglene
    //                     StartTrygdetidFlyt(trygdetidParametere).run(this)

    //                     /**
    //                      * Test
    //                      * Klargjør respons fra resultatet av reglene.
    //                      */
    //                     TrygdetidResponse(
    //                         trygdetid = trygdetidParametere.resultat?.trygdetid,
    //                         trygdetidAlternativ =
    // trygdetidParametere.resultat?.trygdetidAlternativ,
    //                         trygdetidKapittel20 =
    // trygdetidParametere.resultat?.trygdetidKapittel20,
    //                         pakkseddel = trygdetidParametere.resultat?.pakkseddel!!
    //                     )
    //                 }
    //             }
    //            """.trimIndent()
    //             )

    //     analyzeKotlinCode(listOf(testCode, request)).map { ruleServices ->
    //         assertEquals(1, ruleServices.count())
    //         assertEquals(4, ruleServices.first().flyt.elementer.count())
    //     }
    // }
}
