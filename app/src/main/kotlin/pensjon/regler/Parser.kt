package pensjon.regler

import embeddable.compiler.KtElementToDescriptorResult
import embeddable.compiler.getLambdaBlock
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.slf4j.LoggerFactory
import pensjon.regler.repo.StringPathToUriResult
import result.addons.flatMap
import result.addons.toResult
import rule.dsl.DSLTypeFlow
import rule.dsl.findFlowProperty
import rule.dsl.firstDSLTypeAbstractOrNull
import rule.dsl.model.FlowElement
import rule.dsl.model.RuleInfo
import rule.dsl.model.extractFlowElements
import rule.dsl.model.extractRuleInfo

private val logger = LoggerFactory.getLogger("parser")

data class ParserConfig(
    val toGitHubURI: StringPathToUriResult,
    val resolveToDescriptor: KtElementToDescriptorResult,
    val allowEmptyFlow: Boolean
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

fun KtClass.extractFlow(flowType: DSLTypeFlow, config: ParserConfig): Result<FlowElement.Flow> =
    findFlowProperty(flowType).flatMap { property ->
        property.getLambdaBlock()
    }.flatMap { block ->
        logger.info("Flow extraction ${flowType.typeName} - BEGIN")
        block.extractFlowElements(config).also {
            logger.info("Flow extraction ${flowType.typeName} - END")
        }
    }
