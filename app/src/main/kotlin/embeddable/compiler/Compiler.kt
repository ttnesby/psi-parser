package embeddable.compiler

import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("compiler")

typealias SourceCodeToPSI = (String, String) -> KtFile
typealias PSIFilesToBindingContextResult = (List<KtFile>) -> Result<BindingContext>

data class CompilerFunctions(
    val kotlinToPSI: SourceCodeToPSI,
    val buildBindingContext: PSIFilesToBindingContextResult
)

fun initCompiler(disposable: Disposable): Result<CompilerFunctions> =
    createCompiler(File(System.getProperty("java.home")),disposable).map { (config, env) ->
        val psiFactory = PsiFileFactory.getInstance(env.project) as PsiFileFactoryImpl

        /**
         * Partial application for binding PsiFileFactory
         *
         * Creates a function that, given a [PsiFileFactoryImpl], returns a [SourceCodeToPSI].
         *
         * The resulting [SourceCodeToPSI] is itself a function taking:
         * - [fileName] (name of the file to be created),
         * - [content] (file content as a string),
         *
         * and returns a [KtFile] by using the provided [PsiFileFactoryImpl].
         *
         * @receiver A [PsiFileFactoryImpl] used to create PSI files
         * @return A [SourceCodeToPSI] function that converts a file name and content
         *         into a [KtFile] using the receiver [PsiFileFactoryImpl].
         */

        val kotlinToPSIFunction: (PsiFileFactoryImpl) -> SourceCodeToPSI = { factory ->
            { fileName, content ->
                factory.createFileFromText(fileName, KotlinFileType.INSTANCE, content) as KtFile
            }
        }

        /**
         * Partial application for binding CompilerConfiguration and KotlinCoreEnvironment
         *
         * Creates a higher-order function that, given a [CompilerConfiguration] and a [KotlinCoreEnvironment],
         * returns a [PSIFilesToBindingContextResult]. The resulting function accepts a list of [KtFile]s
         * and produces a [Result] wrapping a [BindingContext].
         *
         * Internally, this function:
         * 1. Initializes an [AnalyzerWithCompilerReport] using the [configuration].
         * 2. Creates a [CliBindingTrace] bound to the [environment.project].
         * 3. Analyzes the provided list of [KtFile]s via [TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration].
         * 4. Captures the resulting [BindingContext] in a [Result]. If the analysis fails, the exception
         *    is captured instead.
         */
        val buildBindingContextFunction:
                    (CompilerConfiguration, KotlinCoreEnvironment) -> PSIFilesToBindingContextResult =
            { configuration, environment ->
                { files ->
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
        }

        CompilerFunctions(
            kotlinToPSI = kotlinToPSIFunction(psiFactory),
            buildBindingContext = buildBindingContextFunction(config, env)
        )
    }


private fun createCompiler(
    jdkHome: File,
    disposable: Disposable
): Result<Pair<CompilerConfiguration, KotlinCoreEnvironment>> = runCatching {
    val configuration = createConfiguration(jdkHome)
    val environment = createEnvironment(configuration, disposable)


    logger.info("Compiler created")
    logger.trace("No need to add jar dependencies to classpath for now")

    configuration to environment
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
                languageVersion = LanguageVersion.KOTLIN_2_1,
                apiVersion = ApiVersion.KOTLIN_2_1
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

        put(
            CommonConfigurationKeys.PARALLEL_BACKEND_THREADS,
            maxOf(1, Runtime.getRuntime().availableProcessors() - 2)) // Parallel processing
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