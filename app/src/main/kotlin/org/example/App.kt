package org.example

import java.io.File
import kotlin.getOrThrow
import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.*

// TODO:
// - logging
// - parameter for repo root
// - automatic extract of pensjon-regler dependencies to temp folder

fun <T> Result<T>.onFailurePrint(message: String): Result<T> = onFailure {
    println("$message: ${it.message}")
}

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

private fun findKotlinSourceFiles(root: File, context: CompilerContext): List<KtFile> =
        findSourceRoots(root)
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

private fun addDependenciesToClasspath(folder: File, context: CompilerContext) {
    folder.walk().filter { it.isFile && it.extension == "jar" }.toList().let { jarFiles ->
        context.configuration.addJvmClasspathRoots(jarFiles)
    }
}

fun processRepo(
        context: CompilerContext,
        root: File,
        dependencies: File? = null
): Result<List<RuleServiceDoc>> {
    dependencies?.let { addDependenciesToClasspath(it, context) }

    return findKotlinSourceFiles(root, context).let { sourceFiles ->
        getBindingContext(sourceFiles, context).map { bindingContext ->
            analyzeSourceFiles(sourceFiles, bindingContext)
        }
    }
}

fun analyzeRepository(
        jdkHome: File,
        repoPath: File,
        libsPath: File,
        disposable: Disposable
): Result<List<RuleServiceDoc>> =
        createCompilerContext(jdkHome, disposable)
                .map { context: CompilerContext ->
                    processRepo(context, repoPath, libsPath).getOrThrow().sortedBy { it.navn }
                }
                .onFailurePrint("Repository analysis failed")

fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    try {
        analyzeRepository(
                        jdkHome = File(System.getProperty("java.home")),
                        repoPath = File("/Users/torsteinnesby/gitHub/navikt/pensjon-regler"),
                        libsPath = File("/Users/torsteinnesby/tmp/Libs"),
                        disposable = disposable
                )
                .getOrNull()
                ?.let { ruleServices ->
                    ruleServices.forEach(::println)
                    println("Found ${ruleServices.size} rule services")
                }
    } finally {
        disposable.dispose()
    }
}
