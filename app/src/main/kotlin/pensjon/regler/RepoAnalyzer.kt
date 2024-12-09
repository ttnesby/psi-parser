package pensjon.regler

import embeddable.compiler.CompilerContext
import org.example.*
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import kotlin.io.path.absolutePathString

class RepoAnalyzer private constructor(
    val context: CompilerContext,
    val psiFiles: List<KtFile>,
    val bindingContext: BindingContext
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

    data class Facts(
        val services: List<RuleServiceDoc>,
        val flows: List<RuleFlowDoc>,
        val sets: List<RuleSetDoc>
    )

    // TODO
    // - Add support for RuleFlow and RuleSet, included into analyzeSourceFiles
    fun analyze(): Result<Facts> = runCatching {
        psiFiles
            .chunked(100)
            .fold(Facts(emptyList(), emptyList(), emptyList())) { acc, batch ->
                val batchResults = batch.mapNotNull { file ->

                    when (file.getDSLType()){
                        DSLType.ABSTRACT_RULE_SERVICE -> {
                            getRuleService(file, bindingContext).map { service ->
                                Facts(listOf(service), emptyList(), emptyList())
                            }.getOrThrow()
                        }

                        DSLType.ABSTRACT_RULE_FLOW -> {
                            getRuleFlow(file, bindingContext).map { flow ->
                                Facts(emptyList(), listOf(flow), emptyList())
                            }.getOrThrow()
                        }

                        DSLType.ABSTRACT_RULE_SET -> {
                            getRuleSet(file, bindingContext).map { set ->
                                Facts(emptyList(), emptyList(), listOf(set))
                            }.getOrThrow()
                        }

                        else -> null
                    }.also {
                        (file as PsiFileImpl).clearCaches()
                    }
                }
                Facts(
                    services = acc.services + batchResults.flatMap { it.services },
                    flows = acc.flows + batchResults.flatMap { it.flows },
                    sets = acc.sets + batchResults.flatMap { it.sets }
                )
            }
    }
}
