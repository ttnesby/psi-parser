import embeddable.compiler.initCompiler
import embeddable.compiler.initResolveToDescriptorFunction
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.utils.addToStdlib.measureTimeMillisWithResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import pensjon.regler.*
import pensjon.regler.repo.repoSourceInfo
import result.addons.flatMap
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.test.assertEquals

@Tag("integration")
class ParserIntegrationTest {

    private val logger = LoggerFactory.getLogger(ParserIntegrationTest::class.java)

    private lateinit var disposable: Disposable

    @BeforeEach
    fun setUpEach() {
        disposable = Disposer.newDisposable()
    }

    @AfterEach
    fun tearDownEach() {
        disposable.dispose()
    }

    @Test
    fun `test parsing for FastsettTrygdetid`() {

        val pensjonRegler = "/Users/torsteinnesby/gitHub/navikt/pensjon-regler"
        val output = "/Users/torsteinnesby/tmp/AsciiDocs"

        val args = arrayOf(
            "--repo=${pensjonRegler}",
            "--output=${output}",
            "--relaxed=true",
            "--log=ALL"
        )

        val initCustomSourceRoots: (Path) -> ((Path) -> Boolean) = { localRoot ->
            { path ->
                path.isDirectory(LinkOption.NOFOLLOW_LINKS) &&
                        (path.startsWith(localRoot / "repository" / "nav-repository-pensjon" / "src" / "main" / "kotlin" / "no" / "nav" / "domain" / "pensjon" / "regler" / "repository" / "tjeneste" / "fastsetttrygdetid")
                                || path.startsWith(localRoot / "system")
                                ) &&
                        path.name == "kotlin" &&
                        path.parent?.name == "main" &&
                        path.parent?.parent?.name == "src"
            }
        }

        validateConfig(args).flatMap { config ->
            initCompiler(disposable = disposable).flatMap { compilerFunctions ->
                repoSourceInfo(config.repoPath, initCustomSourceRoots).flatMap { sourceInfo ->

                    val psiFiles = sourceInfo.files.map { sourceFile ->
                        compilerFunctions.kotlinToPSI(sourceFile.path.absolutePathString(), sourceFile.content)
                    }

                    logger.info("${psiFiles.size} kotlin files mapped to PSI format")
                    logger.info("Building binding context for PSI files")

                    val (elapsed, bindingContextResult) = measureTimeMillisWithResult {
                        compilerFunctions.buildBindingContext(psiFiles)
                    }

                    bindingContextResult.flatMap { bindingContext ->
                        logger.info("binding context done")
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

                val services = result.filterIsInstance<RuleServiceInfo>()
                val flows = result.filterIsInstance<RuleFlowInfo>()
                val sets = result.filterIsInstance<RuleSetInfo>()

                assertEquals(1, services.size)

            }


    }


}