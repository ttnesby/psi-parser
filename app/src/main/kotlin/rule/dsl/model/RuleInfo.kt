package rule.dsl.model

import embeddable.compiler.KtElementToDescriptorResult
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import repository.StringPathToUriResult
import result.addons.toResult
import rule.dsl.DSLTypeAbstract
import rule.dsl.DSLTypeAbstract.*
import rule.dsl.firstDSLTypeAbstractOrNull
import java.net.URI

sealed interface RuleInfo {
    val navn: String
    val beskrivelse: String
    val inndata: List<PropertyInfo>
    val flyt: FlowElement.Flow
    val gitHubUri: URI
}

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

private fun KtClass.extractRuleInfo(dslType: DSLTypeAbstract, config: ParserConfig): Result<RuleInfo> = when (dslType) {
    RULE_SERVICE -> extractRuleService(config)
    RULE_FLOW -> extractRuleFlow(config)
    RULE_SET -> extractRuleSet(config)
}