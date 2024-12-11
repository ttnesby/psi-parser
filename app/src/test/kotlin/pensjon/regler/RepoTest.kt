package pensjon.regler

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.writeText

class RepoTest {

    private lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("repo_test")
    }

    @AfterEach
    fun tearDown() {
        //tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should find source root paths based on default filter`() {
        (tempDir / "repository" / "src" / "main" / "kotlin").also {it.createDirectories()}
        (tempDir / "other" / "src" / "main" / "java").also {it.createDirectories()}

        assertEquals(1, Repo(tempDir).sourceRoots.size)
    }

    @Test
    fun `should generate correct GitHub URI`() {
        val localRoot = tempDir / "testRepo"
        val file = (localRoot / "repository" / "src" / "main" / "kotlin" / "Example.kt").also {
            it.createDirectories()
            it.resolve("Example.kt").writeText("package example")
        }

        val repo = Repo(localRoot)
        val uri = repo.toGithubURI(file.absolutePathString())

        assertEquals(
            URI("https://github.com/navikt/testRepo/blob/master/repository/src/main/kotlin/Example.kt"),
            uri
        )
    }
}
