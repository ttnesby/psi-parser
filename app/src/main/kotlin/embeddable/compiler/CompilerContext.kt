package embeddable.compiler

import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import java.io.File

class CompilerContext private constructor(
    val configuration: CompilerConfiguration,
    val environment: KotlinCoreEnvironment,
    val psiFactory: PsiFileFactoryImpl
) {
    companion object {

        fun new(
            jdkHome: File = File(System.getProperty("java.home")),
            //libsPath: Path,
            disposable: Disposable
        ): Result<CompilerContext> = createCompilerContext(
            jdkHome = jdkHome,
            //libsPath = libsPath,
            disposable = disposable)

        private fun createCompilerContext(
            jdkHome: File,
            //libsPath: Path,
            disposable: Disposable
        ): Result<CompilerContext> =
            runCatching {
                val configuration = createConfiguration(jdkHome)
                val environment = createEnvironment(configuration, disposable)
                val psiFactory = PsiFileFactory.getInstance(environment.project) as PsiFileFactoryImpl
                CompilerContext(
                    configuration = configuration,
                    environment = environment,
                    psiFactory = psiFactory
                ).also { _ ->
                    println("Created Compiler Context")
                    println("No need to add jar dependencies to classpath for now")
//                    val jarDependencies = jarDependencies(libsPath)
//                    context.configuration.addJvmClasspathRoots(jarDependencies)
//                    println("Added ${jarDependencies.size} jar files from $libsPath to Classpath\n")
                }
            }

//        private fun jarDependencies(folder: Path): List<File> =
//            folder
//                .walk()
//                .filter { it.isRegularFile() && it.extension == "jar" }
//                .map { it.toFile() }
//                .toList()


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
                            Unit::class.java.protectionDomain.codeSource.location
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

//            fun printSummary() {
//                println("Compilation summary:")
//                println("- Errors: $errorCount")
//                println("- Warnings: $warningCount")
//                println("- Info messages: $infoCount")
//                println()
//            }
        }
    }

    // easy navigation between source files is done via binding context, which is a map of all symbols in the project
    // the binding context is created by the Kotlin compiler analysis phase
    fun buildBindingContext(files: List<KtFile>): Result<BindingContext> =
        runCatching {
            val analyzer =
                AnalyzerWithCompilerReport(
                    configuration,
                )
            val trace = CliBindingTrace(environment.project)

            analyzer.analyzeAndReport(files) {
                TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                    environment.project,
                    files,
                    trace,
                    environment.configuration,
                    environment::createPackagePartProvider
                )
            }
            analyzer.analysisResult.bindingContext
        }

}


