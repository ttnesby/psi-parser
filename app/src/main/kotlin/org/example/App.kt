package org.example

import embeddable.compiler.CompilerContext
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import pensjon.regler.Repo
import pensjon.regler.RepoAnalyzer
import kotlin.io.path.Path
import kotlin.system.measureTimeMillis

// TODO:
// - logging

fun bootstrap(args: Array<String>, disposable: Disposable): Result<Unit> = runCatching {

    if (args.size != 2) {
        throw IllegalArgumentException("Usage: <path to repository> <path to output folder>")
    }

    val elapsed = measureTimeMillis {

        println("Repo root is: ${args[0]}")
        println("AsciiDoc output path is: ${args[1]}\n")

        Repo.initialize(Path(args[0]))

        val repoAnalyzer = RepoAnalyzer.new(
            CompilerContext.new(disposable = disposable).getOrThrow()
        ).getOrThrow()

        repoAnalyzer.analyze().getOrThrow().let { result ->
            println("Found ${result.services.size} rule services")
            println("Found ${result.flows.size} rule flows")
            println("Found ${result.sets.size} rule sets\n")
            // TODO: Add support for Result<T> in generateAsciiDoc
            generateAsciiDoc(ruleDocs = result.services, outputPath = Path(args[1]))
        }
    }
    println("elapsed: ${String.format("%d min, %d sec", (elapsed / 1000) / 60, (elapsed / 1000) % 60)}")
}


/**
 * arg[0] - sti til repository (C:\\data\\pensjon-regler)
 * arg[2] - sti til output mappe for AsciiDoc filer
 */
fun main(args: Array<String>) {

    val disposable = Disposer.newDisposable()

    val exitCode = bootstrap(args, disposable).fold(
        onSuccess = { 0 },
        onFailure = { error ->
            println("Error: ${error.message}")
            println("Error: ${error.stackTraceToString()}")
            1
        }
    )

    disposable.dispose()
    kotlin.system.exitProcess(exitCode)
}

