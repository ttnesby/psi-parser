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
    fun `test new Extractor for FastsettTrygdetid`() {

        val localRoot = repoRoot / "app" / "src" / "test" / "resources"
        val repo = Repo(localRoot).defineSourceRoots { path ->
            path.isDirectory() && path.startsWith(localRoot / "FastsettTrygdetid")
        }

        val extractor = Extractor.new(
            repo = repo,
            context = CompilerContext.new(disposable = disposable).getOrThrow()
        )
        assertTrue(extractor.isSuccess)
        assertEquals(5, repo.sourceRoots.size)

        extractor.map { it.toModel().map { result ->
            assertEquals(1, result.services.size)
            assertEquals(2, result.flows.size)
            assertEquals(0, result.sets.size)

            val ruleService = result.services.first()
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
        } }
    }


}