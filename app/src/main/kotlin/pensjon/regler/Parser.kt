package pensjon.regler

import embeddable.compiler.*
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.slf4j.LoggerFactory
import pensjon.regler.repo.StringPathToUriResult
import result.addons.flatMap
import result.addons.toResult
import rule.dsl.DSLTypeAbstract
import rule.dsl.DSLTypeAbstract.*
import rule.dsl.DSLTypeFlow
import rule.dsl.DSLTypeFlow.FLOW
import rule.dsl.DSLTypeFlow.SERVICE
import rule.dsl.DSLTypeService.RESPONSE


private val logger = LoggerFactory.getLogger("parser")

fun psiFilesToModel(
    toGitHubURI: StringPathToUriResult,
    psiFiles: List<KtFile>
): Result<List<RuleInfo>> =
    psiFiles.mapNotNull { file ->
        file.firstDSLTypeAbstractOrNull()
            ?.let { (ktClass, dslTypeAbstract) ->
                ktClass.extractRuleInfo(dslTypeAbstract, toGitHubURI)
            }
    }.also {
        psiFiles.forEach { (it as PsiFileImpl).clearCaches() }
    }.toResult()

private fun KtClass.extractRuleInfo(
    dslType: DSLTypeAbstract,
    toGitHubURI: StringPathToUriResult
): Result<RuleInfo> = when (dslType) {
    RULE_SERVICE -> extractRuleService(toGitHubURI)
    RULE_FLOW -> extractRuleFlow(toGitHubURI)
    RULE_SET -> extractRuleSet(toGitHubURI)
}

private fun KtClass.extractRuleService(toGitHubURI: StringPathToUriResult): Result<RuleServiceInfo> =
    requireName().flatMap { name ->
        logger.info("Rule service $name - BEGIN")
        extractServiceRequestFields().flatMap { requestFields ->
            extractServiceResponseFields().flatMap { responseFields ->
                extractFlow(SERVICE).flatMap { flow ->
                    toGitHubURI(containingKtFile.name).map { gitHubUri ->
                        logger.info("Rule service $name - END")
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
            primConstr.findParameterDSLTypeServiceRequest()
        }.flatMap { (parameter, serviceRequestClass) ->
            serviceRequestClass
                .requirePrimaryConstructor().flatMap { primConstr ->
                    primConstr.toPropertyInfo().flatMap { properties ->
                        parameter.toPropertyInfo().map { property ->
                            buildList {
                                add(property)
                                addAll(properties)
                            }
                        }
                    }
                }
        }

private fun KtClass.extractServiceResponseFields(): Result<List<PropertyInfo>> =
    findResponseTypeForRuleService()
        .flatMap { typeReference ->
            typeReference.resolveToKtClass()
        }.flatMap { aClass ->
            aClass.mustBeSubClassOf(RESPONSE)
        }.flatMap { serviceResponseClass ->
            serviceResponseClass
                .requirePrimaryConstructor().flatMap { primConstr ->
                    primConstr.toPropertyInfo().flatMap { properties ->
                        serviceResponseClass.toPropertyInfo().map { property ->
                            buildList {
                                add(property)
                                addAll(properties)
                            }
                        }
                    }
                }
        }

private fun KtClass.extractRuleFlow(toGitHubURI: StringPathToUriResult): Result<RuleFlowInfo> =
    requireName().flatMap { name ->
        logger.info("Rule flow $name - BEGIN")
        extractFlowRequestFields().flatMap { requestFields ->
            extractFlow(FLOW).flatMap { flow ->
                toGitHubURI(containingKtFile.name).map { gitHubUri ->
                    logger.info("Rule flow $name - END")
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
        primConstr.findFirstParameterOfTypeClass()
    }.flatMap { (parameter, aClass) ->
        parameter.toPropertyInfo().flatMap { property ->
            aClass.getProperties().toPropertyInfo().map { properties ->
                buildList {
                    add(property)
                    addAll(properties)
                }
            }
        }
    }

private fun KtClass.extractFlow(flowType: DSLTypeFlow): Result<FlowElement.Flow> =
    findFlowProperty(flowType).flatMap { property ->
        property.getLambdaBlock()
    }.flatMap { block ->
        logger.info("Flow extraction ${flowType.typeName} - BEGIN")
        block.extractFlowElements().also {
            logger.info("Flow extraction ${flowType.typeName} - END")
        }
    }

private fun KtClass.extractRuleSet(toGitHubURI: StringPathToUriResult): Result<RuleSetInfo> =
    requireName().flatMap { name ->
        logger.info("Rule set $name - BEGIN")
        toGitHubURI(containingKtFile.name).map { gitHubUri ->
            logger.info("Rule set $name - END")
            RuleSetInfo(
                navn = name,
                beskrivelse = docOrEmpty(),
                inndata = emptyList(),
                flyt = FlowElement.Flow(emptyList()),
                gitHubUri = gitHubUri
            )
        }
    }
