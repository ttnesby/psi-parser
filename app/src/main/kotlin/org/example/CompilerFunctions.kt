package org.example

import java.io.File
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext

data class CompilerContext(
        val configuration: CompilerConfiguration,
        val environment: KotlinCoreEnvironment,
        val psiFactory: PsiFileFactoryImpl
)

class MessageCollectorSummary : MessageCollector {
    private var errorCount = 0
    private var warningCount = 0
    private var infoCount = 0

    override fun clear() {
        errorCount = 0
        warningCount = 0
        infoCount = 0
    }

    override fun hasErrors(): Boolean = errorCount > 0

    override fun report(
            severity: CompilerMessageSeverity,
            message: String,
            location: CompilerMessageSourceLocation?
    ) {
        when (severity) {
            CompilerMessageSeverity.ERROR, CompilerMessageSeverity.EXCEPTION -> errorCount++
            CompilerMessageSeverity.WARNING -> warningCount++
            CompilerMessageSeverity.INFO -> infoCount++
            else -> {} // ignore other severities
        }
    }

    fun printSummary() {
        println("Compilation summary:")
        println("- Errors: $errorCount")
        println("- Warnings: $warningCount")
        println("- Info messages: $infoCount")
        println()
    }
}

fun createCompilerContext(jdkHome: File, disposable: Disposable): Result<CompilerContext> =
        runCatching {
            val configuration = createConfiguration(jdkHome)
            val environment = createEnvironment(configuration, disposable)
            val psiFactory = PsiFileFactory.getInstance(environment.project) as PsiFileFactoryImpl

            CompilerContext(
                    configuration = configuration,
                    environment = environment,
                    psiFactory = psiFactory
            )
        }

fun createKtFile(input: AnalysisInput, context: CompilerContext): Result<KtFile> = runCatching {
    context.psiFactory.createFileFromText(
            input.fileName,
            KotlinFileType.INSTANCE,
            input.sourceCode
    ) as
            KtFile
}

// easy navigation between source files is done via binding context, which is a map of all symbols
// in the project
// the binding context is created by the Kotlin compiler analysis phase
fun getBindingContext(files: List<KtFile>, context: CompilerContext): Result<BindingContext> =
        runCatching {
            val analyzer =
                    AnalyzerWithCompilerReport(
                            context.configuration,
                    )
            val trace = CliBindingTrace(context.environment.project)

            analyzer.analyzeAndReport(files) {
                TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                        context.environment.project,
                        files,
                        trace,
                        context.environment.configuration,
                        context.environment::createPackagePartProvider
                )
            }

            analyzer.analysisResult.bindingContext
        }

private fun createConfiguration(jdkHome: File): CompilerConfiguration =
        CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollectorSummary())
            put(CommonConfigurationKeys.MODULE_NAME, "need_a_module_name")
            put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_21)
            put(JVMConfigurationKeys.JDK_HOME, jdkHome)
            put(
                    CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS,
                    LanguageVersionSettingsImpl(
                            languageVersion = LanguageVersion.KOTLIN_2_0,
                            apiVersion = ApiVersion.KOTLIN_2_0
                    )
            )
            // add classpath roots for the Kotlin standard library and reflection
            addJvmClasspathRoots(
                    listOf(
                            File(
                                    kotlin.Unit::class.java.protectionDomain.codeSource.location
                                            .toURI()
                            ),
                            File(
                                    kotlin.reflect.KClass::class.java.protectionDomain.codeSource
                                            .location.toURI()
                            )
                    )
            )
        }

private fun createEnvironment(
        configuration: CompilerConfiguration,
        disposable: Disposable
): KotlinCoreEnvironment =
        KotlinCoreEnvironment.createForProduction(
                disposable,
                configuration,
                EnvironmentConfigFiles.JVM_CONFIG_FILES
        )
