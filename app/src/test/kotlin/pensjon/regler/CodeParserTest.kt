package pensjon.regler

import embeddable.compiler.BindingContextResolver
import embeddable.compiler.CompilerContext
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.isDirectory

class CodeParserTest {

    private val logger = LoggerFactory.getLogger(CodeParserTest::class.java)

    companion object {
        private lateinit var repoRoot: Path

        private fun getGitRepoRoot(): Result<Path> = runCatching {
            val process = ProcessBuilder("git", "rev-parse", "--show-toplevel")
                .redirectErrorStream(true)
                .start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = Path(reader.readLine())
            process.waitFor()
            if (process.exitValue() == 0) output else throw IllegalStateException("Failed to get git root")
        }

        @BeforeAll
        @JvmStatic
        fun setOnce() {
            repoRoot = getGitRepoRoot().getOrThrow()
        }
    }

    private lateinit var disposable: Disposable

    @BeforeEach
    fun setUpEach() {
        disposable = Disposer.newDisposable()
    }

    @AfterEach
    fun tearDownEach() {
        BindingContextResolver.reset()
        disposable.dispose()
    }

//    @Test
//    fun `test new CodeParser for non-existing Path`() {
//
//        val localRoot = repoRoot / "app" / "src" / "test" / "resources" / "DONOTEXIST"
//        val repo = Repo(localRoot)
//        val compilerContext = CompilerContext.new(disposable = disposable).getOrThrow()
//        val psiFiles = repo.files()
//            .map { fileInfo ->
//                compilerContext.createKtFile(fileInfo.file.absolutePathString(), fileInfo.content)
//            }
//        val bindingContext = compilerContext.buildBindingContext(psiFiles).getOrThrow()
//        // singleton for binding resolution
//        BindingContextResolver.initialize(bindingContext)
//
//        val codeParser = CodeParser.new(
//            repo = repo,
//            psiFiles = psiFiles,
//        )
//        assertEquals(0, repo.sourceRoots.size)
//
//        codeParser.toModel().map { result ->
//            assertEquals(0, result.filterIsInstance<RuleServiceInfo>().size)
//            assertEquals(0, result.filterIsInstance<RuleFlowInfo>().size)
//            assertEquals(0, result.filterIsInstance<RuleSetInfo>().size)
//        }.onFailure { assert(false) }
//
//    }
//
//    @Test
//    fun `test new CodeParser for FastsettTrygdetid`() {
//
//        val localRoot = repoRoot / "app" / "src" / "test" / "resources" / "FastsettTrygdetid"
//        val repo = Repo(localRoot).defineSourceRoots { path ->
//            path.isDirectory() && (
//                    path.startsWith(localRoot / "fastsetttrygdetid" / "flyter")
//                            || path.startsWith(localRoot / "fastsetttrygdetid" / "function")
//                            || path.startsWith(localRoot / "fastsetttrygdetid" / "regler")
//                            || path.startsWith(localRoot / "to")
//                            || path.startsWith(localRoot / "trygdetid" / "flyter")
//                            || path.startsWith(localRoot / "trygdetid" / "function")
//                            || path.startsWith(localRoot / "trygdetid" / "klasser")
//                            || path.startsWith(localRoot / "trygdetid" / "koder")
//                            || path.startsWith(localRoot / "trygdetid" / "regler")
//                    )
//        }
//
//        val compilerContext = CompilerContext.new(disposable = disposable).getOrThrow()
//        val psiFiles = repo.files()
//            .map { fileInfo ->
//                compilerContext.createKtFile(fileInfo.file.absolutePathString(), fileInfo.content)
//            }
//        val bindingContext = compilerContext.buildBindingContext(psiFiles).getOrThrow()
//
//        // singleton for binding resolution
//        BindingContextResolver.initialize(bindingContext)
//
//        val codeParser = CodeParser.new(
//            repo = repo,
//            psiFiles = psiFiles,
//        )
//
//        assertEquals(9, repo.sourceRoots.size)
//
////        codeParser.toModel().map { result ->
////            val services = result.filterIsInstance<RuleServiceInfo>()
////            val flows = result.filterIsInstance<RuleFlowInfo>()
////            val sets = result.filterIsInstance<RuleSetInfo>()
////
////            assertEquals(1, services.size)
////            assertEquals(10, flows.size)
////            assertEquals(31, sets.size)
////
////            `verify rule service FastsettTrygdetidService`(
////                services.find { it.navn == "FastsettTrygdetidService" }!!,
////                localRoot)
////
////            `verify rule flow StartTrygdetidFlyt`(flows.find { it.navn == "StartTrygdetidFlyt" }!!)
////
////            `verify rule flow FastsettTrygdetidFlyt`(flows.find { it.navn == "FastsettTrygdetidFlyt" }!!)
////
////        }.onFailure {
////            println("${it.message} \n ${it.stackTraceToString()}")
////            assert(false)
////        }
//    }
//
//    private fun `verify rule service FastsettTrygdetidService`(ruleService: RuleServiceInfo, localRoot: Path) {
//
//        assertEquals("FastsettTrygdetidService", ruleService.navn)
//        assertEquals("", ruleService.beskrivelse)
//
//        assertEquals(11, ruleService.inndata.size)
//        assertEquals(
//            PropertyInfo(
//                navn = "beregningsvilkarPeriodeListe",
//                beskrivelse = "Liste av beregningsvilkarPerioder, p�krevd ved uf�retrygd.",
//                type = "MutableList<BeregningsvilkarPeriode>"
//
//            ), ruleService.inndata.last()
//        )
//
//        assertEquals(5, ruleService.utdata.size)
//        assertEquals(
//            PropertyInfo(
//                navn = "pakkseddel",
//                beskrivelse = "",
//                type = "Pakkseddel"
//
//            ), ruleService.utdata.last()
//        )
//
//        assertEquals(4, ruleService.flyt.elementer.size)
//
//        val expectedUri = String.format(
//            "https://github.com/navikt/%s/blob/master/fastsetttrygdetid/function/FastsettTrygdetidService.kt",
//                    localRoot.last()
//        )
//
//        assertEquals(URI(expectedUri), ruleService.gitHubUri)
//    }
//
//    private fun `verify rule flow StartTrygdetidFlyt`(ruleFlow: RuleFlowInfo) {
//
//        assertEquals("StartTrygdetidFlyt", ruleFlow.navn)
//        assertEquals("", ruleFlow.beskrivelse)
//        assertEquals(4, ruleFlow.inndata.size)
//
//        assertEquals(
//            PropertyInfo(
//                navn = "variable",
//                type = "TrygdetidVariable?",
//                beskrivelse = ""
//            ), ruleFlow.inndata.last()
//        )
//
//        assertEquals(2, ruleFlow.flyt.elementer.size)
//    }
//
//    private fun `verify rule flow FastsettTrygdetidFlyt`(ruleFlow: RuleFlowInfo) {
//
//        assertEquals("FastsettTrygdetidFlyt", ruleFlow.navn)
//
//        val forgrening = ruleFlow
//            .flyt.elementer
//            .filterIsInstance<FlowElement.Forgrening>().first()
//
//        assertEquals("Uføretrygd?", forgrening.navn)
//        assertEquals("Task: Uføretrygd?", forgrening.beskrivelse)
//        assertEquals(2, forgrening.gren.size)
//
//        assertEquals("Ja", forgrening.gren.first().betingelse.navn)
//        assertEquals("Nei", forgrening.gren.last().betingelse.navn)
//    }
}