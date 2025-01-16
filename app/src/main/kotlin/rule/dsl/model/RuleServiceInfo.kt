package rule.dsl.model

import embeddable.compiler.*
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtTypeReference
import org.slf4j.LoggerFactory
import result.addons.flatMap
import rule.dsl.DSLTypeAbstract.RULE_SERVICE
import rule.dsl.DSLTypeFlow.SERVICE
import rule.dsl.DSLTypeService.REQUEST
import rule.dsl.DSLTypeService.RESPONSE
import rule.dsl.isClassOf
import rule.dsl.mustBeSubClassOf
import java.net.URI

private val logger = LoggerFactory.getLogger("RuleServiceInfo")

data class RuleServiceInfo(
    override val navn: String,
    override val beskrivelse: String,
    override val inndata: List<PropertyInfo>,
    val utdata: List<PropertyInfo>,
    override val flyt: FlowElement.Flow,
    override val gitHubUri: URI,
) : RuleInfo

fun KtClass.extractRuleService(config: ParserConfig): Result<RuleServiceInfo> =
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

// TODO - er det rimelig å anta at service request param er 1. param av typen klasse?
private fun KtPrimaryConstructor.findParameterDSLTypeServiceRequest(config: ParserConfig): Result<Pair<KtParameter, KtClass>> =
    findFirstParameterOfTypeClass(config).flatMap { pair ->
        pair.second.mustBeSubClassOf(REQUEST).map { pair }
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

private fun KtClass.findResponseTypeForRuleService(): Result<KtTypeReference> =
    superTypeListEntries
        .find { it.isClassOf(RULE_SERVICE) }
        ?.findGenericTypeReference()
        ?: Result.failure(illegalState("No service response type found"))