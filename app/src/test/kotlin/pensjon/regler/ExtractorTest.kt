package pensjon.regler

import embeddable.compiler.CompilerContext
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory

class ExtractorTest {

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
        disposable.dispose()
    }

    @Test
    fun `test new Extractor for non-existing Path`() {

        val localRoot = repoRoot / "app" / "src" / "test" / "resources" / "DONOTEXIST"
        val repo = Repo(localRoot)

        val extractor = Extractor.new(
            repo = repo,
            context = CompilerContext.new(disposable = disposable).getOrThrow()
        )
        assertTrue(extractor.isSuccess)
        assertEquals(0, repo.sourceRoots.size)

        extractor.map {
            it.toModel()
                .map { result ->
                    assertEquals(0, result.filterIsInstance<RuleServiceInfo>().size)
                    assertEquals(0, result.filterIsInstance<RuleFlowInfo>().size)
                    assertEquals(0, result.filterIsInstance<RuleSetInfo>().size)
                }.onFailure { assert(false)  }
        }.onFailure { assert(false) }
    }

    @Test
    fun `test new Extractor for FastsettTrygdetid`() {

        val localRoot = repoRoot / "app" / "src" / "test" / "resources" / "FastsettTrygdetid"
        val repo = Repo(localRoot).defineSourceRoots { path ->
            path.isDirectory() && (
                    path.startsWith(localRoot / "fastsetttrygdetid" / "flyter")
                            || path.startsWith(localRoot / "fastsetttrygdetid" / "function")
                            || path.startsWith(localRoot / "fastsetttrygdetid" / "regler")
                            || path.startsWith(localRoot / "to")
                            || path.startsWith(localRoot / "trygdetid" / "flyter")
                            || path.startsWith(localRoot / "trygdetid" / "function")
                            || path.startsWith(localRoot / "trygdetid" / "klasser")
                            || path.startsWith(localRoot / "trygdetid" / "koder")
                            || path.startsWith(localRoot / "trygdetid" / "regler")
                    )
        }

        val extractor = Extractor.new(
            repo = repo,
            context = CompilerContext.new(disposable = disposable).getOrThrow()
        )
        assertTrue(extractor.isSuccess)
        assertEquals(9, repo.sourceRoots.size)

        extractor.map {
            it.toModel()
                .map { result ->
                    val services = result.filterIsInstance<RuleServiceInfo>()
                    val flows = result.filterIsInstance<RuleFlowInfo>()
                    val sets = result.filterIsInstance<RuleSetInfo>()

                    assertEquals(1, services.size)
                    assertEquals(10, flows.size)
                    assertEquals(31, sets.size)

                    // asserts for regel tjeneste

                    val ruleService = services.first()
                    assertEquals("FastsettTrygdetidService", ruleService.navn)
                    assertEquals("", ruleService.beskrivelse)

                    assertEquals(11, ruleService.inndata.size)
                    assertEquals(
                        PropertyInfo(
                            navn = "beregningsvilkarPeriodeListe",
                            beskrivelse = "Liste av beregningsvilkarPerioder, p�krevd ved uf�retrygd.",
                            type = "MutableList<BeregningsvilkarPeriode>"

                        ), ruleService.inndata.last())

                    assertEquals(5, ruleService.utdata.size)
                    assertEquals(
                        PropertyInfo(
                            navn = "pakkseddel",
                            beskrivelse = "",
                            type = "Pakkseddel"

                        ), ruleService.utdata.last())

                    assertEquals(2, ruleService.flyt.elementer.size)

                    assertEquals(
                        URI("https://github.com/navikt/${localRoot.last()}/blob/master/fastsetttrygdetid/function/FastsettTrygdetidService.kt"),
                        ruleService.gitHubUri)

                    // asserts for regel flyter

                    val ruleFlow = flows.first()
                    assertEquals("StartTrygdetidFlyt", ruleFlow.navn)
                    assertEquals("", ruleFlow.beskrivelse)
                    assertEquals(4, ruleFlow.inndata.size)

                    assertEquals(PropertyInfo(
                        navn = "variable",
                        type = "TrygdetidVariable?",
                        beskrivelse = ""
                    ), ruleFlow.inndata.last())

                    assertEquals(2, ruleFlow.flyt.elementer.size)


                }.onFailure { assert(false)  }
        }.onFailure { assert(false) }
    }


}