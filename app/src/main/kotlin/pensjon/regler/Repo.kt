package pensjon.regler

import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.*

private const val ORG_NAVIKT = "https://github.com/navikt"
private const val BRANCH = "blob/master"

object Repo {
    private lateinit var localRoot: Path
    private lateinit var psiFactory: PsiFileFactoryImpl
    lateinit var gitHubUri: URI
        private set

    private var isSourceRoot: (Path) -> Boolean = createDefaultFilter()

    fun initialize(path: Path, psiFactory: PsiFileFactoryImpl) {
        this.localRoot = path
        this.psiFactory = psiFactory
        this.gitHubUri = URI("$ORG_NAVIKT/${path.last()}/$BRANCH")
    }

    private fun createDefaultFilter(): (Path) -> Boolean = { path ->
        path.isDirectory() &&
                (path.startsWith(this.localRoot / "repository") ||
                        path.startsWith(this.localRoot / "system")) &&
                path.name == "kotlin" &&
                path.parent?.name == "main" &&
                path.parent?.parent?.name == "src"
    }

    private val sourceRoots: List<Path> by lazy {
        findSourceRoots().also {
            println("Found ${it.size} source roots")
            println(it.joinToString("\n"))
        }
    }

    private val psiSourceRootFiles: List<KtFile> by lazy {
        sourceFilesToPSI(sourceRoots).also {
            println("Finished mapping ${it.size} kt files to PSI format")
        }
    }

//    fun defineSourceRoots(filter: (Path) -> Boolean): Repo {
//        this.isSourceRoot = filter
//        return this
//    }

    fun psiFiles(): List<KtFile> = psiSourceRootFiles

    private fun findSourceRoots(): List<Path> =
        this.localRoot.walk(PathWalkOption.INCLUDE_DIRECTORIES)
            .filter { isSourceRoot(it) }
            .toList()

    private fun sourceFilesToPSI(sourceRoots: List<Path>): List<KtFile> =
        this.sourceRoots.flatMap { sourceRoot ->
            sourceRoot
                .walk()
                .filter { it.isRegularFile() && it.extension.lowercase() == "kt" }
                .map { file ->
                    psiFactory.createFileFromText(
                        file.absolutePathString(),
                        KotlinFileType.INSTANCE,
                        file.readText().replace("\r\n", "\n")
                    ) as KtFile
                }
        }

    fun toGithubURI(localFilePath: Path): URI = URI("$gitHubUri/${localFilePath.relativeTo(localRoot)}")
}