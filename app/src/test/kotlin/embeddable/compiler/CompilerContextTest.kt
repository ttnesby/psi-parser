package embeddable.compiler

import org.jetbrains.kotlin.cli.jvm.compiler.messageCollector
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.resolve.BindingContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class CompilerContextTest {

    @Test
    fun `test CompilerContext creation succeeds with valid parameters`() {
        val disposable = Disposer.newDisposable()
        val jdkHome = File(System.getProperty("java.home"))

        val result = CompilerContext.new(jdkHome = jdkHome, disposable = disposable)

        assertTrue(result.isSuccess)
        val compilerContext = result.getOrThrow()
        assertNotNull(compilerContext.configuration)
        assertNotNull(compilerContext.environment)
        assertNotNull(compilerContext.psiFactory)

        Disposer.dispose(disposable)
    }

    @Test
    fun `test KtFile creation`() {
        val disposable = Disposer.newDisposable()
        val jdkHome = File(System.getProperty("java.home"))
        val context = CompilerContext.new(jdkHome = jdkHome, disposable = disposable).getOrThrow()

        val ktFileName = "TestFile.kt"
        val ktFileContent = "fun main() { println(\"Hello, Kotlin\") }"
        val ktFile = context.createKtFile(ktFileName, ktFileContent)

        assertNotNull(ktFile)
        assertEquals(ktFileName, ktFile.name)
        assertEquals(ktFileContent, ktFile.text)

        Disposer.dispose(disposable)
    }

    @Test
    fun `test BindingContext creation succeeds with valid Kotlin files`() {
        val disposable = Disposer.newDisposable()
        val jdkHome = File(System.getProperty("java.home"))
        val context = CompilerContext.new(jdkHome = jdkHome, disposable = disposable).getOrThrow()

        val fileName = "Hello.kt"
        val content = "fun main() { println(\"Hello, World\") }"
        val ktFile = context.createKtFile(fileName, content)

        val result = context.buildBindingContext(listOf(ktFile))

        assertTrue(result.isSuccess)
        val bindingContext = result.getOrNull()
        assertNotNull(bindingContext)
        assertTrue(bindingContext is BindingContext)

        val messageCollectorSummary =
            context.environment.messageCollector as? CompilerContext.Companion.MessageCollectorSummary
        assertNotNull(messageCollectorSummary)
        assertEquals(0, messageCollectorSummary?.getErrorCount() ?: -1)

        Disposer.dispose(disposable)
    }

    @Test
    fun `test BindingContext creation succeeds even with invalid Kotlin files`() {
        val disposable = Disposer.newDisposable()
        val jdkHome = File(System.getProperty("java.home"))
        val context = CompilerContext.new(jdkHome = jdkHome, disposable = disposable).getOrThrow()

        val fileName = "BrokenFile.kt"
        val invalidContent = "fun main { println(\"Broken code\") "
        val ktFile = context.createKtFile(fileName, invalidContent)

        val result = context.buildBindingContext(listOf(ktFile))

        assertTrue(result.isSuccess)
        val bindingContext = result.getOrNull()
        assertNotNull(bindingContext)
        assertTrue(bindingContext is BindingContext)

        val messageCollectorSummary =
            context.environment.messageCollector as? CompilerContext.Companion.MessageCollectorSummary
        assertNotNull(messageCollectorSummary)
        assert((messageCollectorSummary?.getErrorCount() ?: 0) >= 1)

        Disposer.dispose(disposable)
    }
}