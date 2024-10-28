package org.example

import java.io.File
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*

fun processRepository(rootDir: File) {
    // Create compiler configuration
    val configuration =
        CompilerConfiguration().apply {
            put(
                CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY,
                PrintingMessageCollector(
                    System.err,
                    MessageRenderer.PLAIN_FULL_PATHS,
                    false
                )
            )
        }

    val disposable = Disposer.newDisposable()
    val environment =
            KotlinCoreEnvironment.createForProduction(
                    disposable,
                    configuration,
                    EnvironmentConfigFiles.JVM_CONFIG_FILES
            )

    // Process files in batches
    rootDir.walk()
            .filter { it.extension == "kt" }
            .chunked(100) // Process 100 files at a time
            .forEach { batch ->
                processBatch(batch, environment)
                System.gc() // Suggest garbage collection between batches
            }
}

fun processBatch(files: List<File>, environment: KotlinCoreEnvironment) {
    files.forEach { file ->
        val content = File(file.absolutePath).readText()
        val psiFactory = PsiFileFactory.getInstance(environment.project) as PsiFileFactoryImpl
        val psiFile =
                psiFactory.createFileFromText(
                        file.name,
                        KotlinFileType.INSTANCE,
                        content,
                ) as
                        KtFile

        // Process the PSI file
        extractRelevantInformation(psiFile)

        // Clear references to allow garbage collection
        (psiFile as PsiFileImpl).clearCaches()
    }
}

fun extractRelevantInformation(ktFile: KtFile) {
    val visitor =
            object : KtTreeVisitorVoid() {
                override fun visitElement(element: PsiElement) {
                    // Process only what you need
                    when (element) {
                        is KtClass -> {
                            // Extract class information
                            println(
                                "Found class: ${element.name}"
                            )
                        }
                        is KtFunction -> {
                            // Extract function information
                            println(
                                "Found function: ${element.name}"
                            )
                        }
                    // Add other relevant elements
                    }
                    super.visitElement(element)
                }
            }

    ktFile.accept(visitor)
}

fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    try {
        // Your processing logic
        processRepository(File("/Users/torsteinnesby/gitHub/navikt/pensjon-regler"))
    } finally {
        disposable.dispose()
    }
}
