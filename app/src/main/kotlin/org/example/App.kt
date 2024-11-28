package org.example

import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoots
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.system.measureTimeMillis

// TODO:
// - logging
// - parameter for repo root
// - automatic extract of pensjon-regler dependencies to temp folder

fun <T> Result<T>.onFailurePrint(message: String): Result<T> = onFailure {
    println("$message: ${it.message}")
}

private fun findSourceRoots(root: File): List<String> =
    root.walk().filter { file ->
        file.isDirectory
                && (file.absolutePath.contains("repository\\") || file.absolutePath.contains("system\\"))
                && !file.absolutePath.contains("src\\test\\")
                && !file.absolutePath.contains("\\target")
                && file.name == "kotlin"
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
                        file.readText().replace("\r\n", "\n")
                    ) as KtFile
                }
        }
        .toList().also {
            println("Finished mapping KtFiles")
        }

private fun addDependenciesToClasspath(folder: File, context: CompilerContext) {
    folder.walk().filter { it.isFile && it.extension == "jar" }.toList().let { jarFiles ->
        context.configuration.addJvmClasspathRoots(jarFiles)
    }.also {
        println("Added Dependencies to Classpath")
    }
}

fun processRepo(
    context: CompilerContext,
    root: File,
    dependencies: File? = null,
): Result<AnalysisResult> {
    dependencies?.let { addDependenciesToClasspath(it, context) }

    return findKotlinSourceFiles(root, context).let { sourceFiles ->
        getBindingContext(sourceFiles, context).map { bindingContext ->
            analyzeSourceFiles2(sourceFiles, bindingContext)
        }.onSuccess {
            println("Created Binding Context")
        }
    }
}

fun analyzeRepository(
    jdkHome: File,
    repoPath: File,
    libsPath: File,
    disposable: Disposable,
): Result<AnalysisResult> =
    createCompilerContext(jdkHome, disposable)
        .map { context: CompilerContext ->
            processRepo(context, repoPath, libsPath).getOrThrow().let { result ->
                AnalysisResult(
                    services = result.services.sortedBy { it.navn },
                    flows = result.flows.sortedBy { it.navn },
                    sets = result.sets.sortedBy { it.navn }
                )
            }
        }
        .onFailurePrint("Repository analysis failed")

/**
 * arg[0] - sti til repository (C:\\data\\pensjon-regler)
 * arg[1] - sti til bibliotek med avhengigheter. Kan bruke m2 (C:\\.m2), eller så les README for å opprette  et mindre bibliotek fra pensjon-regler.
 */
fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    val elapsed = measureTimeMillis {
        try {
            analyzeRepository(
                jdkHome = File(System.getProperty("java.home")),
                repoPath = File(args[0]),
                libsPath = File(args[1]),
                disposable = disposable
            )
                .getOrNull()
                ?.let { result ->
//                    println("Rule Services:")
//                    result.services.forEach(::println)
//                    println("\nRule Flows:")
//                    result.flows.forEach(::println)
//                    println("\nRule Sets:")
//                    result.sets.forEach(::println)
//                    println("\nSummary:")
                    println("Found ${result.services.size} rule services")
                    println("Found ${result.flows.size} rule flows")
                    println("Found ${result.sets.size} rule sets")
                    generateAsciiDoc(result.services, "C:\\data\\psi-parser")
                }
        } finally {
            disposable.dispose()
        }
    }
    println("elapsed: ${String.format("%d min, %d sec", (elapsed / 1000) / 60, (elapsed / 1000) % 60)}")
}

