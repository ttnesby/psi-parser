package rule.dsl.model

import embeddable.compiler.docOrEmpty
import embeddable.compiler.findFirstParameterOfTypeClass
import embeddable.compiler.requireName
import embeddable.compiler.requirePrimaryConstructor
import org.jetbrains.kotlin.psi.KtClass
import org.slf4j.LoggerFactory
import result.addons.flatMap
import rule.dsl.DSLTypeFlow.FLOW
import java.net.URI

private val logger = LoggerFactory.getLogger("RuleFlowInfo")

data class RuleFlowInfo(
    override val navn: String,
    override val beskrivelse: String,
    override val inndata: List<PropertyInfo>,
    override val flyt: FlowElement.Flow,
    override val gitHubUri: URI,
) : RuleInfo

fun KtClass.extractRuleFlow(config: ParserConfig): Result<RuleFlowInfo> =
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