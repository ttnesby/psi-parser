package pensjon.regler

import embeddable.compiler.CompilerContext
import org.example.*
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import kotlin.io.path.absolutePathString

class RepoAnalyzer private constructor(
    private val context: CompilerContext,
    private val psiFiles: List<KtFile>,
    private val bindingContext: BindingContext
) {
    companion object {
        fun new(context: CompilerContext): Result<RepoAnalyzer> = runCatching {

            val psiFiles = Repo.files().map { fileInfo ->
               context.createKtFile(fileInfo.file.absolutePathString(), fileInfo.content)
            }
            val bindingContext = context.buildBindingContext(psiFiles).getOrThrow()

            RepoAnalyzer(context, psiFiles, bindingContext)
        }
    }

    fun analyze(): Result<RulesOverview> = runCatching {
        psiFiles
            .chunked(100)
            .fold(RulesOverview.empty()) { acc, batch ->
                val batchResults = batch.mapNotNull { file ->

                    when (file.getDSLType()){
                        DSLType.ABSTRACT_RULE_SERVICE -> {
                            getRuleService(file, bindingContext)
                                .map {  RulesOverview.newService(it) }
                                .getOrThrow()
                        }

                        DSLType.ABSTRACT_RULE_FLOW -> {
                            getRuleFlow(file, bindingContext)
                                .map { RulesOverview.newFlow(it) }
                                .getOrThrow()
                        }

                        DSLType.ABSTRACT_RULE_SET -> {
                            getRuleSet(file, bindingContext)
                                .map { RulesOverview.newSet(it) }
                                .getOrThrow()
                        }

                        else -> null
                    }.also {
                        (file as PsiFileImpl).clearCaches()
                    }
                }
                acc.addBatch(
                    services = batchResults.flatMap { it.services },
                    flows = batchResults.flatMap { it.flows },
                    sets = batchResults.flatMap { it.sets }
                )
            }
    }
}
