package pensjon.regler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

class RepoTest {

    @Test
    fun `should find source root paths based on filter`() {
        val tempDir = Files.createTempDirectory("repo_test")
        val sourcePath = tempDir / "repository" / "src" / "main" / "kotlin"
        sourcePath.createDirectories()

        val nonMatchingPath = tempDir / "other" / "src" / "main" / "java"
        nonMatchingPath.createDirectories()

        val repo = Repo(tempDir)
        val sourceRoots = repo.files()

        assertEquals(1, sourceRoots.size)
        assertTrue(sourceRoots.contains(sourcePath))
    }

    @Test
    fun `should generate correct GitHub URI`() {
        val tempDir = Files.createTempDirectory("repo_test")
        val localRoot = tempDir / "testRepo"
        val file = localRoot / "repository" / "src" / "main" / "kotlin" / "Example.kt"
        file.createDirectories()
        file.resolve("Example.kt").writeText("package example")

        val repo = Repo(localRoot)
        val uri = repo.toGitHubUri(file)

        assertEquals(
            URI("https://github.com/testRepo/repository/src/main/kotlin/Example.kt"),
            uri
        )
    }
}
