package org.example

import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import javax.xml.transform.Source

// TODO - hvordan strukturere test(er) som er store - ref. siste test som blir involverende
//

class PsiKtFunctionsTest {

    private lateinit var disposable: Disposable
    private lateinit var context: CompilerContext

    private fun createTestCompilerContext(): CompilerContext {
        return createCompilerContext(File(System.getProperty("java.home")), disposable).getOrThrow()
    }

    data class SourceCode(val code: String, val fileName: String = FILE_NAME)

    private fun analyzeKotlinCode(
        codeList: List<SourceCode>,
    ): Result<Pair<List<KtFile>, BindingContext>> =
        codeList
            .map { (sourceCode, fileName) ->
                context.psiFactory.createFileFromText(
                    fileName,
                    KotlinFileType.INSTANCE,
                    sourceCode
                ) as KtFile
            }
            .let { ktFiles ->
                getBindingContext(ktFiles, context).map { bctx -> Pair(ktFiles, bctx) }
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
    fun `Should extract RuleService KtClass from KtFile`() {
        val code =
            SourceCode(
                """
            class Test() : AbstractPensjonRuleService {}
        """.trimIndent()
            )

        analyzeKotlinCode(listOf(code))
            .onSuccess { (ktFiles, _) ->
                ktFiles.first()
                    .getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass)
                    .onSuccess { assert(true) }
                    .onFailure { assert(false) }
            }
            .onFailure { assert(false) }
    }

    @Test
    fun `Should extract RuleFlow KtClass from KtFile`() {
        val code =
            SourceCode(
                """
            class Test() : AbstractPensjonRuleflow {}
        """.trimIndent()
            )

        analyzeKotlinCode(listOf(code))
            .onSuccess { (ktFiles, _) ->
                ktFiles.first()
                    .getSubClassOfSuperClass(KtClass::isSubClassOfRuleFlowClass)
                    .onSuccess { assert(true) }
                    .onFailure { assert(false) }
            }
            .onFailure { assert(false) }
    }

    @Test
    fun `Should handle non existing KtClass in KtFile - 1`() {
        val code =
            SourceCode(
                """
            class Test() : SomethingElse {}
        """.trimIndent()
            )

        analyzeKotlinCode(listOf(code))
            .onSuccess { (ktFiles, _) ->
                ktFiles.first()
                    .getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass)
                    .onSuccess { assert(false) }
                    .onFailure { assert(true) }
            }
            .onFailure { assert(it is NoSuchElementException) }
    }

    @Test
    fun `Should handle non existing KtClass in KtFile - 2`() {
        val code = SourceCode(
            """
            class Test() {}
        """.trimIndent()
        )

        analyzeKotlinCode(listOf(code))
            .onSuccess { (ktFiles, _) ->
                ktFiles.first()
                    .getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass)
                    .onSuccess { assert(false) }
                    .onFailure { assert(true) }
            }
            .onFailure { assert(it is NoSuchElementException) }
    }

    // TODO - unngå dype/pyramide mønster som går innover i de ulike Result med success/failure
    /**
     * @Test
     * fun `Should extract sequence of flow KtElements from RuleService KtClass`() {
     *     val methodName = "ruleService"
     *     val code = SourceCode("""
     *         // ... your test code here ...
     *     """.trimIndent())
     *
     *     runCatching {
     *         val (ktFile, bindingContext) = analyzeKotlinCode(listOf(code)).getOrThrow()
     *         val ruleService = ktFile.first()
     *             .getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass)
     *             .getOrThrow()
     *
     *         val flowElements = ruleService.getRuleServiceFlow(bindingContext).getOrThrow()
     *             .toList()
     *
     *         // Assertions
     *         assertEquals(3, flowElements.size)
     *
     *         with(flowElements[0] as FlowElement.Documentation) {
     *             assertEquals("Test1", beskrivelse)
     *         }
     *
     *         with(flowElements[1] as FlowElement.Documentation) {
     *             assertEquals("Test2", beskrivelse)
     *         }
     *
     *         with(flowElements[2] as FlowElement.RuleFlow) {
     *             assertEquals("StartTrygdetidFlyt", navn)
     *         }
     *     }.onFailure {
     *         fail("Test failed with exception: ${it.message}")
     *     }
     * }
      */

    @Test
    fun `Should get ServiceRequestInfo data class from RuleService KtClass`() {
        val reqName = "req"
        val reqType = "ARequest"
        val code =
            SourceCode(
                """
            class $reqType() : ServiceRequest {}
            class Test(val $reqName: $reqType) : AbstractPensjonRuleService {}
        """.trimIndent()
            )

        analyzeKotlinCode(listOf(code))
            .onSuccess { (ktFile, bindingContext) ->
                ktFile.first()
                    .getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass)
                    .onSuccess { ruleService ->
                        ruleService
                            .getServiceRequestInfo(bindingContext)
                            .onSuccess { (param, requestClass) ->
                                assertEquals(reqName, param.name)
                                assertEquals(reqType, requestClass.name)
                            }
                            .onFailure { assert(false) }
                    }
                    .onFailure { assert(false) }
            }
            .onFailure { assert(false) }
    }

    @Test
    fun `Should handle no ServiceRequestInfo data class from RuleService KtClass`() {
        val reqName = "req"
        val reqType = "ARequest"
        val code =
            SourceCode(
                """
            class $reqType() : SomethingElse() {}
            class Test(val $reqName: $reqType) : AbstractPensjonRuleService {}
        """.trimIndent()
            )

        analyzeKotlinCode(listOf(code))
            .onSuccess { (ktFile, bindingContext) ->
                ktFile.first()
                    .getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass)
                    .onSuccess { ruleService ->
                        ruleService
                            .getServiceRequestInfo(bindingContext)
                            .onSuccess { (_, _) -> assert(false) }
                            .onFailure { assert(it is NoSuchElementException) }
                    }
                    .onFailure { assert(false) }
            }
            .onFailure { assert(false) }
    }

    @Test
    fun `Should get ServiceResponse class for RuleService KtClass`() {
        val respType = "AResponse"
        val code =
            SourceCode(
                """
            class $respType() : ServiceResponse() {}
            class Test() : AbstractPensjonRuleService<$respType> {}
        """.trimIndent()
            )

        analyzeKotlinCode(listOf(code))
            .onSuccess { (ktFile, bindingContext) ->
                ktFile.first()
                    .getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass)
                    .onSuccess { ruleService ->
                        ruleService
                            .getServiceResponseClass(bindingContext)
                            .onSuccess { responseClass ->
                                assertEquals(respType, responseClass.name)
                            }
                            .onFailure { assert(false) }
                    }
                    .onFailure { assert(false) }
            }
            .onFailure { assert(false) }
    }

    @Test
    fun `Should handle no ServiceResponse class for RuleService KtClass`() {
        val respType = "AResponse"
        val code =
            SourceCode(
                """
            class $respType() : SomeThingElse() {}
            class Test() : AbstractPensjonRuleService<$respType> {}
        """.trimIndent()
            )

        analyzeKotlinCode(listOf(code))
            .onSuccess { (ktFile, bindingContext) ->
                ktFile.first()
                    .getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass)
                    .onSuccess { ruleService ->
                        ruleService
                            .getServiceResponseClass(bindingContext)
                            .onSuccess { _ -> assert(false) }
                            .onFailure { assert(it is NoSuchElementException) }
                    }
                    .onFailure { assert(false) }
            }
            .onFailure { assert(false) }
    }

    @Test
    fun `Should extract KDoc from relevant KtClass`() {
        val doc1 = "Some documentation line 1"
        val doc2 = "Some documentation line 2"
        val code =
            SourceCode(
                """
            /**
             * $doc1
             * $doc2
             */

            class Test() : AbstractPensjonRuleService {}
        """.trimIndent()
            )

        analyzeKotlinCode(listOf(code))
            .onSuccess { (ktFile, _) ->
                ktFile.first()
                    .getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass)
                    .onSuccess {
                        val expected = listOf(doc1, doc2).joinToString("\n").trim()
                        assertEquals(expected, it.getKDocOrEmpty())
                    }
                    .onFailure { assert(false) }
            }
            .onFailure { assert(false) }
    }

    @Test
    fun `Should handle no KDoc for relevant KtClass`() {
        val code =
            SourceCode(
                """
            class Test() : AbstractPensjonRuleService {}
        """.trimIndent()
            )

        analyzeKotlinCode(listOf(code))
            .onSuccess { (ktFile, _) ->
                ktFile.first()
                    .getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass)
                    .onSuccess { it.getKDocOrEmpty().isEmpty() }
                    .onFailure { assert(false) }
            }
            .onFailure { assert(false) }
    }

    @Test
    fun `Should extract KDoc for parameters in ServiceRequest primary constructor`() {
        val reqName = "req"
        val reqType = "ARequest"
        val code =
            SourceCode(
                """
            class $reqType(
                /**
                * Virkningstidspunktets fom. for �nsket ytelse.
                */
                var virkFom: Date? = null,

                /**
                * Tom for trygdetiden som skal beregnes. Kun for AP2011, AP2016 og AP2025.
                */
                var virkTom: Date? = null
            ) : ServiceRequest {}

            class Test(val $reqName: $reqType) : AbstractPensjonRuleService {}
        """.trimIndent()
            )

        analyzeKotlinCode(listOf(code))
            .onSuccess { (ktFile, bindingContext) ->
                ktFile.first()
                    .getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass)
                    .onSuccess { ruleService ->
                        ruleService
                            .getServiceRequestInfo(bindingContext)
                            .onSuccess { (_, requestClass) ->
                                assertEquals(reqType, requestClass.name)
                                requestClass.primaryConstructor?.let { primaryConstructor ->
                                    primaryConstructor.valueParameters.forEach {
                                            param,
                                        ->
                                        when (param.name) {
                                            "virkFom" ->
                                                assertEquals(
                                                    "Virkningstidspunktets fom. for �nsket ytelse.",
                                                    param.getKDocOrEmpty()
                                                )

                                            "virkTom" ->
                                                assertEquals(
                                                    "Tom for trygdetiden som skal beregnes. Kun for AP2011, AP2016 og AP2025.",
                                                    param.getKDocOrEmpty()
                                                )

                                            else -> assert(false)
                                        }
                                    }
                                }
                                    ?: assert(false)
                            }
                            .onFailure { assert(false) }
                    }
                    .onFailure { assert(false) }
            }
            .onFailure { assert(false) }
    }

    @Test
    fun `Should no or KDoc for parameters in ServiceRequest primary constructor`() {
        val reqName = "req"
        val reqType = "ARequest"
        val code =
            SourceCode(
                """
            class $reqType(

                var virkFom: Date? = null,

                /**
                * Tom for trygdetiden som skal beregnes. Kun for AP2011, AP2016 og AP2025.
                */
                var virkTom: Date? = null
            ) : ServiceRequest {}

            class Test(val $reqName: $reqType) : AbstractPensjonRuleService {}
        """.trimIndent()
            )

        analyzeKotlinCode(listOf(code))
            .onSuccess { (ktFile, bindingContext) ->
                ktFile.first()
                    .getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass)
                    .onSuccess { ruleService ->
                        ruleService
                            .getServiceRequestInfo(bindingContext)
                            .onSuccess { (_, requestClass) ->
                                assertEquals(reqType, requestClass.name)
                                requestClass.primaryConstructor?.let { primaryConstructor ->
                                    primaryConstructor.valueParameters.forEach {
                                            param,
                                        ->
                                        when (param.name) {
                                            "virkFom" ->
                                                assert(
                                                    param.getKDocOrEmpty()
                                                        .isEmpty()
                                                )

                                            "virkTom" ->
                                                assertEquals(
                                                    "Tom for trygdetiden som skal beregnes. Kun for AP2011, AP2016 og AP2025.",
                                                    param.getKDocOrEmpty()
                                                )

                                            else -> assert(false)
                                        }
                                    }
                                }
                                    ?: assert(false)
                            }
                            .onFailure { assert(false) }
                    }
                    .onFailure { assert(false) }
            }
            .onFailure { assert(false) }
    }

    @Test
    fun `Should extract sequence of flow KtElements from RuleService KtClass`() {
        val methodName = "ruleService"
        val code =
            SourceCode(
                """
                        class StartTrygdetidFlyt(
                            private val trygdetidParametere: TrygdetidParameterType
                        ) : AbstractPensjonRuleflow() {
                            private var førsteVirk: Date? = null
                            private var kapittel20: Boolean? = null

                            override var ruleflow: () -> Unit = {}
                        }

                        class FastsettTrygdetidService() : AbstractPensjonRuleService {
                            override val $methodName: () -> TrygdetidResponse = {
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

        analyzeKotlinCode(listOf(code))
            .onSuccess { (ktFile, bindingContext) ->
                ktFile.first()
                    .getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass)
                    .onSuccess { ruleService ->
                        ruleService
                            .getRuleServiceFlow(bindingContext)
                            .onSuccess { seq ->
                                assertEquals(3, seq.count())
                                assertEquals(
                                    "Test1",
                                    (seq.elementAt(0) as FlowElement.Documentation)
                                        .beskrivelse
                                )
                                assertEquals(
                                    "Test2",
                                    (seq.elementAt(1) as FlowElement.Documentation)
                                        .beskrivelse
                                )
                                assertEquals(
                                    "StartTrygdetidFlyt",
                                    (seq.elementAt(2) as FlowElement.RuleFlow).navn
                                )
                            }
                            .onFailure { assert(false) }
                    }
                    .onFailure { assert(false) }
            }
            .onFailure { assert(false) }
    }

    @Test
    fun `Should handle no named KtProperty in RuleService KtClass`() {
        val methodName = "someOtherMethod"
        val code =
            SourceCode(
                """
                        class FastsettTrygdetidService() : AbstractPensjonRuleService {
                            override val $methodName: () -> TrygdetidResponse = {}
                        }

        """.trimIndent()
            )

        analyzeKotlinCode(listOf(code)).onSuccess { (ktFile, bindingContext) ->
            ktFile.first()
                .getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass)
                .onSuccess { ruleService ->
                    ruleService
                        .getRuleServiceFlow(bindingContext)
                        .onSuccess { _ -> assert(false) }
                        .onFailure { assert(it is NoSuchElementException) }
                }
                .onFailure { assert(false) }
        }
    }

    @Test
    fun `Should extract sequence of flow KtElements from StartTrygdetidFlyt KtClass`() {
        val methodName = "ruleflow"
        val code =
            SourceCode(
                """

                        class StartTrygdetidFlyt(
                            private val trygdetidParametere: TrygdetidParameterType
                        ) : AbstractPensjonRuleflow() {
                            private var førsteVirk: Date? = null
                            private var kapittel20: Boolean? = null

                            override var $methodName: () -> Unit = {

                                /**
                                 * Task: Kontroller informasjonsgrunnlag
                                 */
                                KontrollerTrygdetidInformasjonsgrunnlagFlyt(trygdetidParametere).run(this)
                                /**
                                 * Task: Input ok?
                                 * EPS skal beregnes som SOKER når ytelsen er AP. CR 165527
                                 */
                                forgrening("Input ok?") {
                                    gren {
                                        betingelse { trygdetidParametere.resultat?.pakkseddel!!.merknadListe.isEmpty() }
                                        flyt {
                                            /**
                                             * Task: Init Trygdetidberegning
                                             */
                                            settPregVariableUtenGlobals(
                                                trygdetidParametere.grunnlag?.bruker,
                                                trygdetidParametere.grunnlag?.virkFom
                                            )
                                            trygdetidParametere.grunnlag?.bruker?.vilkarsVedtak = VilkarsVedtak(
                                                kravlinjeType = trygdetidParametere.grunnlag?.ytelseType,
                                                virkFom = trygdetidParametere.grunnlag?.virkFom,
                                                forsteVirk = trygdetidParametere.grunnlag?.førsteVirk,
                                                vilkarsvedtakResultat = VedtakResultatEnum.INNV
                                            )
                                            trygdetidParametere.grunnlag?.beregning = Beregning()

                                            /**
                                             * Task: AP og bruker er EPS?
                                             * EPS skal beregnes som SOKER når ytelsen er AP. CR 165527
                                             */
                                            forgrening("AP og bruker er EPS?") {
                                                gren {
                                                    betingelse {
                                                        trygdetidParametere.grunnlag?.ytelseType == KravlinjeTypeEnum.AP &&
                                                                trygdetidParametere.grunnlag?.bruker?.grunnlagsrolle in listOf(
                                                                    EKTEF,
                                                                    PARTNER,
                                                                    SAMBO
                                                                )
                                                    }
                                                    flyt {
                                                        /**
                                                         * Task: Gjør om EPS til soker
                                                         * Gjør om EPS til soker
                                                         */
                                                        settEPStilSøker(trygdetidParametere)
                                                    }
                                                }
                                                gren {
                                                    betingelse { false }
                                                    flyt {
                                                    }
                                                }
                                            }
                                            /**
                                             * Task: Finn første virkningsdato i trygden
                                             */
                                            førsteVirk = FinnPersonensFørsteVirkRS(
                                                trygdetidParametere.grunnlag?.bruker!!,
                                                trygdetidParametere.grunnlag?.førsteVirk,
                                                trygdetidParametere.grunnlag?.virkFom!!,
                                                trygdetidParametere.grunnlag?.ytelseType!!,
                                                trygdetidParametere.grunnlag?.uttaksgradListe!!
                                            ).run(this)
                                            /**
                                             * Task: Bestem kapittel 20
                                             */
                                            kapittel20 = BestemTTKapittel20RS(
                                                trygdetidParametere.grunnlag?.ytelseType!!,
                                                trygdetidParametere.grunnlag?.regelverkType
                                            ).run(this)
                                            /**
                                             * Task: Init Variable
                                             */
                                            InitTrygdetidVariableRS(trygdetidParametere, førsteVirk, kapittel20).run(this)
                                            /**
                                             * Task: Init resultat
                                             */
                                            //InitTrygdetidResultatRS(trygdetidParametere, kapittel20).run(this)
                                            /**
                                             * Task: Kontroller bostedLand
                                             */
                                            //BestemBosattLandRS(trygdetidParametere.grunnlag?.bruker!!).run(this)
                                            /**
                                             * Task: Overgangskull?
                                             */
                                            forgrening("Overgangskull?") {
                                                gren {
                                                    betingelse {
                                                        (trygdetidParametere.variable?.kapittel20 == true
                                                                && trygdetidParametere.variable?.regelverkType == RegelverkTypeEnum.N_REG_G_N_OPPTJ)
                                                    }
                                                    flyt {
                                                        /**
                                                         * Task: Fastsett Trygdetid overgangskull
                                                         */
                                                        TrygdetidOvergangskullFlyt(trygdetidParametere).run(this)
                                                    }
                                                }
                                                gren {
                                                    betingelse {
                                                        !(trygdetidParametere.variable?.kapittel20 == true
                                                                && trygdetidParametere.variable?.regelverkType == RegelverkTypeEnum.N_REG_G_N_OPPTJ)
                                                    }
                                                    flyt {
                                                        /**
                                                         * Task: Fastsett Trygdetid
                                                         */
                                                        //FastsettTrygdetidFlyt(trygdetidParametere).run(this)
                                                    }
                                                }
                                            }
                                            /**
                                             * Task: Sett virkFom og virkTom på alle returnerte trygdetider
                                             */
                                            //SettVirkFomOgTomPåTrygdetidResultatRS(trygdetidParametere).run(this)
                                        }
                                    }
                                    gren {
                                        betingelse { trygdetidParametere.resultat?.pakkseddel!!.merknadListe.isNotEmpty() }
                                        flyt {
                                        }
                                    }
                                }

                            }

                        }

        """.trimIndent()
            )

        val kigFlyt =
            SourceCode(
                """
            class KontrollerTrygdetidInformasjonsgrunnlagFlyt() : AbstractPensjonRuleflow() {
                override var ruleflow: () -> Unit = { }
            }
            """.trimIndent()
            )

        val initTrygdeTidVariableRS = SourceCode(
            """
class InitTrygdetidVariableRS(
    private val innParametere: TrygdetidParameterType?,
    private val innFørsteVirk: Date?,
    private val innKapittel20: Boolean?
) : AbstractPensjonRuleset<Unit>() {}
                
            """.trimIndent()
        )

        val trygdetidOvergangskullFlyt = SourceCode("""
class TrygdetidOvergangskullFlyt(
    trygdetidParametere: TrygdetidParameterType
) : AbstractPensjonRuleflow() {}            
        """.trimIndent())

        analyzeKotlinCode(listOf(code, kigFlyt, initTrygdeTidVariableRS,trygdetidOvergangskullFlyt))
            .onSuccess { (ktFileList, bctx) ->
                ktFileList
                    .first()
                    .getSubClassOfSuperClass(KtClass::isSubClassOfRuleFlowClass)
                    .onSuccess { ruleFlow ->
                        ruleFlow.getRuleFlowFlow(bctx)
                            .onSuccess { flow ->
                                when (val flowElement = flow.elementer[0]) {
                                    is FlowElement.Documentation ->
                                        assertEquals(
                                            "Task: Kontroller informasjonsgrunnlag",
                                            flowElement.beskrivelse
                                        )

                                    else -> assert(false)
                                }
                                when (val flowElement = flow.elementer[1]) {
                                    is FlowElement.RuleFlow ->
                                        assertEquals(
                                            "KontrollerTrygdetidInformasjonsgrunnlagFlyt",
                                            flowElement.navn
                                        )

                                    else -> assert(false)
                                }
                                when (val flowElement = flow.elementer[2]) {
                                    is FlowElement.Documentation ->
                                        assertEquals(
                                            "Task: Input ok?\nEPS skal beregnes som SOKER når ytelsen er AP. CR 165527",
                                            flowElement.beskrivelse
                                        )

                                    else -> assert(false)
                                }
                                when (val flowElement = flow.elementer[3]) {
                                    is FlowElement.Forgrening -> {
                                        assertEquals(
                                            "Task: Input ok?\nEPS skal beregnes som SOKER når ytelsen er AP. CR 165527",
                                            flowElement.beskrivelse
                                        )
                                        assertEquals("Input ok?", flowElement.navn)
                                        assertEquals(2, flowElement.gren.size)
                                        assertEquals(
                                            "{ trygdetidParametere.resultat?.pakkseddel!!.merknadListe.isEmpty() }",
                                            flowElement.gren[0].betingelse
                                        )
                                        assertEquals(
                                            "{ trygdetidParametere.resultat?.pakkseddel!!.merknadListe.isNotEmpty() }",
                                            flowElement.gren[1].betingelse
                                        )
                                    }

                                    else -> assert(false)
                                }
                            }
                            .onFailure {
                                it.printStackTrace()
                                assert(false)
                            }
                    }
                    .onFailure { assert(false) }
            }
            .onFailure { assert(false) }
    }
}
