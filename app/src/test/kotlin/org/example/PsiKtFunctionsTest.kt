package org.example

import java.io.File
import kotlin.test.assertEquals
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

class PsiKtFunctionsTest {

    private lateinit var disposable: Disposable
    private lateinit var context: CompilerContext

    private fun createTestCompilerContext(): CompilerContext {
        return createCompilerContext(File(System.getProperty("java.home")), disposable).getOrThrow()
    }

    data class SourceCode(val code: String, val fileName: String = FILE_NAME)

    private fun analyzeKotlinCode(
            code: SourceCode,
    ): KtFile =
            context.psiFactory.createFileFromText(
                    code.fileName,
                    KotlinFileType.INSTANCE,
                    code.code
            ) as
                    KtFile

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
    @DisplayName("Should extract RuleService KtClass from KtFile")
    fun testExtractRuleServiceKtClass() {
        val code =
                SourceCode(
                        """
            class Test() : AbstractPensjonRuleService {}
        """.trimIndent()
                )

        analyzeKotlinCode(code).let { ktFile ->
            ktFile.getClassOfSuperType(KtClass::isRuleServiceClass).map { assert(true) }.onFailure {
                assert(false)
            }
        }
    }

    @Test
    @DisplayName("Should extract RuleFlow KtClass from KtFile")
    fun testExtractRuleFlowKtClass() {
        val code =
                SourceCode(
                        """
            class Test() : AbstractPensjonRuleflow {}
        """.trimIndent()
                )

        analyzeKotlinCode(code).let { ktFile ->
            ktFile.getClassOfSuperType(KtClass::isRuleFlowClass).map { assert(true) }.onFailure {
                assert(false)
            }
        }
    }

    @Test
    @DisplayName("Should handle non existing KtClass in KtFile - 1")
    fun testExtractKtClassFailure1() {
        val code =
                SourceCode("""
            class Test() : SomethingElse {}
        """.trimIndent())

        analyzeKotlinCode(code).let { ktFile ->
            ktFile.getClassOfSuperType(KtClass::isRuleServiceClass)
                    .map { assert(false) }
                    .onFailure { assert(it is NoSuchElementException) }
        }
    }

    @Test
    @DisplayName("Should handle non existing KtClass in KtFile - 2")
    fun testExtractKtClassFailure2() {
        val code = SourceCode("""
            class Test() {}
        """.trimIndent())

        analyzeKotlinCode(code).let { ktFile ->
            ktFile.getClassOfSuperType(KtClass::isRuleServiceClass)
                    .map { assert(false) }
                    .onFailure { assert(it is NoSuchElementException) }
        }
    }

    @Test
    @DisplayName("Should get ServiceRequestInfo data class from RuleService KtClass")
    fun testGetRequestClassFromRuleServiceClass() {
        val reqName = "req"
        val reqType = "ARequest"
        val code =
                SourceCode(
                        """
            class ARequest() : ServiceRequest {}
            class Test(val $reqName: $reqType) : AbstractPensjonRuleService {}
        """.trimIndent()
                )

        analyzeKotlinCode(code).let { ktFile ->
            getBindingContext(listOf(ktFile), context).map { bindingContext ->
                ktFile.getClassOfSuperType(KtClass::isRuleServiceClass)
                        .map { ruleService ->
                            ruleService
                                    .getServiceRequestInfo(bindingContext)
                                    .map { (param, requestClass) ->
                                        assertEquals(reqName, param.name)
                                        assertEquals(reqType, requestClass.name)
                                    }
                                    .onFailure { assert(false) }
                        }
                        .onFailure { assert(false) }
            }
        }
    }

    @Test
    @DisplayName("Should handle no ServiceRequestInfo data class from RuleService KtClass")
    fun testGetRequestClassFromRuleServiceClassError() {
        val reqName = "req"
        val reqType = "ARequest"
        val code =
                SourceCode(
                        """
            class ARequest() : SomethingElse() {}
            class Test(val $reqName: $reqType) : AbstractPensjonRuleService {}
        """.trimIndent()
                )

        analyzeKotlinCode(code).let { ktFile ->
            getBindingContext(listOf(ktFile), context).map { bindingContext ->
                ktFile.getClassOfSuperType(KtClass::isRuleServiceClass)
                        .map { ruleService ->
                            ruleService
                                    .getServiceRequestInfo(bindingContext)
                                    .map { (_, _) -> assert(false) }
                                    .onFailure { assert(it is NoSuchElementException) }
                        }
                        .onFailure { assert(false) }
            }
        }
    }

    @Test
    @DisplayName("Should get ServiceResponse class from RuleService KtClass")
    fun testGetResponseClassFromRuleServiceClass() {
        val respType = "AResponse"
        val code =
                SourceCode(
                        """
            class $respType() : ServiceResponse() {}
            class Test() : AbstractPensjonRuleService<$respType> {}
        """.trimIndent()
                )

        analyzeKotlinCode(code).let { ktFile ->
            getBindingContext(listOf(ktFile), context).map { bindingContext ->
                ktFile.getClassOfSuperType(KtClass::isRuleServiceClass)
                        .map { ruleService ->
                            ruleService
                                    .getServiceResponseClass(bindingContext)
                                    .map { responseClass ->
                                        assertEquals(respType, responseClass.name)
                                    }
                                    .onFailure { assert(false) }
                        }
                        .onFailure { assert(false) }
            }
        }
    }

    @Test
    @DisplayName("Should extract KDoc from relevant KtClass")
    fun testExtractKDoc() {
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

        analyzeKotlinCode(code).let { ktFile ->
            ktFile.getClassOfSuperType(KtClass::isRuleServiceClass)
                    .map {
                        val expected = listOf(doc1, doc2).joinToString("\n").trim()
                        assertEquals(expected, it.getKDocOrEmpty())
                    }
                    .onFailure { assert(false) }
        }
    }

    @Test
    @DisplayName("Should handle no KDoc for relevant KtClass")
    fun testExtractKDocEmpty() {
        val code =
                SourceCode(
                        """
            class Test() : AbstractPensjonRuleService {}
        """.trimIndent()
                )

        analyzeKotlinCode(code).let { ktFile ->
            ktFile.getClassOfSuperType(KtClass::isRuleServiceClass)
                    .map { it.getKDocOrEmpty().isEmpty() }
                    .onFailure { assert(false) }
        }
    }
}
