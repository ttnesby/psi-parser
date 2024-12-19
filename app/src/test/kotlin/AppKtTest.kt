import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class AppKtTest {

    /**
     * Tests the bootstrap function in AppKt to ensure it behaves correctly for various input scenarios.
     */

    private lateinit var tempDir: Path
    private lateinit var disposable: Disposable

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("directory_test")
        disposable = Disposer.newDisposable()
    }

    @AfterEach
    fun tearDown() {
        //tempDir.toFile().deleteRecursively()
        disposable.dispose()
    }

    @Test
    fun `test bootstrap with valid repository and output paths`() {
        val repoPath = (tempDir / "testRepo").also { it.createDirectories() }
        val outputPath = (tempDir / "testOutput").also { it.createDirectories() }

        val result = bootstrap(
            arrayOf(repoPath.toString(), outputPath.toString()),
            disposable
        )
        assertTrue(result.isSuccess)
    }

    @Test
    fun `test bootstrap with invalid number of arguments`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            bootstrap(arrayOf("only-one-argument"), disposable).getOrThrow()
        }
        assertEquals("Usage: <path to repository> <path to output folder>", exception.message)
    }

    @Test
    fun `test bootstrap with non-directory repository path`() {
        val repoPath = (tempDir / "notDirectory.txt").also { it.toFile().createNewFile() }
        val outputPath = (tempDir / "testOutput").also { it.createDirectories() }

        assertFailsWith<IllegalArgumentException> {
            bootstrap(
                arrayOf(repoPath.toString(), outputPath.toString()),
                disposable
            ).getOrThrow()
        }
    }

    @Test
    fun `test bootstrap with non-directory output path`() {
        val repoPath = (tempDir / "testRepo").also { it.createDirectories() }
        val outputPath = (tempDir / "notDirectory.txt").also { it.toFile().createNewFile() }

        assertFailsWith<IllegalArgumentException> {
            bootstrap(
                arrayOf(repoPath.toString(), outputPath.toString()),
                disposable
            ).getOrThrow()
        }
    }
}