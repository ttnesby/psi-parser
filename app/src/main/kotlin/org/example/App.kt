package org.example

import java.io.File
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoots
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.cli.jvm.compiler.CliBindingTrace
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

data class RuleServiceDoc(
        val navn: String,
        val beskrivelse: String,
        val inndata: List<PropertyDoc>,
        val utdata: List<PropertyDoc>,
) {
    override fun toString(): String {
        return """
            |RuleServiceDoc(
            |   navn = $navn,
            |   beskrivelse = $beskrivelse,
            |   inndata = $inndata,
            |   utdata = $utdata
            |)
        """.trimMargin()
    }
}

data class PropertyDoc(
        val navn: String,
        val type: String,
        val beskrivelse: String,
) {
    override fun toString(): String {
        return """
                |PropertyDoc(
                |   navn = $navn,
                |   type = $type,
                |   beskrivelse = $beskrivelse
                |)
            """.trimMargin()
    }
}

data class RuleFlowDoc(
        val navn: String,
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

data class CompilerAnalyzerResult(
        val psiSourceFiles: List<KtFile>,
        val analyzer: AnalyzerWithCompilerReport,
        val environment: KotlinCoreEnvironment
)

private fun compilerEnvironment(
        kotlinSourceRoots: List<String>,
        kotlinSourceFiles: List<String>
): CompilerAnalyzerResult {
    val disposable = Disposer.newDisposable()

    try {
        val messageCollector = MessageCollectorSummary()
        val configuration =
                CompilerConfiguration().apply {
                    put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
                    put(
                            CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS,
                            LanguageVersionSettingsImpl(
                                    languageVersion = LanguageVersion.KOTLIN_2_0,
                                    apiVersion = ApiVersion.KOTLIN_2_0
                            )
                    )
                    put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_21)
                    put(
                            JVMConfigurationKeys.JDK_HOME,
                            File("/Users/torsteinnesby/.sdkman/candidates/java/current")
                    )
                    addKotlinSourceRoots(kotlinSourceRoots)
                    put(CommonConfigurationKeys.MODULE_NAME, "repository-analysis")
                }

        // add kotlin stdlib and reflection
        configuration.addJvmClasspathRoots(
                listOf(
                        File(kotlin.Unit::class.java.protectionDomain.codeSource.location.toURI()),
                        File(
                                kotlin.reflect.KClass::class.java.protectionDomain.codeSource
                                        .location.toURI()
                        )
                )
        )

        val libsDir = File("/Users/torsteinnesby/tmp/Libs")
        val dependencyJars = libsDir.walk().filter { it.isFile && it.extension == "jar" }.toList()
        configuration.addJvmClasspathRoots(dependencyJars)

        val environment =
                KotlinCoreEnvironment.createForProduction(
                        disposable,
                        configuration,
                        EnvironmentConfigFiles.JVM_CONFIG_FILES
                )

        val psiFactory = PsiFileFactory.getInstance(environment.project) as PsiFileFactoryImpl

        val psiSourceFiles =
                kotlinSourceFiles
                        .map { filePath ->
                            val file = File(filePath)
                            val content = file.readText()
                            val psiFile =
                                    psiFactory.createFileFromText(
                                            file.name,
                                            KotlinFileType.INSTANCE,
                                            content
                                    ) as
                                            KtFile
                            psiFile
                        }
                        .toList()

        val analyzer = AnalyzerWithCompilerReport(configuration)
        val trace = CliBindingTrace(environment.project)

        analyzer.analyzeAndReport(psiSourceFiles) {
            TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                    environment.project,
                    psiSourceFiles,
                    trace,
                    environment.configuration,
                    environment::createPackagePartProvider
            )
        }

        messageCollector.printSummary()

        return CompilerAnalyzerResult(psiSourceFiles, analyzer, environment)
    } finally {
        disposable.dispose()
    }
}

fun processRepository(rootDir: File): List<RuleServiceDoc> {
    // define source roots
    val kotlinSourceRoots =
            rootDir.walk()
                    .filter { file ->
                        file.isDirectory &&
                                (file.absolutePath.contains("repository/") ||
                                        file.absolutePath.contains("system/")) &&
                                !file.absolutePath.contains("src/test/") &&
                                !file.absolutePath.contains("/target/") &&
                                file.name == "kotlin"
                    }
                    .map { it.absolutePath }
                    .toList()

    // then, find all Kotlin files under these roots
    val kotlinSourceFiles =
            kotlinSourceRoots
                    .flatMap { sourceRoot ->
                        File(sourceRoot)
                                .walk()
                                .filter { it.isFile }
                                .filter { it.extension.lowercase() == "kt" }
                                .map { it.absolutePath }
                    }
                    .toList()

    val disposable = Disposer.newDisposable()
    try {
        val compilerResult = compilerEnvironment(kotlinSourceRoots, kotlinSourceFiles)

        val bindingContext = compilerResult.analyzer.analysisResult.bindingContext

        // process psi files in batches
        return compilerResult.psiSourceFiles.chunked(100).flatMap { batch ->
            batch.flatMap { file ->
                extractRuleService(file, bindingContext).also {
                    (file as PsiFileImpl).clearCaches()
                }
            }
        }
    } finally {
        disposable.dispose()
    }
}

fun extractRuleService(ktFile: KtFile, bindingContext: BindingContext): List<RuleServiceDoc> {
    val ruleServices = mutableListOf<RuleServiceDoc>()

    val ruleServiceClasses =
            ktFile.declarations.filterIsInstance<KtClass>().filter { klass ->
                klass.getSuperTypeListEntries().any { superTypeEntry ->
                    superTypeEntry.typeReference?.text?.contains("AbstractPensjonRuleService") ==
                            true
                }
            }

    ruleServices.addAll(
            ruleServiceClasses.map { klass ->
                val navn = klass.name ?: "anonymous"
                RuleServiceDoc(
                        navn = navn,
                        beskrivelse = "test regeltjeneste ${navn}",
                        inndata = extractRequestFields(klass, bindingContext),
                        utdata = extractResponseFields(klass, bindingContext)
                )
            }
    )
    return ruleServices
}

fun extractRequestFields(ktClass: KtClass, bindingContext: BindingContext): List<PropertyDoc> {
    val props = mutableListOf<PropertyDoc>()
    val primaryConstructor = ktClass.primaryConstructor ?: return emptyList()

    primaryConstructor.valueParameters.forEach { parameter ->
        val typeRef = parameter.typeReference ?: return@forEach
        val type = bindingContext.get(BindingContext.TYPE, typeRef)

        // Get the declared type's classification
        val typeClassifier = type?.constructor?.declarationDescriptor

        if (typeClassifier != null) {
            // Find the source for this type
            val declaration = DescriptorToSourceUtils.getSourceFromDescriptor(typeClassifier)

            if (declaration is KtClass) {
                // Now we have the actual class declaration
                val properties =
                        declaration.body?.properties?.map { prop ->
                            PropertyDoc(
                                    navn = prop.name ?: "",
                                    type = prop.typeReference?.text ?: "Unknown",
                                    beskrivelse = prop.docComment?.getText() ?: ""
                            )
                        }
                                ?: emptyList()

                // Add the parameter info
                props.add(
                        PropertyDoc(
                                navn = parameter.name ?: "",
                                type = typeRef.text,
                                beskrivelse = "Parameter of ${ktClass.name}"
                        )
                )

                props.addAll(properties)
            }
        }
    }

    return props
}

fun extractResponseFields(ktClass: KtClass, bindingContext: BindingContext): List<PropertyDoc> {
    val props = mutableListOf<PropertyDoc>()

    // Find the supertype entry for AbstractPensjonRuleService
    val abstractServiceEntry =
            ktClass.getSuperTypeListEntries().find { superTypeEntry ->
                superTypeEntry.typeReference?.text?.contains("AbstractPensjonRuleService") == true
            }
                    ?: return emptyList()

    // Get the generic type argument (response type)
    val typeArguments = abstractServiceEntry.typeReference?.typeElement?.typeArgumentsAsTypes
    val responseTypeRef = typeArguments?.getOrNull(0) ?: return emptyList()

    // Get binding context information for the response type
    val responseType = bindingContext.get(BindingContext.TYPE, responseTypeRef)
    val typeClassifier = responseType?.constructor?.declarationDescriptor

    if (typeClassifier != null) {
        // Find the source declaration for the response type
        val declaration = DescriptorToSourceUtils.getSourceFromDescriptor(typeClassifier)

        if (declaration is KtClass) {
            // Add the response class itself
            props.add(
                    PropertyDoc(
                            navn = declaration.name ?: "",
                            type = responseTypeRef.text,
                            beskrivelse = declaration.docComment?.getText()
                                            ?: "Response for ${ktClass.name}"
                    )
            )

            // Add all properties from the response class
            val properties =
                    declaration.body?.properties?.map { prop ->
                        PropertyDoc(
                                navn = prop.name ?: "",
                                type = prop.typeReference?.text ?: "Unknown",
                                beskrivelse = prop.docComment?.getText() ?: ""
                        )
                    }
                            ?: emptyList()

            props.addAll(properties)
        }
    }

    return props
}

fun extractRuleFlow(ktClass: KtClass, bindingContext: BindingContext): List<RuleFlowDoc> {
    val ruleFlows = mutableListOf<RuleFlowDoc>()

    return ruleFlows
}

fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    try {
        val ruleServices =
                processRepository(File("/Users/torsteinnesby/gitHub/navikt/pensjon-regler"))
                        .sortedBy { it.navn }

        ruleServices.forEach { info -> println(info) }
        println("Found ${ruleServices.size} rule services")
    } finally {
        disposable.dispose()
    }
}
