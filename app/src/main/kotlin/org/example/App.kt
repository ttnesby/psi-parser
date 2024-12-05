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


fun <T> Result<T>.onFailurePrint(message: String): Result<T> = onFailure {
    println("$message: ${it.message}")
}

fun processRepo(
    repoPath: Path,
    //libsPath: Path,
    disposable: Disposable,
): Result<AnalysisResult> =

    CompilerContext.new(
        //libsPath = libsPath,
        disposable = disposable).flatMap { context ->

        val psiFiles = sourceFilesToPSI(sourceRoots(repoPath), context)

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
 * arg[2] - sti til output mappe for AsciiDoc filer
 */
fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    val elapsed = measureTimeMillis {

        println("Repo root is: ${args[0]}")
        //println("Libs path is: ${args[1]}")
        println("AsciiDoc output path is: ${args[1]}\n")

        processRepo(
            repoPath = Path(args[0]),
            //libsPath = Path(args[1]),
            disposable = disposable
        ).map { result ->
            println("Found ${result.services.size} rule services")
            println("Found ${result.flows.size} rule flows")
            println("Found ${result.sets.size} rule sets\n")
            generateAsciiDoc(ruleDocs = result.services, outputPath = Path(args[1]))
        }.onFailure {
            println("Failed to process repo: ${it.stackTraceToString()}")
        }
    }
    disposable.dispose()
    println("elapsed: ${String.format("%d min, %d sec", (elapsed / 1000) / 60, (elapsed / 1000) % 60)}")
}

