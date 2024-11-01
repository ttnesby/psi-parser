package org.example

import java.io.File
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoots
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DelegatingBindingTrace

data class RuleServiceDoc(
        val navn: String,
        val beskrivelse: String,
        val inndata: List<PropertyDoc>,
        val utdata: List<PropertyDoc>,
)

data class PropertyDoc(
        val navn: String,
        val type: String,
        val beskrivelse: String,
)

fun processRepository(rootDir: File): List<RuleServiceDoc> {
    // create compiler configuration

    val disposable = Disposer.newDisposable()
    val messageCollector =
            PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false)

    try {
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

                    // Add source roots to the configuration
                    addKotlinSourceRoots(
                            rootDir.walk()
                                    .filter { it.isDirectory && it.name == "kotlin" }
                                    .map { it.absolutePath }
                                    .toList()
                    )

                    // to be continued on Monday

                    put(CommonConfigurationKeys.MODULE_NAME, "nav-merknad-pensjon")
                    put(CommonConfigurationKeys.MODULE_NAME, "nav-presentation-pensjon-guimodel")
                    put(CommonConfigurationKeys.MODULE_NAME, "nav-provider-pensjon-acl")
                    put(CommonConfigurationKeys.MODULE_NAME, "nav-provider-pensjon-api")
                    put(CommonConfigurationKeys.MODULE_NAME, "nav-repository-pensjon")
                    put(CommonConfigurationKeys.MODULE_NAME, "nav-sats-pensjon")
                    put(CommonConfigurationKeys.MODULE_NAME, "nav-system-pensjon-domain")
                    put(CommonConfigurationKeys.MODULE_NAME, "nav-presentation-pensjon-guimodel")
                    put(CommonConfigurationKeys.MODULE_NAME, "nav-system-pensjon-unleash")
                    put(CommonConfigurationKeys.MODULE_NAME, "nav-system-pensjon-util")
                    put(CommonConfigurationKeys.MODULE_NAME, "nav-merknad-pensjon")
                    put(CommonConfigurationKeys.MODULE_NAME, "no.nav.pensjonregler")
                    put(CommonConfigurationKeys.MODULE_NAME, "presentation")
                    put(CommonConfigurationKeys.MODULE_NAME, "provider")
                    put(CommonConfigurationKeys.MODULE_NAME, "repository")
                    put(CommonConfigurationKeys.MODULE_NAME, "system")

                    // classpath settings?
                }

        // init kotlin compiler environment
        // createForTests is `lighter` (faster init, less memory), BUT ide plugins dependency
        // EnvironmentConfigFiles.METADATA_CONFIG_FILES gives `lighter` environment, but does not
        // support
        // resolve of user type reference across kotlin files, dependent on analyze context
        val environment =
                KotlinCoreEnvironment.createForProduction(
                        disposable,
                        configuration,
                        EnvironmentConfigFiles.JVM_CONFIG_FILES
                )

        // init psi factory from kotlin compiler env
        val psiFactory = PsiFileFactory.getInstance(environment.project) as PsiFileFactoryImpl

        val psiSourceFiles =
                rootDir.walk()
                        .filter { it.extension == "kt" }
                        .filter { file -> file.absolutePath.contains("/src/main/") }
                        .distinctBy { it.canonicalPath } // Use canonical path to handle symlinks
                        .map { file ->
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

        // Create an analyzer with a message collector
        val analyzer = AnalyzerWithCompilerReport(configuration)

        val trace = DelegatingBindingTrace(BindingContext.EMPTY, "Trace for analyzing source files")

        // Perform analysis
        analyzer.analyzeAndReport(psiSourceFiles) {
            TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                    environment.project,
                    psiSourceFiles,
                    trace, // BindingTrace implementation
                    environment.configuration,
                    environment::createPackagePartProvider
            )
        }

        // Get the BindingContext
        val bindingContext = analyzer.analysisResult.bindingContext

        // process psi files in batches
        return psiSourceFiles.chunked(100).flatMap { batch ->
            batch.flatMap { file ->
                extractRuleService(file).also { (file as PsiFileImpl).clearCaches() }
            }
        }
    } finally {
        disposable.dispose()
    }
}

fun extractRuleService(ktFile: KtFile): List<RuleServiceDoc> {
    val ruleServices = mutableListOf<RuleServiceDoc>()

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
                        val navn = klass.name ?: "anonymous"
                        val serviceDoc =
                                RuleServiceDoc(
                                        navn = navn,
                                        beskrivelse = "test regeltjeneste ${navn}",
                                        inndata = emptyList(),
                                        utdata = emptyList(),
                                )

                        ruleServices.add(serviceDoc)

                        val t = extractRequestFields(klass)
                    }
                }
            }

    ktFile.accept(visitor)
    return ruleServices
}

fun extractRequestFields(ktClass: KtClass): List<PropertyDoc> {
    val properties = mutableListOf<PropertyDoc>()

    val primaryConstructor: KtPrimaryConstructor = ktClass.primaryConstructor ?: return emptyList()

    // Print parameter names
    primaryConstructor.valueParameters.forEach { parameter ->
        val typeRef = parameter.typeReference ?: return@forEach

        // If it's a user-defined type
        val userType = typeRef.typeElement as? KtUserType
        val referencedClass =
                userType?.referenceExpression?.references?.firstNotNullOfOrNull { ref ->
                    when (val resolved = ref.resolve()) {
                        is KtClass -> resolved // Kotlin class
                        else -> null
                    }
                }

        properties.add(
                PropertyDoc(
                        navn = parameter.name ?: "",
                        type = typeRef.text,
                        beskrivelse = referencedClass?.name ?: "Unknown type"
                )
        )

        // If you need to process the referenced class further
        if (referencedClass is KtClass) {
            // Process the referenced class properties, methods, etc.
            println("Referenced class: ${referencedClass.name}")
            println("Location: ${referencedClass.containingFile.virtualFile?.path}")
        }
    }

    // println("Parameter name: ${parameter.name}, Type: ${parameter.typeReference?.text}")

    // // Resolve the reference
    // val typeReference: KtTypeReference = parameter.typeReference ?: return@forEach
    // val nameReferenceExpression =
    //         (typeReference.firstChild as? KtUserType)?.firstChild as? KtNameReferenceExpression

    // //        if (nameReferenceExpression == null) {
    // //            return@forEach
    // //        }

    // val target: PsiElement = nameReferenceExpression.resolve() ?: return@forEach

    // // Navigate to the target's declaration
    // if (target is KtClass && target.isValid) {
    //     val navigationElement = target.navigationElement
    //     if (navigationElement is Navigatable && navigationElement.canNavigate()) {
    //         navigationElement.navigate(true)

    //         // Further processing with the KtClass target
    //         println("Found class: ${target.name}")
    //         // Add any additional handling for the KtClass here
    //     }
    // }

    return emptyList()
}

fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    try {
        val ruleServices =
                processRepository(File("/Users/torsteinnesby/gitHub/navikt/pensjon-regler"))
                        .sortedBy { it.navn }

        ruleServices.forEach { info ->
            println(
                    """
                    Navn: ${info.navn}
                    Beskrivelse: ${info.beskrivelse}
                    Inndata: ${info.inndata.joinToString(", ")}
                    Utdata: ${info.utdata.joinToString(", ")}
                    ----------------------
                """.trimIndent()
            )
        }
        println("Found ${ruleServices.size} rule services")
    } finally {
        disposable.dispose()
    }
}
