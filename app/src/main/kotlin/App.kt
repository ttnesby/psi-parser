import embeddable.compiler.CompilerContext
import embeddable.compiler.flatMap
import org.example.generateAsciiDoc
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import pensjon.regler.*
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.system.measureTimeMillis

// TODO:
// - logging

fun bootstrap(args: Array<String>, disposable: Disposable): Result<Unit> = runCatching {

    if (args.size != 2) {
        throw IllegalArgumentException("Usage: <path to repository> <path to output folder>")
    }

    val pathRepoRoot = Path(args[0]).also {
        if (!it.isDirectory()) {
            throw IllegalArgumentException("Path to repository is not a directory")
        }
    }
    println("Repo root is: $pathRepoRoot")

    val pathAsciiDocOutput = Path(args[1]).also {
        if (!it.isDirectory()) {
            throw IllegalArgumentException("Path to output folder is not a directory")
        }
    }
    println("AsciiDoc output path is: $pathAsciiDocOutput\n")

    Extractor.new(
        repo = Repo(pathRepoRoot),
        context = CompilerContext.new(disposable = disposable).getOrThrow()
    ).flatMap {
        it.toModel()
    }.map { result ->
        val services = result.filterIsInstance<RuleServiceInfo>()
        println("Found ${services.size} rule services")
        println("Found ${result.filterIsInstance<RuleFlowInfo>().size} rule flows")
        println("Found ${result.filterIsInstance<RuleSetInfo>().size} rule sets\n")
        generateAsciiDoc(services, pathAsciiDocOutput)
    }
}


/**
 * arg[0] - sti til repository (C:\\data\\pensjon-regler)
 * arg[2] - sti til output mappe for AsciiDoc filer
 */
fun main(args: Array<String>) {

    val disposable = Disposer.newDisposable()
    val elapsed: Long
    val exitCode: Int

    elapsed = measureTimeMillis {
        exitCode = bootstrap(args, disposable).fold(
            onSuccess = { 0 },
            onFailure = { error ->
                println("Error: ${error.message}\n")
                println("Error: ${error.stackTraceToString()}\n")
                1
            }
        )
    }

    println(
        "Elapsed time: ${
            String.format(
                "%d min, %d sec",
                (elapsed / 1000) / 60,
                (elapsed / 1000) % 60
            )
        }"
    )

    disposable.dispose()
    println("Exiting with code: $exitCode\n")
    kotlin.system.exitProcess(exitCode)
}

