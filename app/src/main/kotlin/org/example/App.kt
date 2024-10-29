package org.example

import java.io.File
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*

// Add this data class at the top level
data class RuleServiceInfo(
        val className: String,
        val filePath: String,
        val genericType: String?,
        val methods: List<String>
)

fun processRepository(rootDir: File): List<RuleServiceInfo> {
    // create compiler configuration

    val configuration =
            CompilerConfiguration().apply {
                put(
                        CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, // configuration key
                        PrintingMessageCollector( // how to handle compiler messages
                                System.err, // direct error to standard error
                                MessageRenderer.PLAIN_FULL_PATHS, // full path in error messages
                                false // don't report errors as warnings
                        )
                )
                put(
                        CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS,
                        LanguageVersionSettingsImpl(
                                languageVersion = LanguageVersion.KOTLIN_2_0,
                                apiVersion = ApiVersion.KOTLIN_2_0
                        )
                )
                put(JVMConfigurationKeys.JVM_TARGET, JvmTarget.JVM_21)
            }

    val disposable = Disposer.newDisposable()

    // init kotlin compiler environment
    // createForTests is `lighter` (faster init, less memory), BUT ide plugins dependency
    val environment =
            KotlinCoreEnvironment.createForProduction(
                    disposable,
                    configuration,
                    EnvironmentConfigFiles.JVM_CONFIG_FILES
            )

    // init psi factory from kotlin compiler env
    val psiFactory = PsiFileFactory.getInstance(environment.project) as PsiFileFactoryImpl

    // process repo files in batches
    return rootDir.walk()
            .filter { it.extension == "kt" }
            .chunked(100) // 100 files at a time
            .flatMap { batch -> processRuleService(batch, psiFactory) }
            .toList()
            .also { disposable.dispose() }
}

fun processRuleService(files: List<File>, psiFactory: PsiFileFactoryImpl): List<RuleServiceInfo> =
        files.flatMap { file ->
            file.readText().let { content ->
                val psiFile =
                        psiFactory.createFileFromText(
                                file.name,
                                KotlinFileType.INSTANCE,
                                content,
                        ) as
                                KtFile

                extractRuleService(psiFile).also { (psiFile as PsiFileImpl).clearCaches() }
            }
        }

fun extractRuleService(ktFile: KtFile): List<RuleServiceInfo> {
    val ruleServices = mutableListOf<RuleServiceInfo>()

    val visitor =
            object : KtTreeVisitorVoid() {
                override fun visitClass(klass: KtClass) {
                    super.visitClass(klass)

                    // Check if class extends AbstractRuleService
                    if (klass.getSuperTypeListEntries().any { superTypeEntry ->
                                val superTypeName = superTypeEntry.typeReference?.text
                                superTypeName?.contains("AbstractPensjonRuleService") == true
                            }
                    ) {
                        val serviceInfo =
                                RuleServiceInfo(
                                        className = klass.name ?: "anonymous",
                                        filePath = ktFile.containingFile.name,
                                        genericType =
                                                klass.getSuperTypeListEntries()
                                                        .firstOrNull {
                                                            it.typeReference?.text?.contains(
                                                                    "AbstractPensjonRuleService"
                                                            ) == true
                                                        }
                                                        ?.typeReference
                                                        ?.text
                                                        ?.substringAfter("<")
                                                        ?.removeSuffix(">"),
                                        methods =
                                                klass.declarations.filterIsInstance<
                                                                KtNamedFunction>()
                                                        .mapNotNull { it.name }
                                )
                        ruleServices.add(serviceInfo)
                    }
                }
            }

    ktFile.accept(visitor)
    return ruleServices
}

fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    try {
        val ruleServices =
                processRepository(File("/Users/torsteinnesby/gitHub/navikt/pensjon-regler"))
                        .sortedBy { it.className }

        ruleServices.forEach { info ->
            println(
                    """
                    Class: ${info.className}
                    FilePath: ${info.filePath}
                    Generic Type: ${info.genericType}
                    Methods: ${info.methods.joinToString(", ")}
                    ----------------------
                """.trimIndent()
            )
        }
    } finally {
        disposable.dispose()
    }
}
