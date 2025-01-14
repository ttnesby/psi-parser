package pensjon.regler.repo

import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.LinkOption
import java.nio.file.Path
import kotlin.io.path.*
import result.addons.flatMap

private const val ORG_NAVIKT = "https://github.com/navikt"
private const val BRANCH = "blob/master/"

private val logger = LoggerFactory.getLogger("repo")

data class FileInfo(
    val path: Path,
    val content: String
)

data class SourceInfo(
    val roots: List<Path>,
    val files: List<FileInfo>
)

typealias PathToBoolean = (Path) -> Boolean

val initDefaultSourceRootFilterFunction: (Path) -> PathToBoolean = { localRoot ->
    { path ->
        path.isDirectory(LinkOption.NOFOLLOW_LINKS) &&
                (path.startsWith(localRoot / "repository") || path.startsWith(localRoot / "system")) &&
                path.name == "kotlin" &&
                path.parent?.name == "main" &&
                path.parent?.parent?.name == "src"
    }
}

private fun findSourceRoots(localRoot: Path, isSourceRoot: PathToBoolean): Result<List<Path>> = runCatching {
    localRoot
        .walk(PathWalkOption.INCLUDE_DIRECTORIES)
        .filter { isSourceRoot(it) }
        .toList()
        .also {
            logger.info("Found ${it.size} source roots")
            logger.debug(it.joinToString(", "))
        }
}

private fun findSourceFiles(sourceRoots: List<Path>): Result<List<FileInfo>> = runCatching {
    sourceRoots.flatMap { sourceRoot ->
        sourceRoot
            .walk()
            .filter { it.isRegularFile() && it.extension.equals("kt", ignoreCase = true) }
            .distinctBy { it.absolutePathString() }
            .map { file ->
                FileInfo(
                    path = file,
                    content = file.readText().replace("\r\n", "\n") // unify line endings
                )
            }
    }
        .also {
            logger.info("Found ${it.size} kotlin files")
        }
}

fun repoSourceInfo(localRoot: Path, isSourceRoot: (Path) -> PathToBoolean): Result<SourceInfo> =
    findSourceRoots(localRoot, isSourceRoot(localRoot)).flatMap { sourceRoots ->
        findSourceFiles(sourceRoots).map { sourceFiles ->
            SourceInfo(
                roots = sourceRoots,
                files = sourceFiles
            )
        }
    }

/**
 * Converts a local file path into a GitHub URI, based on the localRoot.
 */

typealias StringPathToUriResult = (String) -> Result<URI>

val initToGitHubURIFunction: (Path) -> StringPathToUriResult = { localRoot ->
    { localFilePath ->
        // The “root” GitHub URI for this repo’s folder.
        val rootRepoUri = URI("$ORG_NAVIKT/${localRoot.last()}/$BRANCH")

        runCatching {
            // Convert to URIs, and remove trailing slash from the file path.
            val localRootUri = localRoot.toUri()
            val localFilePathUri = Path(localFilePath).normalize().toUri()
            val sanitizedFilePathUri = URI(localFilePathUri.toString().removeSuffix("/"))

            // Build a relative path from `localRoot` to the file.
            val relativePathUri = localRootUri.relativize(sanitizedFilePathUri)

            // Append the relative path to the root GitHub URI.
            rootRepoUri.resolve(relativePathUri)
        }
    }
}
