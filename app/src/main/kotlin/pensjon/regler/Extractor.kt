package pensjon.regler

import embeddable.compiler.*
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import rule.dsl.DSLTypeAbstract
import rule.dsl.DSLTypeAbstract.*
import rule.dsl.DSLTypeFlow
import rule.dsl.DSLTypeFlow.FLOW
import rule.dsl.DSLTypeFlow.SERVICE
import rule.dsl.DSLTypeService.RESPONSE
import kotlin.io.path.absolutePathString

class Extractor private constructor(
    private val repo: Repo,
    private val psiFiles: List<KtFile>,
    private val bindingContext: BindingContext
) {
    companion object {
        fun new(repo: Repo, psiFiles: List<KtFile>, bindingContext: BindingContext): Extractor =
            Extractor(repo, psiFiles, bindingContext)
    }

    fun toModel(): Result<List<RuleInfo>> =
        psiFiles.mapNotNull { file ->
            file.findDSLTypeAbstract()
                ?.let { (ktClass, dslTypeAbstract) ->
                    extractRuleInfo(ktClass, dslTypeAbstract)
                }
        }.also {
            psiFiles.forEach { (it as PsiFileImpl).clearCaches() }
        }.toResult()



    private fun extractRuleInfo(ktClass: KtClass, dslType: DSLTypeAbstract): Result<RuleInfo> = when (dslType) {
        RULE_SERVICE -> ktClass.extractRuleService()
        RULE_FLOW -> ktClass.extractRuleFlow()
        RULE_SET -> ktClass.extractRuleSet()
    }

    private fun KtClass.extractRuleService(): Result<RuleServiceInfo> = runCatching {
        RuleServiceInfo(
            navn = name!!,
            beskrivelse = docOrEmpty(),
            inndata = extractServiceRequestFields().getOrThrow(),
            utdata = extractServiceResponseFields().getOrThrow(),
            flyt = extractFlow(SERVICE).getOrThrow(),
            gitHubUri = repo.toGithubURI(containingKtFile.name).getOrThrow()
        )
    }

    private fun KtClass.extractServiceRequestFields(): Result<List<PropertyInfo>> =
        requirePrimaryConstructor()
            .flatMap { it.findParameterDSLTypeServiceRequest(bindingContext) }
            .flatMap { (parameter, serviceRequestClass) ->
                serviceRequestClass
                    .requirePrimaryConstructor()
                    .map { it.toPropertyInfo() }
                    .map { requestProperties ->
                        buildList {
                            add(parameter.toPropertyInfo())
                            addAll(requestProperties)
                        }
                    }
            }

    private fun KtClass.extractServiceResponseFields(): Result<List<PropertyInfo>> =
        findResponseTypeForRuleService()
            .flatMap { it.resolveToKtClass(bindingContext) }
            .flatMap { it.mustBeSubClassOf(RESPONSE) }
            .flatMap { serviceResponseClass ->
                serviceResponseClass
                    .requirePrimaryConstructor()
                    .map { it.toPropertyInfo() }
                    .map { responseProperties ->
                        buildList {
                            add(
                                PropertyInfo(
                                    navn = serviceResponseClass.name!!,
                                    type = serviceResponseClass.name!!,
                                    beskrivelse = "Response for $name"
                                )
                            )
                            addAll(responseProperties)
                        }
                    }
            }

    private fun KtClass.extractRuleFlow(): Result<RuleFlowInfo> = runCatching {
        RuleFlowInfo(
            navn = name!!,
            beskrivelse = docOrEmpty(),
            inndata = extractFlowRequestFields().getOrThrow(),
            flyt = extractFlow(FLOW).getOrThrow(),
            gitHubUri = repo.toGithubURI(containingKtFile.name).getOrThrow()
        )
    }

    private fun KtClass.extractFlowRequestFields(): Result<List<PropertyInfo>> =
        requirePrimaryConstructor()
            .flatMap { it.findFirstParameterOfTypeClass(bindingContext) }
            .map { (parameter, aClass) ->
                buildList {
                    add(parameter.toPropertyInfo())
                    addAll(aClass.getProperties().toPropertyInfo())
                }
            }

    private fun KtClass.extractFlow(flowType: DSLTypeFlow): Result<FlowElement.Flow> =
        findMatchingProperty(flowType)
            .flatMap { it.getLambdaBlock() }
            .flatMap { block ->
                when (flowType) {
                    SERVICE -> block.extractRuleServiceFlow(bindingContext)
                    FLOW -> block.extractRuleFlowFlow(bindingContext)
                }
            }

    private fun KtClass.extractRuleSet(): Result<RuleSetInfo> = runCatching {
        RuleSetInfo(
            navn = name!!,
            beskrivelse = docOrEmpty(),
            inndata = emptyList(),
            flyt = FlowElement.Flow(emptyList()),
            gitHubUri = repo.toGithubURI(containingKtFile.name).getOrThrow()
        )
    }
}
