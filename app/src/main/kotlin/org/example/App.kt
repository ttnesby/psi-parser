package org.example

import embeddable.compiler.CompilerContext
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.system.measureTimeMillis

// TODO:
// - logging
// - parameter for repo root
// - automatic extract of pensjon-regler dependencies to temp folder

fun <T> Result<T>.onFailurePrint(message: String): Result<T> = onFailure {
    println("$message: ${it.message}")
}

private fun findSourceRoots(root: Path): List<Path> =
    root.walk(PathWalkOption.INCLUDE_DIRECTORIES)
        .filter { path ->
            path.isDirectory()
                    && (path.contains("/repository/") || path.contains("/system/"))
                    && !(path.contains("/src/test/") || path.contains("/target/"))
                    && path.name == "kotlin"
        }
        .toList()
        .also {
            println("Found ${it.size} source roots")
            println(it.joinToString("\n"))
        }

// Extension function to safely check path components, normalize path separators from Windows to Mac/Linux
private fun Path.contains(subPath: String): Boolean =
    this.absolutePathString().replace('\\', '/').contains(subPath)

private fun findKotlinSourceFiles(sourceRoots: List<Path>, context: CompilerContext): List<KtFile> =
    sourceRoots.flatMap { sourceRoot ->
        sourceRoot
            .walk()
            .filter { it.isRegularFile() && it.extension.lowercase() == "kt" }
            .map { file ->
                context.psiFactory.createFileFromText(
                    file.absolutePathString(),
                    KotlinFileType.INSTANCE,
                    file.readText().replace("\r\n", "\n")
                ) as KtFile
            }

    }
        .toList()
        .also {
            println("Finished mapping ${it.size} kt files to PSI format")
        }

fun processRepo(
    repoPath: Path,
    libsPath: Path,
    disposable: Disposable,
): Result<AnalysisResult> =

    CompilerContext.new(libsPath = libsPath, disposable = disposable).flatMap { context ->

        val psiFiles = findKotlinSourceFiles(findSourceRoots(repoPath), context)

        context.buildBindingContext(psiFiles.toList()).flatMap { bindingContext ->
            analyzeSourceFiles(
                psiFiles,
                bindingContext
            )
        }
    }

/**
 * arg[0] - sti til repository (C:\\data\\pensjon-regler)
 * arg[1] - sti til bibliotek med avhengigheter. Kan bruke m2 (C:\\.m2), eller så les README for å opprette  et mindre bibliotek fra pensjon-regler.
 */
fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    val elapsed = measureTimeMillis {

        println("Repo root is: ${args[0]}")
        println("Libs path is: ${args[1]}")

        processRepo(
            repoPath = Path(args[0]),
            libsPath = Path(args[1]),
            disposable = disposable
        ).map { result ->
            println("Found ${result.services.size} rule services")
            println("Found ${result.flows.size} rule flows")
            println("Found ${result.sets.size} rule sets")
            generateAsciiDoc(result.services, "C:\\data\\psi-parser")
        }.onFailure {
            println("Failed to process repo: ${it.stackTraceToString()}")
        }
    }
    disposable.dispose()
    println("elapsed: ${String.format("%d min, %d sec", (elapsed / 1000) / 60, (elapsed / 1000) % 60)}")
}

