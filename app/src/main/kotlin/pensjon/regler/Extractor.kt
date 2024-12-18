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
    //private val context: CompilerContext,
    private val psiFiles: List<KtFile>,
    private val bindingContext: BindingContext
) {
    companion object {
        fun new(repo: Repo, context: CompilerContext): Result<Extractor> = runCatching {

            val psiFiles = repo.files().map { fileInfo ->
                context.createKtFile(fileInfo.file.absolutePathString(), fileInfo.content)
            }
            println("Building binding context for ${psiFiles.size} files\n")
            val bindingContext = context.buildBindingContext(psiFiles).getOrThrow()

            Extractor(repo, psiFiles, bindingContext)
        }
    }

    fun toModel(): Result<List<RuleInfo>> = runCatching {

        psiFiles.mapNotNull { file ->
            file.findDSLTypeAbstract()
                .getOrThrow()
                ?.let { (ktClass, dslTypeAbstract) ->
                    extractRuleInfo(ktClass, dslTypeAbstract).getOrThrow()
                }
        }.also { psiFiles.forEach { (it as PsiFileImpl).clearCaches() } }
    }

    private fun extractRuleInfo(ktClass: KtClass, dslType: DSLTypeAbstract): Result<RuleInfo> = when (dslType) {
        RULE_SERVICE -> ktClass.extractRuleService()
        RULE_FLOW -> ktClass.extractRuleFlow()
        RULE_SET -> ktClass.extractRuleSet()
    }

    private fun KtClass.extractRuleService(): Result<RuleServiceInfo> = runCatching {
        RuleServiceInfo(
            navn = name!!,
            beskrivelse = getKDocOrEmpty(),
            inndata = extractServiceRequestFields().getOrThrow(),
            utdata = extractServiceResponseFields().getOrThrow(),
            flyt = extractFlow(SERVICE).getOrThrow(),
            gitHubUri = repo.toGithubURI(containingKtFile.name).getOrThrow()
        )
    }

    private fun KtClass.extractServiceRequestFields(): Result<List<PropertyInfo>> = runCatching {

        val (parameter, serviceRequestClass) = primaryConstructorOrThrow()
            .findDSLTypeServiceRequest(bindingContext)
            .getOrThrow()

        buildList {
            add(parameter.toPropertyInfo())
            addAll(serviceRequestClass.primaryConstructorOrThrow().toPropertyInfo())
        }
    }

    private fun KtClass.extractServiceResponseFields(): Result<List<PropertyInfo>> = runCatching {

        val serviceResponseClass = findGenericParameterForRuleServiceOrThrow()
            .resolveToKtClass(bindingContext)
            .getOrThrow()
            .isSubClassOfOrThrow(RESPONSE)

        buildList {
            add(
                PropertyInfo(
                    navn = serviceResponseClass.name!!,
                    type = serviceResponseClass.name!!,
                    beskrivelse = "Response for $name"
                )
            )
            addAll(serviceResponseClass.primaryConstructorOrThrow().toPropertyInfo())
        }
    }

    private fun KtClass.extractRuleFlow(): Result<RuleFlowInfo> = runCatching {
        RuleFlowInfo(
            navn = name!!,
            beskrivelse = getKDocOrEmpty(),
            inndata = extractFlowRequestFields().getOrThrow(),
            flyt = extractFlow(FLOW).getOrThrow(),
            gitHubUri = repo.toGithubURI(containingKtFile.name).getOrThrow()
        )
    }

    private fun KtClass.extractFlowRequestFields(): Result<List<PropertyInfo>> = runCatching {

        val (parameter, parameterType) = primaryConstructorOrThrow()
            .findFirstParameterOfTypeClassOrThrow(bindingContext)

        buildList {
            add(parameter.toPropertyInfo())
            addAll(parameterType.getProperties().toPropertyInfo())
        }
    }

    private fun KtClass.extractFlow(flowType: DSLTypeFlow): Result<FlowElement.Flow> = runCatching {

        findMatchingPropertyOrThrow(flowType).getLambdaBlock().flatMap {
            when (flowType) {
                SERVICE -> it.extractRuleServiceFlow(bindingContext)
                FLOW -> it.extractRuleFlowFlow(bindingContext)
            }
        }.getOrThrow()
    }

    private fun KtClass.extractRuleSet(): Result<RuleSetInfo> = runCatching {
        RuleSetInfo(
            navn = name!!,
            beskrivelse = getKDocOrEmpty(),
            inndata = emptyList(),
            flyt = FlowElement.Flow(emptyList()),
            gitHubUri = repo.toGithubURI(containingKtFile.name).getOrThrow()
        )
    }
}
