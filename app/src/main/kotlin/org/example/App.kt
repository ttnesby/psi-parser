package org.example

import java.io.File
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*

private fun findSourceRoots(root: File): List<String> =
        root.walk()
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

private fun findKotlinSourceFiles(root: File, context: CompilerContext): List<KtFile> {
    val sourceRoots = findSourceRoots(root)
    return sourceRoots
            .flatMap { sourceRoot ->
                File(sourceRoot)
                        .walk()
                        .filter { it.isFile && it.extension.lowercase() == "kt" }
                        .map { file ->
                            context.psiFactory.createFileFromText(
                                    file.absolutePath,
                                    KotlinFileType.INSTANCE,
                                    file.readText()
                            ) as
                                    KtFile
                        }
            }
            .toList()
}

private fun addDependenciesToClasspath(folder: File, context: CompilerContext) {
    val jarFiles = folder.walk().filter { it.isFile && it.extension == "jar" }.toList()
    context.configuration.addJvmClasspathRoots(jarFiles)
}

fun processPensjonReglerRepo(
        context: CompilerContext,
        root: File,
        dependencies: File? = null
): List<RuleServiceDoc> {

    // eventually, add the dependencies to the classpath
    dependencies?.let { addDependenciesToClasspath(it, context) }

    // Get source roots and files
    val kotlinSourceFiles = findKotlinSourceFiles(root, context)

    // Process files with binding context
    return getBindingContext(kotlinSourceFiles, context)
            .fold(
                    onSuccess = { bindingContext ->
                        kotlinSourceFiles.chunked(100).flatMap { batch ->
                            batch.flatMap { file ->
                                analyzeRuleService(file, bindingContext).also {
                                    (file as PsiFileImpl).clearCaches()
                                }
                            }
                        }
                    },
                    onFailure = { error ->
                        println("Failed to get binding context: ${error.message}")
                        emptyList()
                    }
            )
}

fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    try {
        val context: CompilerContext? =
                createCompilerContext(
                                File(System.getProperty("java.home")),
                                disposable,
                        )
                        .fold(
                                onSuccess = { context -> context },
                                onFailure = { error ->
                                    println("Failed to create compiler context: ${error.message}")
                                    null
                                }
                        )

        val ruleServices: List<RuleServiceDoc>? =
                context?.let { ctx ->
                    processPensjonReglerRepo(
                                    ctx,
                                    File("/Users/torsteinnesby/gitHub/navikt/pensjon-regler"),
                                    File("/Users/torsteinnesby/tmp/Libs")
                            )
                            .sortedBy { it.navn }
                }

        ruleServices?.forEach { info -> println(info) }
        println("Found ${ruleServices?.size} rule services")
    } finally {
        disposable.dispose()
    }
}
