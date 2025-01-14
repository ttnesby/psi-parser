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

data class ParserConfig(
    val toGitHubURI: StringPathToUriResult,
    val resolveToDescriptor: KtElementToDescriptorResult
)

fun psiFilesToModel(
    psiFiles: List<KtFile>,
    config: ParserConfig
): Result<List<RuleInfo>> =
    psiFiles.mapNotNull { file ->
        file.firstDSLTypeAbstractOrNull()
            ?.let { (ktClass, dslTypeAbstract) ->
                ktClass.extractRuleInfo(dslTypeAbstract, config)
            }
    }.also {
        psiFiles.forEach { (it as PsiFileImpl).clearCaches() }
    }.toResult()

private fun KtClass.extractRuleInfo(
    dslType: DSLTypeAbstract,
    config: ParserConfig
): Result<RuleInfo> = when (dslType) {
    RULE_SERVICE -> extractRuleService(config)
    RULE_FLOW -> extractRuleFlow(config)
    RULE_SET -> extractRuleSet(config)
}

private fun KtClass.extractRuleService(config: ParserConfig): Result<RuleServiceInfo> =
    requireName().flatMap { name ->
        logger.info("Rule service $name - BEGIN")
        extractServiceRequestFields(config).flatMap { requestFields ->
            extractServiceResponseFields(config).flatMap { responseFields ->
                extractFlow(SERVICE, config).flatMap { flow ->
                    config.toGitHubURI(containingKtFile.name).map { gitHubUri ->
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

private fun KtClass.extractServiceRequestFields(config: ParserConfig): Result<List<PropertyInfo>> =
    requirePrimaryConstructor()
        .flatMap { primConstr ->
            primConstr.findParameterDSLTypeServiceRequest(config)
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

private fun KtClass.extractServiceResponseFields(config: ParserConfig): Result<List<PropertyInfo>> =
    findResponseTypeForRuleService()
        .flatMap { typeReference ->
            typeReference.resolveToKtClass(config)
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

private fun KtClass.extractRuleFlow(config: ParserConfig): Result<RuleFlowInfo> =
    requireName().flatMap { name ->
        logger.info("Rule flow $name - BEGIN")
        extractFlowRequestFields(config).flatMap { requestFields ->
            extractFlow(FLOW, config).flatMap { flow ->
                config.toGitHubURI(containingKtFile.name).map { gitHubUri ->
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

private fun KtClass.extractFlowRequestFields(config: ParserConfig): Result<List<PropertyInfo>> =
    requirePrimaryConstructor().flatMap { primConstr ->
        primConstr.findFirstParameterOfTypeClass(config)
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

private fun KtClass.extractFlow(flowType: DSLTypeFlow, config: ParserConfig): Result<FlowElement.Flow> =
    findFlowProperty(flowType).flatMap { property ->
        property.getLambdaBlock()
    }.flatMap { block ->
        logger.info("Flow extraction ${flowType.typeName} - BEGIN")
        block.extractFlowElements(config).also {
            logger.info("Flow extraction ${flowType.typeName} - END")
        }
    }

private fun KtClass.extractRuleSet(config: ParserConfig): Result<RuleSetInfo> =
    requireName().flatMap { name ->
        logger.info("Rule set $name - BEGIN")
        config.toGitHubURI(containingKtFile.name).map { gitHubUri ->
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
