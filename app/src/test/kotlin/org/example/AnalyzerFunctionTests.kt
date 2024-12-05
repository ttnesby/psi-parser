/*
 * This source file was generated by the Gradle 'init' task
 */
package org.example

import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.junit5.JUnit5Asserter.fail
import embeddable.compiler.CompilerContext

class AnalyzerFunctionsTests {

    private lateinit var disposable: Disposable
    private lateinit var context: CompilerContext

    private fun createTestCompilerContext(): CompilerContext {
        return CompilerContext.new(
            //libsPath = Path(""),
            disposable = disposable).getOrThrow()
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
                context.buildBindingContext(ktFiles).map { bctx ->
                    analyzeSourceFilesTest(ktFiles, bctx)
                }
            }

    private fun analyzeSourceFiles(code: List<SourceCode>): Result<Pair<List<KtFile>, BindingContext>> = runCatching {
        val ktFiles = code
            .map { (sourceCode, fileName) ->
                context.psiFactory.createFileFromText(
                    fileName,
                    KotlinFileType.INSTANCE,
                    sourceCode
                ) as
                        KtFile
            }

            context.buildBindingContext(ktFiles).map { bctx ->
                Pair(ktFiles, bctx)
            }.getOrThrow()
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

                    /**
                    * StartTrygdetidFlyt
                    */
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
            assertEquals(URI("https://github.com/navikt/app/src/test/kotlin/org/example/Test.kt"), rs.gitHubUri)
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

            assertEquals(3, rs.flyt.elementer.count())

            val firstFunction = rs.flyt.elementer[0] as FlowElement.Function
            assertEquals("log_debug", firstFunction.navn)

            val aRuleFlow = rs.flyt.elementer[1] as FlowElement.RuleFlow
            assertEquals("StartTrygdetidFlyt", aRuleFlow.navn)
            assertEquals("StartTrygdetidFlyt", aRuleFlow.beskrivelse)

            val secondFunction = rs.flyt.elementer[2] as FlowElement.Function
            assertEquals("TrygdetidResponse", secondFunction.navn)
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
    @DisplayName("Should give a list of PropertyDoc for a Rule flow KtClass")
    fun `Should give a list of PropertyDoc for a Rule flow KtClass`() {


        val ruleFlow = SourceCode(
            """
            class StartTrygdetidFlyt(
                private val trygdetidParametere: TrygdetidParameterType
            ) : AbstractPensjonRuleflow() {
                private var førsteVirk: Date? = null
                private var kapittel20: Boolean? = null

                override var ruleflow: () -> Unit = {}
            }
        """.trimIndent()
        )

        val trygdeTidParameter = SourceCode(
            """
            class TrygdetidParameterType {
                var grunnlag: TrygdetidGrunnlag? = null
                /**
                 * Resultat av trygdetidsberegningen.
                 */
                var resultat: TrygdetidResultat? = null
                var variable: TrygdetidVariable? = null
            }
        """.trimIndent()
        )

        runCatching() {
            val (ktFiles, bindingContext) = analyzeSourceFiles(listOf(ruleFlow, trygdeTidParameter)).getOrThrow()
            val ruleFlowClass = ktFiles.first().getSubClassOfSuperClass(KtClass::isSubClassOfRuleFlowClass).getOrThrow()
            val propertyDocs = getFlowRequestFields(ruleFlowClass, bindingContext).getOrThrow()

            assertEquals(4, propertyDocs.size)
            assertEquals("trygdetidParametere", propertyDocs[0].navn)
            assertEquals("TrygdetidParameterType", propertyDocs[0].type)
            assertEquals("Resultat av trygdetidsberegningen.", propertyDocs[2].beskrivelse)
            assertEquals("variable", propertyDocs[3].navn)
            assertEquals("TrygdetidVariable?", propertyDocs[3].type)

        }.onFailure {
            fail("Test failed with exception: ${it.message}")
        }
    }
}
