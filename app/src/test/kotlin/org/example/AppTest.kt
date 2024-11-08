/*
 * This source file was generated by the Gradle 'init' task
 */
package org.example

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AppTest {
    @Test
    @DisplayName("Should extract RuleService from KtFile")
    fun testExtractRuleService() {
        val disposable = Disposer.newDisposable()
        try {
            val configuration =
                    CompilerConfiguration().apply {
                        put(CommonConfigurationKeys.MODULE_NAME, "test-module")
                        put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_21)
                    }

            val environment =
                    KotlinCoreEnvironment.createForProduction(
                            disposable,
                            configuration,
                            EnvironmentConfigFiles.JVM_CONFIG_FILES
                    )

            val psiFactory = PsiFileFactory.getInstance(environment.project) as PsiFileFactoryImpl

            val testCode =
                    """
                class TestRuleService : AbstractPensjonRuleService<TestResponse>() {
                    data class TestRequest(val field1: String)
                    data class TestResponse(val result: String)
                }
            """.trimIndent()

            val ktFile =
                    psiFactory.createFileFromText("Test.kt", KotlinFileType.INSTANCE, testCode) as
                            KtFile

            val bindingContext = BindingContext.EMPTY

            val result = extractRuleService(ktFile, bindingContext)

            assertEquals(1, result.size)
            assertEquals("TestRuleService", result[0].navn)
        } finally {
            disposable.dispose()
        }
    }

    @Test
    @DisplayName("Should handle KtFile with no RuleServices")
    fun testExtractRuleServiceEmpty() {
        val disposable = Disposer.newDisposable()
        try {
            val configuration =
                    CompilerConfiguration().apply {
                        put(CommonConfigurationKeys.MODULE_NAME, "test-module")
                        put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_21)
                    }

            val environment =
                    KotlinCoreEnvironment.createForProduction(
                            disposable,
                            configuration,
                            EnvironmentConfigFiles.JVM_CONFIG_FILES
                    )

            val psiFactory = PsiFileFactory.getInstance(environment.project) as PsiFileFactoryImpl

            val testCode =
                    """
                class RegularClass {
                    fun someFunction() {}
                }
            """.trimIndent()

            val ktFile =
                    psiFactory.createFileFromText("Test.kt", KotlinFileType.INSTANCE, testCode) as
                            KtFile

            val bindingContext = BindingContext.EMPTY

            val result = extractRuleService(ktFile, bindingContext)

            assertTrue(result.isEmpty())
        } finally {
            disposable.dispose()
        }
    }
}
