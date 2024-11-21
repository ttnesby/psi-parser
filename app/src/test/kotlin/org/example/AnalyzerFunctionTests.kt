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

class AnalyzerFunctionsTests {

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
                            analyzeSourceFiles(ktFiles, bctx)
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
    @DisplayName("Should extract RuleService from KtFile")
    fun testExtractRuleService() {
        val ruleServiceName = "TestRuleService"
        val reqName = "req"
        val reqType = "TrygdetidRequest"
        val respType = "TrygdetidResponse"
        val ruleService =
                SourceCode(
                        """
            fun log_debug(message: String) = println(message)

            class ${ruleServiceName}(val $reqName: $reqType) : AbstractPensjonRuleService<$respType>() {
                override val ruleService: () -> TrygdetidResponse = {
                    log_debug("[FUN] startFastsettTrygdetid")

                    /**
                    * Test1
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
                    * Test2
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

                    TrygdetidResponse(
                        trygdetid = trygdetidParametere.resultat?.trygdetid,
                        trygdetidAlternativ = trygdetidParametere.resultat?.trygdetidAlternativ,
                        trygdetidKapittel20 = trygdetidParametere.resultat?.trygdetidKapittel20,
                        pakkseddel = trygdetidParametere.resultat?.pakkseddel!!
                    )
                }
            }
        """.trimIndent()
                )

        val request =
                SourceCode(
                        """
                class TrygdetidRequest(
                    /**
                    * Virkningstidspunktets fom. for �nsket ytelse.
                    */
                    var virkFom: Date? = null,

                    /**
                    * Tom for trygdetiden som skal beregnes. Kun for AP2011, AP2016 og AP2025.
                    */
                    var virkTom: Date? = null,
                ) : ServiceRequest() {}
        """.trimIndent(),
                        "$DEFAULT_PATH + $reqType.kt"
                )

        val response =
                SourceCode(
                        """
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
                ) : ServiceResponse() {}
        """.trimIndent(),
                        "$DEFAULT_PATH + $respType.kt"
                )
        val ruleFlow =
                SourceCode(
                        """
                class StartTrygdetidFlyt(
                    private val trygdetidParametere: TrygdetidParameterType
                ) : AbstractPensjonRuleflow() {
                    private var førsteVirk: Date? = null
                    private var kapittel20: Boolean? = null

                    override var ruleflow: () -> Unit = {}
                }
        """.trimIndent(),
                        "$DEFAULT_PATH + StartTrygdetidFlyt.kt"
                )

        analyzeKotlinCode(listOf(ruleService, request, response, ruleFlow)).map { ruleServices ->
            assert(ruleServices.isNotEmpty())

            val rs = ruleServices.first()

            assertEquals(ruleServiceName, rs.navn)
            assert(rs.beskrivelse.isEmpty())
            assertEquals(URI(FILE_NAME), rs.gitHubUri)
            assert(rs.inndata.isNotEmpty())
            assert(rs.utdata.isNotEmpty())

            assertEquals(3, rs.inndata.count())

            assertEquals(reqName, rs.inndata[0].navn)
            assertEquals(reqType, rs.inndata[0].type)
            assert(rs.inndata[0].beskrivelse.isNotEmpty())

            assertEquals("virkTom", rs.inndata[2].navn)
            assertEquals("Date?", rs.inndata[2].type)
            assertEquals(
                    "Tom for trygdetiden som skal beregnes. Kun for AP2011, AP2016 og AP2025.",
                    rs.inndata[2].beskrivelse
            )

            assertEquals(5, rs.utdata.count())

            assertEquals(respType, rs.utdata[0].navn)
            assertEquals(respType, rs.utdata[0].type)
            assert(rs.utdata[0].beskrivelse.isNotEmpty())

            assertEquals("pakkseddel", rs.utdata[4].navn)
            assertEquals("Pakkseddel", rs.utdata[4].type)
            assert(rs.utdata[4].beskrivelse.isEmpty())

            assertEquals(5, rs.flyt.elementer.count())

            val element = rs.flyt.elementer[3] as FlowElement.Reference
            val aRuleFlow = element.reference as FlowReference.RuleFlow
            assertEquals("StartTrygdetidFlyt", aRuleFlow.navn)
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
}
