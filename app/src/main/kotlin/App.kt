//import org.example.generateAsciiDoc
import embeddable.compiler.initCompiler
import embeddable.compiler.initResolveToDescriptorFunction
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.utils.addToStdlib.measureTimeMillisWithResult
import org.slf4j.LoggerFactory
import pensjon.regler.*
import pensjon.regler.repo.initDefaultSourceRootFilterFunction
import pensjon.regler.repo.repoSourceInfo
import result.addons.flatMap
import kotlin.io.path.absolutePathString

private val logger = LoggerFactory.getLogger("bootstrap")

private fun logExtractionResults(result: List<RuleInfo>) {
    logger.info("--- RESULT ---\n")
    logger.info("Found ${result.filterIsInstance<RuleServiceInfo>().size} rule services")
    logger.info("Found ${result.filterIsInstance<RuleFlowInfo>().size} rule flows")
    logger.info("Found ${result.filterIsInstance<RuleSetInfo>().size} rule sets\n")
}

fun bootstrap(args: Array<String>, disposable: Disposable): Result<Unit> =
    validateConfig(args).flatMap { config ->
        initCompiler(disposable = disposable).flatMap { compilerFunctions ->
            repoSourceInfo(config.repoPath, initDefaultSourceRootFilterFunction).flatMap { sourceInfo ->

                val psiFiles = sourceInfo.files.map { sourceFile ->
                    compilerFunctions.kotlinToPSI(sourceFile.path.absolutePathString(), sourceFile.content)
                }

                logger.info("${psiFiles.size} kotlin files mapped to PSI format")
                logger.info("Building binding context for PSI files")

                val (elapsed, bindingContextResult) = measureTimeMillisWithResult {
                    compilerFunctions.buildBindingContext(psiFiles)
                }

                bindingContextResult.flatMap { bindingContext ->
                    logger.info("binding context done in ${formatElapsedTime(elapsed)}\n")
                    logger.info("start parsing")

                    psiFilesToModel(
                        psiFiles,
                        ParserConfig(
                            toGitHubURI = sourceInfo.toGitHubURI,
                            resolveToDescriptor = initResolveToDescriptorFunction(bindingContext),
                            relaxedMode = config.relaxedMode
                        )
                    )
                }
            }
        }
    }
        .map { result ->
            logExtractionResults(result)
            //generateAsciiDoc(result.filterIsInstance<RuleServiceInfo>(), asciiDocOutput)
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

