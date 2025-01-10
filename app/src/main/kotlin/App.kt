@file:OptIn(ExperimentalHoplite::class)

//import org.example.generateAsciiDoc
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.sksamuel.hoplite.ConfigAlias
import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.PropertySource
import embeddable.compiler.BindingContextResolver
import embeddable.compiler.CompilerContext
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.utils.addToStdlib.measureTimeMillisWithResult
import org.slf4j.LoggerFactory
import pensjon.regler.*
import result.addons.flatMap
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.isDirectory

private const val REPO_ERROR = "Path to repository is not a directory"
private const val OUTPUT_ERROR = "Path to output folder is not a directory"

private fun validateDirectoryPath(path: Path, errorMessage: String) =
    if (!path.isDirectory()) throw IllegalArgumentException(errorMessage) else Unit

private fun buildAndLogPsiFiles(compilerContext: CompilerContext, repo: Repo): List<KtFile> =
    repo.files().map { fileInfo ->
        compilerContext.createKtFile(fileInfo.file.absolutePathString(), fileInfo.content)
    }

private fun logExtractionResults(result: List<RuleInfo>) {
    println("\nFound ${result.filterIsInstance<RuleServiceInfo>().size} rule services")
    println("Found ${result.filterIsInstance<RuleFlowInfo>().size} rule flows")
    println("Found ${result.filterIsInstance<RuleSetInfo>().size} rule sets\n")
}

// easier with custom enum versus reuse of logback level and custom decoder
enum class LogLevel {
    ALL,
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    OFF;
}

data class AppConfig(
    @param:ConfigAlias("repo")
    val repoPath: Path,
    @param:ConfigAlias("output")
    val outputPath: Path,
    @param:ConfigAlias("log")
    val level: LogLevel = LogLevel.INFO
)

private val logger = LoggerFactory.getLogger("bootstrap")

fun bootstrap(args: Array<String>, disposable: Disposable): Result<Unit> =
    runCatching {
        val config = ConfigLoader.builder()
            .addPropertySource(PropertySource.commandLine(args))
            .withExplicitSealedTypes()
            .build()
            .loadConfigOrThrow<AppConfig>()

        validateDirectoryPath(config.repoPath, REPO_ERROR)
        validateDirectoryPath(config.outputPath, OUTPUT_ERROR)

        val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        rootLogger.level = Level.valueOf(config.level.toString())

        config.repoPath to config.outputPath
    }
        .flatMap { (repoRoot, asciiDocOutput) ->
            CompilerContext.new(disposable = disposable)
                .flatMap { compilerContext ->
                    val repo = Repo(repoRoot)
                    val psiFiles = buildAndLogPsiFiles(compilerContext, repo)

                    logger.info("Building binding context for ${psiFiles.size} files")

                    val (elapsed, bindingContextResult) = measureTimeMillisWithResult {
                        compilerContext.buildBindingContext(psiFiles)
                    }

                    bindingContextResult.map { bindingContext ->
                        logger.info(" binding context done in ${formatElapsedTime(elapsed)}\n")
                        BindingContextResolver.initialize(bindingContext) // singleton for static binding context
                        CodeParser.new(repo, psiFiles)

                    }
                }
                .flatMap { codeParser ->
                    codeParser.toModel()
                }
                .map { result ->
                    logExtractionResults(result)
                    //generateAsciiDoc(result.filterIsInstance<RuleServiceInfo>(), asciiDocOutput)
                }
        }

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

