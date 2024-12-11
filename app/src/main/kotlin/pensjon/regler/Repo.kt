package pensjon.regler

import java.net.URI
import java.nio.file.Path
import kotlin.io.path.*

private const val ORG_NAVIKT = "https://github.com/navikt"
private const val BRANCH = "blob/master"

class Repo(private val localRoot: Path) {
    private val gitHubUri: URI = URI("$ORG_NAVIKT/${localRoot.last()}/$BRANCH")

    private var isSourceRoot: (Path) -> Boolean = createDefaultFilter()

    private fun createDefaultFilter(): (Path) -> Boolean = { path ->
        path.isDirectory() &&
                (path.startsWith(localRoot / "repository") ||
                        path.startsWith(localRoot / "system")) &&
                path.name == "kotlin" &&
                path.parent?.name == "main" &&
                path.parent?.parent?.name == "src"
    }

//    fun defineSourceRoots(filter: (Path) -> Boolean): Repo {
//        this.isSourceRoot = filter
//        return this
//    }

    val sourceRoots: List<Path> by lazy {
        findSourceRoots().also {
            println("Found ${it.size} source roots")
            println(it.joinToString("\n"))
        }
    }

    private val sourceRootFiles: List<FileInfo> by lazy {
        sourceFiles().also {
            println("Finished mapping ${it.size} kt files to PSI format")
        }
    }

    data class FileInfo(
        val file: Path,
        val content: String
    )

    fun files(): List<FileInfo> = sourceRootFiles

    private fun findSourceRoots(): List<Path> =
        localRoot.walk(PathWalkOption.INCLUDE_DIRECTORIES)
            .filter { isSourceRoot(it) }
            .toList()

    private fun sourceFiles(): List<FileInfo> =
        sourceRoots.flatMap { sourceRoot ->
            sourceRoot
                .walk()
                .filter { it.isRegularFile() && it.extension.lowercase() == "kt" }
                .map { file ->
                    FileInfo(
                        file = file,
                        content = file.readText().replace("\r\n", "\n")
                    )
                }
        }

    fun toGithubURI(localFilePath: String): URI = URI("$gitHubUri/${Path(localFilePath).relativeTo(localRoot)}")
}