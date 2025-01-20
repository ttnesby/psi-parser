package embeddable.compiler

import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class CompilerTest {

    private lateinit var jdkHome: File
    private lateinit var disposable: Disposable

    @BeforeEach
    fun setUp() {
        jdkHome = File(System.getProperty("java.home"))
        disposable = Disposer.newDisposable()
    }

    @AfterEach
    fun tearDown() {
        disposable.dispose()
    }

    @Test
    fun `test CompilerContext creation succeeds with valid parameters`() {
        val compilerFunctions = initCompiler(disposable = disposable)

        assertTrue(compilerFunctions.isSuccess)
    }

    @Test
    fun `test KtFile creation`() {
        val compilerFunctions = initCompiler(disposable = disposable).getOrThrow()

        val ktFileName = "TestFile.kt"
        val ktFileContent = "fun main() { println(\"Hello, Kotlin\") }"
        val ktFile = compilerFunctions.kotlinToPSI(ktFileName, ktFileContent)

        assertNotNull(ktFile)
        assertEquals(ktFileName, ktFile.name)
        assertEquals(ktFileContent, ktFile.text)
    }

    @Test
    fun `test BindingContext creation succeeds with valid Kotlin files`() {
        val compilerFunctions = initCompiler(disposable = disposable).getOrThrow()

        val fileName = "Hello.kt"
        val content = "fun main() { println(\"Hello, World\") }"
        val ktFile = compilerFunctions.kotlinToPSI(fileName, content)

        val result = compilerFunctions.buildBindingContext(listOf(ktFile))
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test BindingContext creation succeeds even with invalid Kotlin files`() {
        val compilerFunctions = initCompiler(disposable = disposable).getOrThrow()

        val fileName = "BrokenFile.kt"
        val invalidContent = "fun main { println(\"Broken code\") "
        val ktFile = compilerFunctions.kotlinToPSI(fileName, invalidContent)

        val result = compilerFunctions.buildBindingContext(listOf(ktFile))
        assertTrue(result.isSuccess)
    }
}