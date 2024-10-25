package org.example

import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.io.File

fun main(args: Array<String>) {
    val sourceFile = if (args.isNotEmpty()) args[0] else "/Users/torsteinnesby/gitHub/navikt/pensjon-regler/repository/nav-repository-pensjon/src/main/kotlin/no/nav/domain/pensjon/regler/repository/tjeneste/beregnytelse/flyter/StartBeregnYtelseFlyt.kt"

    // Create compiler configuration
    val configuration = CompilerConfiguration().apply {
        put(
            CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY,
            PrintingMessageCollector(System.err, MessageRenderer.PLAIN_FULL_PATHS, false)
        )
    }

    // Create compiler environment
    val disposable = Disposer.newDisposable()
    val environment = KotlinCoreEnvironment.createForProduction(
        disposable,
        configuration,
        EnvironmentConfigFiles.JVM_CONFIG_FILES
    )

    try {
        // Read and parse the Kotlin file
        val content = File(sourceFile).readText()
        val psiFactory = PsiFileFactory.getInstance(environment.project) as PsiFileFactoryImpl
        val psiFile = psiFactory.createFileFromText(sourceFile, KotlinFileType.INSTANCE, content) as KtFile

        // Visit the PSI tree and collect forgrening/gren calls
        var forgrenCount = 0
        var grenCount = 0

        psiFile.accept(object : KtTreeVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)

                when (expression.calleeExpression?.text) {
                    "forgrening" -> {
                        forgrenCount++
                        println("Found forgrening: ${expression.valueArguments.firstOrNull()?.text ?: "unnamed"}")
                    }
                    "gren" -> {
                        grenCount++
                        println("Found gren at line ${expression.node.startOffset}")
                    }
                }
            }
        })

        println("\nSummary:")
        println("Total forgrening calls: $forgrenCount")
        println("Total gren calls: $grenCount")
    } finally {
        disposable.dispose()
    }
}