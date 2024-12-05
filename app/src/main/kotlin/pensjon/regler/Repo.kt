package pensjon.regler

import embeddable.compiler.CompilerContext
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.Path
import kotlin.io.path.*

class Repo private constructor(
    val root: Path,
) {
    companion object {
        fun fromPath(root: Path): Repo {
            return Repo(root)
        }
    }

    private fun sourceRoots(): List<Path> =
        root.walk(PathWalkOption.INCLUDE_DIRECTORIES)
            .filter { path ->

                path.isDirectory()
                        && (path.startsWith(root / "repository") || path.startsWith(root / "system"))
                        && path.name == "kotlin"
                        && path.parent?.name == "main"
                        && path.parent?.parent?.name == "src"
            }
            .toList()
            .also {
                println("Found ${it.size} source roots")
                println(it.joinToString("\n"))
            }

    private fun sourceFilesToPSI(sourceRoots: List<Path>, context: CompilerContext): List<KtFile> =
        sourceRoots.flatMap { sourceRoot ->
            sourceRoot
                .walk()
                .filter { it.isRegularFile() && it.extension.lowercase() == "kt" }
                .map { file ->
                    context.psiFactory.createFileFromText(
                        file.absolutePathString(),
                        KotlinFileType.INSTANCE,
                        file.readText().replace("\r\n", "\n")
                    ) as KtFile
                }

        }
            .toList()
            .also {
                println("Finished mapping ${it.size} kt files to PSI format")
            }
}