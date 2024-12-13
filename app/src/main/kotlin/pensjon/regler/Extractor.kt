package pensjon.regler

import embeddable.compiler.CompilerContext
import org.example.*
import org.example.DSLTypeAbstract.*
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.resolve.BindingContext
import pensjon.regler.PropertyInfo.Companion.fromParameter
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

                    val (dslType, ktClass) = file.findDSLTypeAbstract()
                        .getOrElse {
                            // ikke relevant fil, rydd opp i cache og fortsett
                            (file as PsiFileImpl).clearCaches()
                            return@mapNotNull null
                        }
                    when (dslType) {

                        RULE_SERVICE -> {
                            ktClass.extractRuleService().getOrThrow().let { ModelResult.newService(it) }
                        }

                        RULE_FLOW -> {
                            ktClass.extractRuleFlow().getOrThrow().let { ModelResult.newFlow(it) }
                        }

                        RULE_SET -> {
                            ktClass.extractRuleSet().getOrThrow().let { ModelResult.newSet(it) }
                        }
                    }.also {
                        // rydd opp i cache
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

    private fun KtClass.extractRuleService(): Result<RuleServiceInfo> = runCatching {
        RuleServiceInfo(
            navn = name!!,
            beskrivelse = getKDocOrEmpty(),
            inndata = extractServiceRequestFields().getOrThrow(),
            utdata = extractServiceResponseFields(this).getOrThrow(),
            flyt = extractRuleServiceFlow(this).getOrThrow(),
            gitHubUri = repo.toGithubURI(containingKtFile.name)
        )
    }


//    private fun KtClass.extractServiceRequestFields(): Result<List<PropertyInfo>> = runCatching {
//        getServiceRequestInfo(bindingContext)
//            .map { (parameter, serviceRequestClass) ->
//                buildList {
//                    add(fromParameter(parameter, this@extractServiceRequestFields))
//                    addAll(
//                        serviceRequestClass.primaryConstructor?.let {
//                            PropertyInfo.fromPrimaryConstructor(it)
//                        } ?: throw IllegalStateException("No primary constructor found for ${serviceRequestClass.name}")
//                    )
//                }
//            }.getOrThrow()
//    }

    private fun KtClass.extractServiceRequestFields(): Result<List<PropertyInfo>> = runCatching {
        primaryConstructor
            ?.valueParameters
            ?.findDSLTypeServiceRequest()
            ?.map { (parameter, serviceRequestClass) ->
                buildList {
                    add(fromParameter(parameter, this@extractServiceRequestFields))
                    addAll(
                        serviceRequestClass
                            .primaryConstructor
                            ?.let { PropertyInfo.fromPrimaryConstructor(it) }
                            ?: throw IllegalArgumentException(
                                "No primary constructor found for ${serviceRequestClass.containingKtFile.name}"
                            )
                    )
                }
            }?.getOrThrow()
            ?: throw NoSuchElementException(
                "No service request field found in primary constructor [${containingKtFile.name}]"
            )
    }

    private fun List<KtParameter>.findDSLTypeServiceRequest(): Result<Pair<KtParameter, KtClass>> = runCatching {
        firstNotNullOf { parameter ->
            parameter
                .typeReference
                ?.resolveToKtClass(bindingContext)
                ?.getOrNull()
                ?.let { resolvedClass ->
                    if (resolvedClass.isSubClassOf(DSLTypeService.REQUEST)) {
                        parameter to resolvedClass
                    } else {
                        null
                    }
                }
                ?: throw NoSuchElementException(
                    "No type reference found for ${parameter.name} in primary constructor"
                )
        }
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

    private fun KtClass.extractRuleFlow(): Result<RuleFlowInfo> = runCatching {
        RuleFlowInfo.new(
            navn = name!!,
            beskrivelse = getKDocOrEmpty(),
            inndata = extractFlowRequestFields(this).getOrThrow(),
            flyt = getRuleFlowFlow(bindingContext).getOrThrow(),
            gitHubUri = repo.toGithubURI(containingKtFile.name)
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

    private fun KtClass.extractRuleSet(): Result<RuleSetInfo> = runCatching {
        RuleSetInfo.new(
            navn = name!!,
            beskrivelse = getKDocOrEmpty(),
            inndata = emptyList(),
            flyt = FlowElement.Flow(emptyList()),
            gitHubUri = repo.toGithubURI(containingKtFile.name)
        )
    }
}
