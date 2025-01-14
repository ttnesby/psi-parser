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

/**
 * Creates a filter function [PathToBoolean] that checks whether a given [Path] is considered a
 * "default source root" under the specified [localRoot].
 *
 * The returned function applies the following checks on the [path] parameter:
 * 1. The path is a directory (with [LinkOption.NOFOLLOW_LINKS]).
 * 2. It is located under either `localRoot/repository` or `localRoot/system`.
 * 3. Its name is `"kotlin"`.
 * 4. Its parent directory’s name is `"main"`.
 * 5. Its grandparent directory’s name is `"src"`.
 *
 * @receiver [localRoot] The root directory against which the checks are performed.
 * @return A function of type [PathToBoolean], which returns `true` if the [path] meets all the criteria,
 *         and `false` otherwise.
 */
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

/**
 * Creates a function [StringPathToUriResult] for generating a GitHub [URI] based on the given [localRoot].
 *
 * The returned function accepts a local file path ([localFilePath]) and produces a [Result] wrapping the
 * corresponding GitHub [URI]. Here’s the flow:
 *
 * 1. Builds a “root” GitHub URI using the [localRoot]'s last path segment appended to the global
 *    [ORG_NAVIKT] and [BRANCH] constants (e.g., `"https://github.com/navikt/<lastSegment>/blob/master/"`).
 * 2. Converts [localFilePath] to a [Path], normalizes it, and removes any trailing slash.
 * 3. Constructs a relative path ([relativePathUri]) between [localRoot] and the [localFilePath].
 * 4. Resolves [relativePathUri] against the “root” GitHub URI to produce the final [URI].
 *
 * If any step fails (e.g., malformed path), the error is captured in the [Result].
 *
 * @receiver The [localRoot] directory against which paths are resolved.
 * @return A function [StringPathToUriResult] that transforms a [localFilePath] into a [Result] of [URI].
 */
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
