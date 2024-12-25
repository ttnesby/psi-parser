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
                    ktClass.extractRuleInfo(dslTypeAbstract)
                }
        }.also {
            psiFiles.forEach { (it as PsiFileImpl).clearCaches() }
        }.toResult()

    private fun KtClass.extractRuleInfo(dslType: DSLTypeAbstract): Result<RuleInfo> = when (dslType) {
        RULE_SERVICE -> extractRuleService()
        RULE_FLOW -> extractRuleFlow()
        RULE_SET -> extractRuleSet()
    }

    private fun KtClass.extractRuleService(): Result<RuleServiceInfo> =
        requireName().flatMap { name ->
            extractServiceRequestFields().flatMap { requestFields ->
                extractServiceResponseFields().flatMap { responseFields ->
                    extractFlow(SERVICE).flatMap { flow ->
                        repo.toGithubURI(containingKtFile.name).map { gitHubUri ->
                            RuleServiceInfo(
                                navn = name,
                                beskrivelse = docOrEmpty(),
                                inndata = requestFields,
                                utdata = responseFields,
                                flyt = flow,
                                gitHubUri = gitHubUri
                            )
                        }
                    }
                }
            }
        }

    private fun KtClass.extractServiceRequestFields(): Result<List<PropertyInfo>> =
        requirePrimaryConstructor()
            .flatMap { primConstr ->
                primConstr.findParameterDSLTypeServiceRequest(bindingContext)
            }.flatMap { (parameter, serviceRequestClass) ->
                serviceRequestClass
                    .requirePrimaryConstructor().map { primConstr ->
                        buildList {
                            add(parameter.toPropertyInfo())
                            addAll(primConstr.toPropertyInfo())
                        }
                    }
        }

    private fun KtClass.extractServiceResponseFields(): Result<List<PropertyInfo>> =
        findResponseTypeForRuleService()
            .flatMap { typeReference ->
                typeReference.resolveToKtClass(bindingContext)
            }.flatMap { aClass ->
                aClass.mustBeSubClassOf(RESPONSE)
            }.flatMap { serviceResponseClass ->
                serviceResponseClass
                    .requirePrimaryConstructor().map { primConstr ->
                        buildList {
                            add(
                                // TODO - fix - require name
                                PropertyInfo(
                                    navn = serviceResponseClass.name!!,
                                    type = serviceResponseClass.name!!,
                                    beskrivelse = "Response for $name"
                                )
                            )
                            addAll(primConstr.toPropertyInfo())
                        }
                    }
        }

    private fun KtClass.extractRuleFlow(): Result<RuleFlowInfo> =
        requireName().flatMap { name ->
            extractFlowRequestFields().flatMap { requestFields ->
                extractFlow(FLOW).flatMap { flow ->
                    repo.toGithubURI(containingKtFile.name).map { gitHubUri ->
                        RuleFlowInfo(
                            navn = name,
                            beskrivelse = docOrEmpty(),
                            inndata = requestFields,
                            flyt = flow,
                            gitHubUri = gitHubUri
                        )
                    }
                }
            }
        }

    private fun KtClass.extractFlowRequestFields(): Result<List<PropertyInfo>> =
        requirePrimaryConstructor().flatMap { primConstr ->
            primConstr.findFirstParameterOfTypeClass(bindingContext)
        }.map { (parameter, aClass) ->
            buildList {
                add(parameter.toPropertyInfo())
                addAll(aClass.getProperties().toPropertyInfo())
            }
        }

    private fun KtClass.extractFlow(flowType: DSLTypeFlow): Result<FlowElement.Flow> =
        findMatchingProperty(flowType).flatMap { property ->
            property.getLambdaBlock()
        }.flatMap { block ->
            when (flowType) {
                SERVICE -> block.extractRuleServiceFlow(bindingContext)
                FLOW -> block.extractRuleFlowFlow(bindingContext)
            }
        }

    private fun KtClass.extractRuleSet(): Result<RuleSetInfo> =
        requireName().flatMap { name ->
            repo.toGithubURI(containingKtFile.name).map { gitHubUri ->
                RuleSetInfo(
                    navn = name,
                    beskrivelse = docOrEmpty(),
                    inndata = emptyList(),
                    flyt = FlowElement.Flow(emptyList()),
                    gitHubUri = gitHubUri
                )
            }
        }
}
