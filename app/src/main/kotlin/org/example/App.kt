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
import org.jetbrains.kotlin.com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.psi.*

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

                // Add source roots to the configuration
                addKotlinSourceRoots(
                        rootDir.walk()
                                .filter { it.isDirectory && it.name == "kotlin" }
                                .map { it.absolutePath }
                                .toList()
                )
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

    // Create an analysis context
    val analyzer = AnalyzerWithCompilerReport(configuration)

    // Analyze all files
//    // Create trace holder
//    val trace = CliLightClassGenerationSupport.NoScopeRecordCliBindingTrace()

    // Get the module
//    val moduleContext =
//            TopDownAnalyzerFacadeForJVM.createContextWithSealedModule(
//                    environment.project,
//                    environment.configuration
//            )

    // Analyze all files
    val analysisResult =
            analyzer.analyzeAndReport(environment.getSourceFiles()) {
                TopDownAnalyzerFacadeForJVM.analyzeFilesWithJavaIntegration(
                    project = environment.project,
                    files = environment.getSourceFiles(),
                    trace = NoScopeRecordCliBindingTrace(environment.project),
                    configuration = configuration,
                    packagePartProvider = environment.createPackagePartProvider(scope = GlobalSearchScope.fileScope()),
                    declarationProviderFactory = TODO(),
                    sourceModuleSearchScope = TODO(),
                    klibList = TODO(),
                    explicitModuleDependencyList = TODO(),
                    explicitModuleFriendsList = TODO(),
                    explicitCompilerEnvironment = TODO(),
                    packagePartProvider = TODO(),
                    declarationProviderFactory = TODO(),
                    sourceModuleSearchScope = TODO(),
                    klibList = TODO(),
                    explicitModuleDependencyList = TODO(),
                    explicitModuleFriendsList = TODO(),
                    explicitCompilerEnvironment = TODO()
                )
//                ,
//                        packagePartProvider = environment.createPackagePartProvider(),
//                        containerSource = null
//                )
            }

    // init psi factory from kotlin compiler env
    val psiFactory = PsiFileFactory.getInstance(environment.project) as PsiFileFactoryImpl

    // process repo files in batches
    return rootDir.walk()
            .filter { it.extension == "kt" }
            .filter { file ->
                //                // Exclude common build and test directories
                //                !file.absolutePath.contains("/build/") &&
                //                        !file.absolutePath.contains("/target/") &&
                //                        !file.absolutePath.contains("/generated/") &&
                //                        // Optional: only include main source directories
                file.absolutePath.contains("/src/main/")
            }
            .distinctBy { it.canonicalPath } // Use canonical path to handle symlinks
            .chunked(100) // 100 files at a time
            .flatMap { batch -> processRuleService(batch, psiFactory) }
            .toList()
            .also { disposable.dispose() }
}

fun processRuleService(files: List<File>, psiFactory: PsiFileFactoryImpl): List<RuleServiceDoc> =
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
