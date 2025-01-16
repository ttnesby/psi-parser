package rule.dsl.model

import org.jetbrains.kotlin.psi.KtClass
import pensjon.regler.ParserConfig
import rule.dsl.DSLTypeAbstract
import rule.dsl.DSLTypeAbstract.*
import java.net.URI

sealed interface RuleInfo {
    val navn: String
    val beskrivelse: String
    val inndata: List<PropertyInfo>
    val flyt: FlowElement.Flow
    val gitHubUri: URI
}

fun KtClass.extractRuleInfo(dslType: DSLTypeAbstract, config: ParserConfig): Result<RuleInfo> = when (dslType) {
    RULE_SERVICE -> extractRuleService(config)
    RULE_FLOW -> extractRuleFlow(config)
    RULE_SET -> extractRuleSet(config)
}