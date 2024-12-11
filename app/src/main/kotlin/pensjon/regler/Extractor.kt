package pensjon.regler

import embeddable.compiler.CompilerContext
import org.example.*
import pensjon.regler.PropertyInfo.Companion.fromParameter
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import java.net.URI
import kotlin.io.path.absolutePathString

class Extractor private constructor(
    private val repo: Repo,
    //private val context: CompilerContext,
    private val psiFiles: List<KtFile>,
    private val bindingContext: BindingContext
) {
    companion object {
        fun new(repo: Repo, context: CompilerContext): Result<Extractor> = runCatching {

            val psiFiles = repo.files().map { fileInfo ->
                context.createKtFile(fileInfo.file.absolutePathString(), fileInfo.content)
            }
            val bindingContext = context.buildBindingContext(psiFiles).getOrThrow()

            Extractor(repo, psiFiles, bindingContext)
        }
    }

    fun toModel(): Result<ModelResult> = runCatching {
        psiFiles
            .chunked(100)
            .fold(ModelResult.empty()) { acc, batch ->
                val batchResults = batch.mapNotNull { file ->

                    when (file.getDSLType()) {
                        DSLType.ABSTRACT_RULE_SERVICE -> {
                            extractRuleService(file)
                                .map { ModelResult.newService(it) }
                                .getOrThrow()
                        }

                        DSLType.ABSTRACT_RULE_FLOW -> {
                            extractRuleFlow(file)
                                .map { ModelResult.newFlow(it) }
                                .getOrThrow()
                        }

                        DSLType.ABSTRACT_RULE_SET -> {
                            extractRuleSet(file)
                                .map { ModelResult.newSet(it) }
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

    private fun extractRuleService(ktFile: KtFile): Result<RuleServiceInfo> =
        ktFile.getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass).map { ktClass ->
            RuleServiceInfo(
                navn = ktClass.name!!,
                beskrivelse = ktClass.getKDocOrEmpty(),
                inndata = extractServiceRequestFields(ktClass).getOrThrow(),
                utdata = extractServiceResponseFields(ktClass).getOrThrow(),
                flyt = extractRuleServiceFlow(ktClass).getOrThrow(),
                // TODO - Legge om til å hente URI fra Repo, krever en del endringer i test oppsettet
                gitHubUri = repo.toGithubURI(ktFile.name) //URI(ktFile.name.convertToGitHubUrl())
            )
        }

    private fun extractServiceRequestFields(ktClass: KtClass): Result<List<PropertyInfo>> = runCatching {
        ktClass.getServiceRequestInfo(bindingContext)
            .map { (parameter, serviceRequestClass) ->
                buildList {
                    add(fromParameter(parameter, ktClass))
                    addAll(
                        serviceRequestClass.primaryConstructor?.let {
                            PropertyInfo.fromPrimaryConstructor(it)
                        } ?: throw IllegalStateException("No primary constructor found for ${serviceRequestClass.name}")
                    )
                }
            }.getOrThrow()
    }

    private fun extractServiceResponseFields(ktClass: KtClass): Result<List<PropertyInfo>> = runCatching {
        ktClass.getServiceResponseClass(bindingContext)
            .map { serviceResponseClass ->
                buildList {
                    add(
                        PropertyInfo.new(
                            serviceResponseClass.name!!,
                            serviceResponseClass.name!!,
                            "Response for ${ktClass.name}"
                        )
                    )
                    addAll(
                        serviceResponseClass.primaryConstructor?.let {
                            PropertyInfo.fromPrimaryConstructor(it)
                        }
                            ?: throw IllegalArgumentException("No primary constructor found for ${serviceResponseClass.name}")
                    )
                }
            }.getOrThrow()
    }

    private fun extractRuleServiceFlow(ktClass: KtClass): Result<FlowElement.Flow> = runCatching {
        ktClass.getRuleServiceFlow(bindingContext)
            .map { sequence -> FlowElement.Flow(sequence.toList()) }
            .getOrThrow()
    }

    private fun extractRuleFlow(ktFile: KtFile): Result<RuleFlowInfo> =
        ktFile.getSubClassOfSuperClass(KtClass::isSubClassOfRuleFlowClass).map { ktClass ->
            RuleFlowInfo.new(
                navn = ktClass.name!!,
                beskrivelse = ktClass.getKDocOrEmpty(),
                inndata = extractFlowRequestFields(ktClass).getOrThrow(),
                flyt = ktClass.getRuleFlowFlow(bindingContext).getOrThrow(),
                gitHubUri = URI(ktFile.name.convertToGitHubUrl())
            )
        }

    private fun extractFlowRequestFields(ktClass: KtClass): Result<List<PropertyInfo>> = runCatching {
        ktClass.primaryConstructor?.valueParameters?.firstNotNullOfOrNull { parameter ->
            val resolvedClass = parameter.typeReference?.resolveToKtClass(bindingContext)?.getOrThrow()
                ?: throw NoSuchElementException("No type reference found")

            buildList {
                add(fromParameter(parameter, ktClass))
                addAll(
                    resolvedClass.getProperties().map {
                        PropertyInfo.new(
                            it.name!!,
                            it.typeReference?.text ?: "Unknown",
                            it.children.filterIsInstance<KDoc>().firstOrNull()?.formatOrEmpty() ?: ""
                        )
                    }
                )
            }
        } ?: throw NoSuchElementException(
            "No flow request fields found ${ktClass.name}"
        )
    }

    private fun extractRuleSet(ktFile: KtFile): Result<RuleSetInfo> =
        ktFile.getSubClassOfSuperClass(KtClass::isSubClassOfRuleSetClass).map { ktClass ->
            RuleSetInfo.new(
                navn = ktClass.name!!,
                beskrivelse = ktClass.getKDocOrEmpty(),
                inndata = emptyList(),
                flyt = FlowElement.Flow(emptyList()),
                gitHubUri = URI(ktFile.name.convertToGitHubUrl())
            )
        }
}
