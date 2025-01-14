package pensjon.regler.repo

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
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `should find source root paths and files based on default filter`() {
        val repoKotlinDir = (tempDir / "repository" / "src" / "main" / "kotlin").also { it.createDirectories() }
        val systemKotlinDir = (tempDir / "system" / "src" / "main" / "kotlin").also { it.createDirectories() }
        // not part of default source root filter
        (tempDir / "other" / "src" / "main" / "java").also { it.createDirectories() }

        val createFile: (Path, String) -> Unit = {path, prefix ->
            (path / "${prefix}Class.kt").writeText(
                """
        package ${prefix.lowercase()}
        
        class ${prefix}Class {
            fun hello() = "Hello from ${prefix}Class!"
        }
        """.trimIndent()
            )

        }

        createFile(repoKotlinDir, "Repo")
        createFile(repoKotlinDir, "Repo2")
        createFile(repoKotlinDir, "Repo3")
        createFile(systemKotlinDir, "System")
        createFile(systemKotlinDir, "System2")

        val sourceInfo = repoSourceInfo(tempDir, initDefaultSourceRootFilterFunction).getOrThrow()

        assertEquals(
            2,
            sourceInfo.roots.size
        )

        assertEquals(
            5,
            sourceInfo.files.size
        )
    }

    @Test
    fun `should generate correct GitHub URI`() {
        val localRoot = tempDir / "testRepo"
        val file = (localRoot / "repository" / "src" / "main" / "kotlin" / "Example.kt").also {
            it.createDirectories()
            it.resolve("Example.kt").writeText("package example")
        }

        assertEquals(
            URI("https://github.com/navikt/testRepo/blob/master/repository/src/main/kotlin/Example.kt"),
            repoSourceInfo(localRoot, initDefaultSourceRootFilterFunction)
                .getOrThrow()
                .toGitHubURI(file.absolutePathString())
                .getOrThrow()
        )
    }


    @Test
    fun `should find custom source root paths`() {
        (tempDir / "customRepo" / "src").also { it.createDirectories() }
        (tempDir / "customRepo" / "tests").also { it.createDirectories() }
        // not part of custom source roots
        (tempDir / "exclude" / "tests").also { it.createDirectories() }

        val initCustomSourceRoots: (Path) -> ((Path) -> Boolean) = { localRoot ->
            { path ->
                path.startsWith(localRoot / "customRepo" / "src") ||
                        path.startsWith(localRoot / "customRepo" / "tests")
            }
        }

        assertEquals(
            2,
            repoSourceInfo(tempDir, initCustomSourceRoots).getOrThrow().roots.size
        )
    }
}
