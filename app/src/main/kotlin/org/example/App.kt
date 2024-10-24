import java.io.File
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.com.intellij.openapi.Disposable
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiManager
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

fun main() {
    // Set up the compiler environment
    val disposable: Disposable = Disposer.newDisposable()

    try {
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "my-module")
            put(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        }

        val environment = KotlinCoreEnvironment.createForProduction(
            disposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES
        )

        // Create PSI manager
        val psiManager = PsiManager.getInstance(environment.project)

        // Directory containing Kotlin source files
        val sourceDir = File("src/main/kotlin")
        require(sourceDir.exists() && sourceDir.isDirectory) {
            "Source directory not found: ${sourceDir.absolutePath}"
        }

        // Collect Kotlin file paths
        val ktFilePaths = sourceDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.absolutePath }
            .toList()

        // Process each Kotlin file
        // Parse and process each Kotlin file
        ktFilePaths.forEach { filePath ->
            val ktFile = parseKotlinFile(filePath, psiManager)
            ktFile?.accept(object : KtVisitorVoid() {
                override fun visitNamedFunction(function: KtNamedFunction) {
                    println("Function: ${function.name}")
                    // Build your graph model here
                }

                override fun visitClass(klass: KtClass) {
                    println("Class: ${klass.name}")
                    // Build your graph model here
                }

                // Implement other visit methods as needed
            })
        }

    }
    finally {
        Disposer.dispose(disposable)
    }
}
// Function to parse a Kotlin file
fun parseKotlinFile(file: File, environment: KotlinCoreEnvironment): KtFile? {
    val psiManager = environment.project.getComponent(PsiManager::class.java)
    val virtualFile = kotlin.io.virtual.VfsUtil.findFileByIoFile(file)
    return virtualFile?.let { psiManager.findFile(it) as? KtFile }
}