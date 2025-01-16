package rule.dsl.model

import embeddable.compiler.*
import org.jetbrains.kotlin.psi.*
import org.slf4j.LoggerFactory
import pensjon.regler.ParserConfig
import result.addons.flatMap
import result.addons.toResult
import rule.dsl.DSLTypeAbstract.*
import rule.dsl.DSLTypeBranch.FORGRENING
import rule.dsl.DSLTypeFlow
import rule.dsl.findDSLTypeBranchOrNull
import rule.dsl.findFlowProperty
import rule.dsl.model.FlowElement.Condition
import rule.dsl.resolveReceiverClass
import java.io.File

private val logger = LoggerFactory.getLogger("FlowElement")

sealed class FlowElement {

    data class Oppdater(val beskrivelse: String, val uttrykk: String) : FlowElement()
    data class Merknad(val beskrivelse: String, val uttrykk: String) : FlowElement()
    data class While(val betingelse: String, val flyt: Flow): FlowElement()
    data class Flow(val elementer: List<FlowElement>) : FlowElement()
    data class Forgrening(val beskrivelse: String, val navn: String, val gren: List<Gren>) :
        FlowElement()

    data class Condition(val navn: String, val uttrykk: String)
    data class Gren(val beskrivelse: String, val betingelse: Condition, val flyt: Flow) :
        FlowElement()

    // reference to flow element in other files
    data class RuleFlow(val navn: String, val beskrivelse: String, val fil: File) : FlowElement()
    data class RuleSet(val navn: String, val beskrivelse: String, val fil: File) : FlowElement()
    data class Function(val navn: String, val beskrivelse: String, val fil: File) : FlowElement()
}

fun KtClass.extractFlow(flowType: DSLTypeFlow, config: ParserConfig): Result<FlowElement.Flow> =
    findFlowProperty(flowType).flatMap { property ->
        property.getLambdaBlock()
    }.flatMap { block ->
        logger.info("Flow extraction ${flowType.typeName} - BEGIN")
        block.extractFlowElements(config).also {
            logger.info("Flow extraction ${flowType.typeName} - END")
        }
    }

// TODO - NB! når KDoc er relatert til flow/ruleset/function - this.children -> this.statements
fun KtBlockExpression.extractFlowElements(config: ParserConfig): Result<FlowElement.Flow> =
    children.mapNotNull { child ->
        when (child) {
            is KtBinaryExpression -> child.extractInitializer(child.extractDocOrEmpty(), config)
            is KtProperty -> child.extractInitializer(child.extractDocOrEmpty(), config)
            is KtCallExpression ->
                child.extractForgreningOrNull(config) ?: child.extractFunctionReference(child.extractDocOrEmpty(), config)

            is KtDotQualifiedExpression -> child.extractFlowReference(child.extractDocOrEmpty(), config)
            is KtWhileExpression -> child.extractWhile(config) // see FaktoromregnInntekterBatchFlyt as example
            else -> null
        }
    }
        .let { flyt ->
            if (flyt.isEmpty()) {
                logger.error(illegalState("Empty flow with current flow extraction logic").message)
                if (config.allowEmptyFlow) Result.success(FlowElement.Flow(emptyList()))
                else Result.failure(illegalState("Empty FlowElements.Flow"))
            } else {
                flyt.toResult().map { FlowElement.Flow(it) }
            }
        }

private fun KtWhileExpression.extractWhile(config: ParserConfig): Result<FlowElement> =
    condition
        ?.let { expression ->
            (body as? KtBlockExpression)
                ?.let { blockExpression ->
                    blockExpression.extractFlowElements(config).map { flyt ->
                        FlowElement.While(
                            betingelse = expression.text,
                            flyt = flyt
                        )
                    }
                } ?: Result.failure(illegalState("No block expression for while"))
        } ?: Result.failure(illegalState("No condition for while"))

private fun KtBinaryExpression.extractInitializer(doc: String, config: ParserConfig): Result<FlowElement>? =
    if (right is KtPostfixExpression) extractOppdater(doc)
    else right?.extractInitializerExpression(doc, config)

private fun KtProperty.extractInitializer(doc: String, config: ParserConfig): Result<FlowElement>? =
    initializer?.extractInitializerExpression(doc, config)

private fun KtExpression.extractInitializerExpression(doc: String, config: ParserConfig): Result<FlowElement>? =
    when (this) {
        is KtCallExpression -> extractFunctionReference(doc, config)
        is KtDotQualifiedExpression -> extractFlowReference(doc, config)
        else -> null
    }

// TODO sjekk med Erik/Jens - må sjekke med EQ og forskjellen mellom Postfix versus DotQualified...
private fun KtBinaryExpression.extractOppdater(doc: String): Result<FlowElement> =
    Result.success(
        FlowElement.Oppdater(
            beskrivelse = doc,
            uttrykk = this.text
        )
    )

private fun KtClass.toRuleFlowReference(doc: String): Result<FlowElement.RuleFlow> =
    requireName().map { name ->
        FlowElement.RuleFlow(
            navn = name,
            beskrivelse = doc,
            fil = File(containingKtFile.name)
        )
    }

private fun KtClass.toRuleSetReference(doc: String): Result<FlowElement.RuleSet> =
    requireName().map { name ->
        FlowElement.RuleSet(
            navn = name,
            beskrivelse = doc,
            fil = File(containingKtFile.name)
        )
    }

private fun KtCallExpression.extractFunctionReference(doc: String, config: ParserConfig): Result<FlowElement.Function>? =
    resolveFunctionDeclaration(config)
        .map { (name, file) ->
            FlowElement.Function(
                navn = name,
                beskrivelse = doc,
                fil = file
            )
        }
        // TODO - must add required functions
        // @TestOnly - temporary handling missing in binding context
        .fold(
            onSuccess = { Result.success(it) },
            onFailure = {
                logger.warn("Missing func ${(this.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()} in binding context ${it.message}")
                null
            }
        )



// TODO verifiser med Erik/Jens - gjelder for flyt som har 2 children?
private fun KtDotQualifiedExpression.extractMerknad(doc: String): Result<FlowElement> =
    Result.success(
        FlowElement.Merknad(
            beskrivelse = doc,
            uttrykk = this.text
        )
    )

private fun KtDotQualifiedExpression.extractFlowReference(doc: String, config: ParserConfig): Result<FlowElement>? =
    if (doc.contains("merknad", ignoreCase = true)) extractMerknad(doc)
    else
        resolveReceiverClass(config)
            ?.let { (resolvedClass, dslTypeAbstract) ->
                when (dslTypeAbstract) {
                    RULE_FLOW -> resolvedClass.toRuleFlowReference(doc)
                    RULE_SET -> resolvedClass.toRuleSetReference(doc)
                    RULE_SERVICE -> null
                }
            }

/**
 * Extract forgrening
 * ```
 * forgrening(string) {lambda block}
 * ```
 *
 * @return A 'Result' wrapping 'FlowElement.Forgrening'
 */

private fun KtCallExpression.extractForgrening(config: ParserConfig): Result<FlowElement.Forgrening> =
    firstArgument().flatMap { name ->
        logger.trace("forgrening $name - BEGIN]")
        getLambdaBlock().flatMap { blockExpression ->
            blockExpression.extractGrener(config).map { grener ->
                logger.trace("forgrening $name - END]")
                FlowElement.Forgrening(
                    beskrivelse = extractDocOrEmpty(),
                    navn = name,
                    gren = grener
                )
            }
        }
    }

private fun KtCallExpression.firstArgument(): Result<String> =
    valueArguments
        .firstOrNull()
        ?.let { arg ->
            Result.success(arg.text.removeSurrounding("\""))
        } ?: Result.failure(illegalState("No name found for forgrening"))

/**
 * Extract a list of 'gren' call expressions from a forgrening lambda block,
 * and map each call expression to 'FlowElement.Gren'
 * ```
 * {
 *      gren...
 *      gren...
 *      ...
 * }
 * ```
 * @return A 'Result' wrapping a list of 'FlowElement.Gren'
 */

private fun KtBlockExpression.extractGrener(config: ParserConfig): Result<List<FlowElement.Gren>> =
    this.statements
        .mapNotNull { expression -> (expression as? KtCallExpression) }
        .map { it.extractGren(config) }
        .let { aList ->
            if (aList.isEmpty()) Result.failure(
                illegalState("Empty 'forgrening', require at least 2 'gren' expressions")
            )
            else aList.toResult()
        }

/**
 * Extract gren
 * ```
 * gren {
 *      betingelse...
 *      flyt...
 * }
 * ```
 */

private fun KtCallExpression.extractGren(config: ParserConfig): Result<FlowElement.Gren> =
    getLambdaBlock().flatMap { blockExpression ->
        blockExpression.findBetingelseAndFlyt().flatMap { (betingelseExpr, flytExpr) ->
            betingelseExpr.extractBetingelse().flatMap { betingelse ->
                flytExpr.getLambdaBlock().flatMap { flytBlockExpression ->
                    val flytResult =
                        // do not extractFlowElements for empty flyt - `flyt {}` - gives error
                        if (flytBlockExpression.children.isEmpty()) Result.success(FlowElement.Flow(emptyList()))
                        else flytBlockExpression.extractFlowElements(config)

                    flytResult.map { flyt ->
                        FlowElement.Gren(
                            beskrivelse = extractDocOrEmpty(),
                            betingelse = betingelse,
                            flyt = flyt
                        )
                    }
                }
            }
        }
    }

/**
 * Extract a pair of call expressions from gren lambda block
 *```
 * {
 *      betingelse(string)? {lambda block}
 *      flyt {lambda block}
 * }
 * ```
 * @return A 'Result' wrapping a 'Pair' of call expressions, (betingelse,flyt)
 */

private fun KtBlockExpression.findBetingelseAndFlyt(): Result<Pair<KtCallExpression, KtCallExpression>> =
    this.statements
        .mapNotNull { expression -> (expression as? KtCallExpression) }
        .let { aList ->
            if (aList.size == 2) Result.success(Pair(aList[0], aList[1]))
            else Result.failure(illegalState("Invalid 'gren' content, expected expression 'betingelse' and 'fly'"))
        }

/**
 * Extract betingelse
 * ```
 *   betingelse(string)? {lambda block}
 * ```
 */

private fun KtCallExpression.extractBetingelse(): Result<Condition> =
    getLambdaBlock().map { blockExpression: KtBlockExpression ->
        Condition(
            navn = this.firstArgumentOrEmpty(),
            uttrykk = blockExpression.text.trim().replace("\\s+".toRegex(), " ")
        )
    }

private fun KtCallExpression.extractForgreningOrNull(config: ParserConfig): Result<FlowElement>? =
    findDSLTypeBranchOrNull()
        ?.let { dslTypeBranch ->
            when (dslTypeBranch) {
                FORGRENING -> extractForgrening(config)
            }
        }

