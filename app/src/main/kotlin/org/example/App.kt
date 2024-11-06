package org.example

import java.io.File
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoots
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
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
)

data class PropertyDoc(
        val navn: String,
        val type: String,
        val beskrivelse: String,
)

fun processRepository(rootDir: File): List<RuleServiceDoc> {
    // create compiler configuration

    val kotlinSourceRoots =
            rootDir.walk()
                    .filter { file ->
                        file.isDirectory &&
                                file.absolutePath.contains("repository/") &&
                                !file.absolutePath.contains("src/test/") &&
                                !file.absolutePath.contains("/target/") &&
                                file.name == "kotlin"
                    }
                    .map { it.absolutePath }
                    .toList()

    // Then, find all Kotlin files under these roots
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

    // println(kotlinSourceRoots)
    // println(kotlinSourceFiles)
    // return emptyList()

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

                    put(
                            JVMConfigurationKeys.JDK_HOME,
                            File("/Users/torsteinnesby/.sdkman/candidates/java/current")
                    )

                    // Add source roots to the configuration
                    addKotlinSourceRoots(kotlinSourceRoots)

                    put(CommonConfigurationKeys.MODULE_NAME, "repository-analysis")

                    // put(CommonConfigurationKeys.MODULE_NAME, "nav-merknad-pensjon")
                    // put(CommonConfigurationKeys.MODULE_NAME, "nav-repository-pensjon")
                    // put(CommonConfigurationKeys.MODULE_NAME, "nav-sats-pensjon")
                    // put(CommonConfigurationKeys.MODULE_NAME, "repository")
                    // put(CommonConfigurationKeys.MODULE_NAME, "nav-presentation-pensjon-guimodel")
                    // put(CommonConfigurationKeys.MODULE_NAME, "nav-provider-pensjon-acl")
                    // put(CommonConfigurationKeys.MODULE_NAME, "nav-provider-pensjon-api")
                    // put(CommonConfigurationKeys.MODULE_NAME, "nav-system-pensjon-domain")
                    // put(CommonConfigurationKeys.MODULE_NAME, "nav-presentation-pensjon-guimodel")
                    // put(CommonConfigurationKeys.MODULE_NAME, "nav-system-pensjon-unleash")
                    // put(CommonConfigurationKeys.MODULE_NAME, "nav-system-pensjon-util")
                    // put(CommonConfigurationKeys.MODULE_NAME, "nav-merknad-pensjon")
                    // put(CommonConfigurationKeys.MODULE_NAME, "no.nav.pensjonregler")
                    // put(CommonConfigurationKeys.MODULE_NAME, "presentation")
                    // put(CommonConfigurationKeys.MODULE_NAME, "provider")
                    // put(CommonConfigurationKeys.MODULE_NAME, "system")
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
        // add pensjon-regler JARS
        // based on `mvn dependency:copy-dependencies
        // -DoutputDirectory=/Users/torsteinnesby/tmp/Libs`

        val libsDir = File("/Users/torsteinnesby/tmp/Libs")
        val dependencyJars = libsDir.walk().filter { it.isFile && it.extension == "jar" }.toList()
        configuration.addJvmClasspathRoots(dependencyJars)

        // init kotlin compiler environment
        val environment =
                KotlinCoreEnvironment.createForProduction(
                        disposable,
                        configuration,
                        EnvironmentConfigFiles.JVM_CONFIG_FILES
                )

        // init psi factory from kotlin compiler env
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

        // Create an analyzer with a message collector
        val analyzer = AnalyzerWithCompilerReport(configuration)
        val trace = CliBindingTrace(environment.project)

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
                                        inndata = extractRequestFields(klass, bindingContext),
                                        utdata = emptyList(),
                                )

                        ruleServices.add(serviceDoc)
                    }
                }
            }

    ktFile.accept(visitor)
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

                // You might want to process the properties of the referenced class here
                // properties.addAll(extractPropertiesFromClass(declaration))
            }
        }
    }

    return props
}

// fun extractRequestFields(ktClass: KtClass, bindingContext: BindingContext): List<PropertyDoc> {
//     val properties = mutableListOf<PropertyDoc>()

//     val primaryConstructor: KtPrimaryConstructor = ktClass.primaryConstructor ?: return
// emptyList()

//     // Print parameter names
//     primaryConstructor.valueParameters.forEach { parameter ->
//         val typeRef = parameter.typeReference ?: return@forEach

//         // If it's a user-defined type
//         val userType = typeRef.typeElement as? KtUserType
//         val referencedClass =
//                 userType?.referenceExpression?.references?.firstNotNullOfOrNull { ref ->
//                     when (val resolved = ref.resolve()) {
//                         is KtClass -> resolved // Kotlin class
//                         else -> null
//                     }
//                 }

//         properties.add(
//                 PropertyDoc(
//                         navn = parameter.name ?: "",
//                         type = typeRef.text,
//                         beskrivelse = referencedClass?.name ?: "Unknown type"
//                 )
//         )

//         // If you need to process the referenced class further
//         if (referencedClass is KtClass) {
//             // Process the referenced class properties, methods, etc.
//             println("Referenced class: ${referencedClass.name}")
//             println("Location: ${referencedClass.containingFile.virtualFile?.path}")
//         }
//     }

//     // println("Parameter name: ${parameter.name}, Type: ${parameter.typeReference?.text}")

//     // // Resolve the reference
//     // val typeReference: KtTypeReference = parameter.typeReference ?: return@forEach
//     // val nameReferenceExpression =
//     //         (typeReference.firstChild as? KtUserType)?.firstChild as?
// KtNameReferenceExpression

//     // //        if (nameReferenceExpression == null) {
//     // //            return@forEach
//     // //        }

//     // val target: PsiElement = nameReferenceExpression.resolve() ?: return@forEach

//     // // Navigate to the target's declaration
//     // if (target is KtClass && target.isValid) {
//     //     val navigationElement = target.navigationElement
//     //     if (navigationElement is Navigatable && navigationElement.canNavigate()) {
//     //         navigationElement.navigate(true)

//     //         // Further processing with the KtClass target
//     //         println("Found class: ${target.name}")
//     //         // Add any additional handling for the KtClass here
//     //     }
//     // }

//     return properties
// }

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
