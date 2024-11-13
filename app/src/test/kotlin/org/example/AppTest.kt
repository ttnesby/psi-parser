/*
 * This source file was generated by the Gradle 'init' task
 */
package org.example

import java.io.File
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
            sourceCode: String,
            fileName: String = "Test.kt"
    ): Result<List<RuleServiceDoc>> {
        val ktFile =
                context.psiFactory.createFileFromText(
                        fileName,
                        KotlinFileType.INSTANCE,
                        sourceCode
                ) as
                        KtFile

        return getBindingContext(listOf(ktFile), context).map { bctx ->
            analyzeRuleService(ktFile, bctx)
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

    @Test
    @DisplayName("Should extract RuleService from KtFile")
    fun testExtractRuleService() {
        val ruleServiceName = "TestRuleService"
        val testCode =
                """
            class ${ruleServiceName}() : AbstractPensjonRuleService<Dummy>() {}
        """.trimIndent()

        analyzeKotlinCode(testCode).map { ruleServices ->
            assert(ruleServices.isNotEmpty())
            assertEquals(ruleServiceName, ruleServices.first().navn)
        }
    }

    @Test
    @DisplayName("Should handle KtFile with no RuleServices")
    fun testExtractRuleServiceEmpty() {
        val testCode =
                """
            class RegularClass() {
                fun someFunction() {}
            }
        """.trimIndent()

        analyzeKotlinCode(testCode).map { ruleServices -> assertTrue(ruleServices.isEmpty()) }
    }

    @Test
    @DisplayName("Should extract Request from RuleService")
    fun testExtractRequest() {
        val requestTypeName = "TestRequest"
        val requestName = "request"
        val var1Name = "att1"
        val var1Type = "String"
        val var2Name = "att2"
        val var2Type = "Boolean"
        val testCode =
                """
            class ${requestTypeName}(
                var ${var1Name}: ${var1Type} = "test"
                var ${var2Name}: ${var2Type} = true
            ) : ServiceRequest()

            class TestRuleService(private val ${requestName}: ${requestTypeName}) : AbstractPensjonRuleService<Dummy>() {}
        """.trimIndent()

        analyzeKotlinCode(testCode).map { ruleServices ->
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
    @DisplayName("Should handle RuleService without Request")
    fun testExtractRequestEmpty() {
        val requestTypeName = "TestRequest"
        val requestName = "request"
        val var1Name = "att1"
        val var1Type = "String"
        val var2Name = "att2"
        val var2Type = "Boolean"
        val testCode =
                """
            class ${requestTypeName}(
                var ${var1Name}: ${var1Type} = "test"
                var ${var2Name}: ${var2Type} = true
            ) : SomethingElse()

            class TestRuleService(private val ${requestName}: ${requestTypeName}) : AbstractPensjonRuleService<Dummy>() {}
        """.trimIndent()

        analyzeKotlinCode(testCode).map { ruleServices ->
            assert(ruleServices.count() == 1)
            assert(ruleServices.first().inndata.isEmpty())
        }
    }

    @Test
    @DisplayName("Should extract Response from RuleService")
    fun testExtractResponse() {
        val responseTypeName = "TestResponse"
        val var1Name = "att1"
        val var1Type = "String"
        val var2Name = "att2"
        val var2Type = "Boolean"
        val testCode =
                """
            class ${responseTypeName}(
                var ${var1Name}: ${var1Type} = "test"
                var ${var2Name}: ${var2Type} = true
            ) : ServiceResponse()

            class TestRuleService() : AbstractPensjonRuleService<${responseTypeName}>() {}
        """.trimIndent()

        analyzeKotlinCode(testCode).map { ruleServices ->
            assert(ruleServices.count() == 1)
            // 3 props: response, att1, att2
            assertEquals(ruleServices.first().utdata.count(), 3)
            assertEquals(ruleServices.first().utdata.first().navn, responseTypeName)
            assertEquals(ruleServices.first().utdata.first().type, responseTypeName)
            assertEquals(ruleServices.first().utdata.last().navn, var2Name)
            assertEquals(ruleServices.first().utdata.last().type, var2Type)
        }
    }

    @DisplayName("Should handle RuleService without Response")
    fun testExtractResponseEmpty() {
        val responseTypeName = "TestResponse"
        val var1Name = "att1"
        val var1Type = "String"
        val var2Name = "att2"
        val var2Type = "Boolean"
        val testCode =
                """
            class ${responseTypeName}(
                var ${var1Name}: ${var1Type} = "test"
                var ${var2Name}: ${var2Type} = true
            ) : SomethingElse()

            class TestRuleService() : AbstractPensjonRuleService<${responseTypeName}>() {}
        """.trimIndent()

        analyzeKotlinCode(testCode).map { ruleServices ->
            assert(ruleServices.count() == 1)
            assert(ruleServices.first().utdata.isEmpty())
        }
    }

    @Test
    @DisplayName("Should extract RuleFlowStart from RuleService")
    fun testExtractRuleFlowStart() {
        val testCode =
                """
                class FastsettTrygdetidService(
                    private val innTrygdetidRequest: TrygdetidRequest
                ) : AbstractPensjonRuleService<TrygdetidResponse>(innTrygdetidRequest) {
                    override val ruleService: () -> TrygdetidResponse = {
                        log_debug("[FUN] startFastsettTrygdetid")

                        /**
                         * Test
                         * Etabler grunnlag for fastsettelse av trygdetid.
                         */
                        val trygdetidParametere = TrygdetidParameterType(
                            grunnlag = TrygdetidGrunnlag(
                                bruker = innTrygdetidRequest.persongrunnlag,
                                boddEllerArbeidetIUtlandet = innTrygdetidRequest.boddEllerArbeidetIUtlandet,
                                førsteVirk = innTrygdetidRequest.brukerForsteVirk,
                                virkFom = innTrygdetidRequest.virkFom,
                                virkTom = innTrygdetidRequest.virkTom,
                                ytelseType = innTrygdetidRequest.hovedKravlinjeType,
                                regelverkType = innTrygdetidRequest.regelverkType,
                                uttaksgradListe = innTrygdetidRequest.uttaksgradListe,
                                beregningsvilkarsPeriodeListe = innTrygdetidRequest.sortedBeregningssvilkarPeriodeListe(),
                                redusertFTTUT = innTrygdetidRequest.redusertFTTUT,
                                beregning = null
                            )
                        )

                        /**
                         * Utled regelverkstype hvis ikke satt i request.
                         * Default er G_REG.
                         */
                        if (trygdetidParametere.grunnlag?.regelverkType == null
                            && trygdetidParametere.grunnlag?.bruker != null
                            && trygdetidParametere.grunnlag?.ytelseType != null) {
                            trygdetidParametere.grunnlag!!.regelverkType = utledRegelverkstype(
                                trygdetidParametere.grunnlag?.bruker!!,
                                trygdetidParametere.grunnlag?.ytelseType!!
                            )
                        }

                        trygdetidParametere.resultat = TrygdetidResultat(pakkseddel = Pakkseddel())

                        // Kjør reglene
                        StartTrygdetidFlyt(trygdetidParametere).run(this)

                        /**
                         * Test
                         * Klargjør respons fra resultatet av reglene.
                         */
                        TrygdetidResponse(
                            trygdetid = trygdetidParametere.resultat?.trygdetid,
                            trygdetidAlternativ = trygdetidParametere.resultat?.trygdetidAlternativ,
                            trygdetidKapittel20 = trygdetidParametere.resultat?.trygdetidKapittel20,
                            pakkseddel = trygdetidParametere.resultat?.pakkseddel!!
                        )
                    }
                }
               """.trimIndent()

        analyzeKotlinCode(testCode).map { ruleServices ->
            assertEquals(ruleServices.count(), 1)
            assertEquals(ruleServices.first().tjeneste?.kdoc?.count(), 3)
        }
    }
}
