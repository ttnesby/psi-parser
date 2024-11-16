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
    @DisplayName("Should extract correct KtClass from KtFile")
    fun testExtractKtClass() {
        val code =
                SourceCode(
                        """
            class Test() : AbstractPensjonRuleService {}
        """.trimIndent()
                )

        analyzeKotlinCode(code).let { ktFile ->
            ktFile.getClassOfSuperType(::isRuleServiceClass).map { assert(true) }.onFailure {
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
            ktFile.getClassOfSuperType(::isRuleServiceClass).map { assert(false) }.onFailure {
                assert(it is NoSuchElementException)
            }
        }
    }

    @Test
    @DisplayName("Should handle non existing KtClass in KtFile - 2")
    fun testExtractKtClassFailure2() {
        val code = SourceCode("""
            class Test() {}
        """.trimIndent())

        analyzeKotlinCode(code).let { ktFile ->
            ktFile.getClassOfSuperType(::isRuleServiceClass).map { assert(false) }.onFailure {
                assert(it is NoSuchElementException)
            }
        }
    }

    @Test
    @DisplayName("Should extract KDoc from KtClass")
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
            ktFile.getClassOfSuperType(::isRuleServiceClass)
                    .map {
                        val doc = it.getKDocOrEmpty()
                        assertEquals(listOf(doc1, doc2).joinToString("\n").trim(), doc)
                    }
                    .onFailure { assert(false) }
        }
    }

    @Test
    @DisplayName("Should handle no KDoc for KtClass")
    fun testExtractKDocEmpty() {
        val code =
                SourceCode(
                        """
            /** Some documentation */
            class Test() : AbstractPensjonRuleService {}
        """.trimIndent()
                )

        analyzeKotlinCode(code).let { ktFile ->
            ktFile.getClassOfSuperType(::isRuleServiceClass)
                    .map { it.getKDocOrEmpty().isEmpty() }
                    .onFailure { assert(false) }
        }
    }
}
