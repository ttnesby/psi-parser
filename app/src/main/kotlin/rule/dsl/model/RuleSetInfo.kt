package rule.dsl.model

import embeddable.compiler.docOrEmpty
import embeddable.compiler.requireName
import org.jetbrains.kotlin.psi.KtClass
import org.slf4j.LoggerFactory
import pensjon.regler.ParserConfig
import result.addons.flatMap
import java.net.URI

private val logger = LoggerFactory.getLogger("RuleSetInfo")

data class RuleSetInfo(
    override val navn: String,
    override val beskrivelse: String,
    override val inndata: List<PropertyInfo>,
    override val flyt: FlowElement.Flow,
    override val gitHubUri: URI,
) : RuleInfo

fun KtClass.extractRuleSet(config: ParserConfig): Result<RuleSetInfo> =
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