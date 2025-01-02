import embeddable.compiler.BindingContextResolver
import embeddable.compiler.CompilerContext
import embeddable.compiler.flatMap
import org.example.generateAsciiDoc
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.measureTimeMillisWithResult
import pensjon.regler.*
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory

// TODO:
// - logging

private const val USAGE_ERROR = "Usage: <path to repository> <path to output folder>"
private const val REPO_ERROR = "Path to repository is not a directory"
private const val OUTPUT_ERROR = "Path to output folder is not a directory"

private fun validateDirectoryPath(path: String, errorMessage: String): Path =
    Path(path).also {
        if (!it.isDirectory()) throw IllegalArgumentException(errorMessage)
    }

private fun buildAndLogPsiFiles(compilerContext: CompilerContext, repo: Repo): List<KtFile> =
    repo.files().map { fileInfo ->
        compilerContext.createKtFile(fileInfo.file.absolutePathString(), fileInfo.content)
    }.also {
        println("Building binding context for ${it.size} files\n")
    }

private fun logExtractionResults(result: List<RuleInfo>) {
    println("\nFound ${result.filterIsInstance<RuleServiceInfo>().size} rule services")
    println("Found ${result.filterIsInstance<RuleFlowInfo>().size} rule flows")
    println("Found ${result.filterIsInstance<RuleSetInfo>().size} rule sets\n")
}

fun bootstrap(args: Array<String>, disposable: Disposable): Result<Unit> =
    runCatching {
        if (args.size != 2) throw IllegalArgumentException(USAGE_ERROR)

        val repoRoot = validateDirectoryPath(args[0], REPO_ERROR)
            .also { println("Repo root is: $it") }
        val asciiDocOutputPath = validateDirectoryPath(args[1], OUTPUT_ERROR)
            .also { println("AsciiDoc output path is: $it \n") }

        repoRoot to asciiDocOutputPath
    }
        .flatMap { (repoRoot, asciiDocOutput) ->
            CompilerContext.new(disposable = disposable)
                .flatMap { compilerContext ->
                    val repo = Repo(repoRoot)
                    val psiFiles = buildAndLogPsiFiles(compilerContext, repo)

                    compilerContext.buildBindingContext(psiFiles).map { bindingContext ->
                        // singleton for binding resolution
                        BindingContextResolver.initialize(bindingContext)
                        CodeParser.new(repo, psiFiles)
                    }
                }
                .flatMap { codeParser ->
                    codeParser.toModel()
                }
                .map { result ->
                    logExtractionResults(result)
                    generateAsciiDoc(result.filterIsInstance<RuleServiceInfo>(), asciiDocOutput)
                }
        }

/**
 * arg[0] - sti til repository (C:\\data\\pensjon-regler)
 * arg[2] - sti til output mappe for AsciiDoc filer
 */

private const val EXIT_CODE_SUCCESS = 0
private const val EXIT_CODE_FAILURE = 1

fun main(args: Array<String>) {

    val disposable = Disposer.newDisposable()

    val (elapsed, exitCode) = measureTimeMillisWithResult {
        bootstrap(args, disposable).fold(
            onSuccess = { EXIT_CODE_SUCCESS },
            onFailure = { error ->
                println("Error: ${error.message}\n")
                println("Error: ${error.stackTraceToString()}\n")
                EXIT_CODE_FAILURE
            }
        )
    }

    println("Elapsed time: ${formatElapsedTime(elapsed)}")

    cleanupAndExit(disposable, exitCode)
}

private fun formatElapsedTime(elapsed: Long): String =
    String.format(
        "%d min, %d sec",
        (elapsed / 1000) / 60,
        (elapsed / 1000) % 60
    )

private fun cleanupAndExit(disposable: Disposable, exitCode: Int) {
    disposable.dispose()
    println("Exiting with code: $exitCode\n")
    kotlin.system.exitProcess(exitCode)
}

