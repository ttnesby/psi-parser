package rule.dsl

import embeddable.compiler.getLambdaBlock
import embeddable.compiler.illegalState
import embeddable.compiler.requireBody
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtProperty
import org.slf4j.LoggerFactory
import result.addons.flatMap
import rule.dsl.model.FlowElement
import rule.dsl.model.ParserConfig
import rule.dsl.model.extractFlowElements

private val logger = LoggerFactory.getLogger("DSLTypeFlow")

/**
 * Represents the flow types available in the domain-specific language (DSL).
 *
 * The `DSLTypeFlow` enum class is used to define and categorize specific types of flows in the
 * DSL. Each flow type is associated with a unique string identifier (`typeName`) which provides
 * its textual representation. This identifier plays a key role in matching and identifying flow
 * properties within the system.
 *
 * The enum defines the following values:
 * - `SERVICE`: Corresponds to the "ruleService" flow type, typically used for service-related flows.
 * - `FLOW`: Represents the "ruleflow" type, used for workflow-related logic flows.
 *
 * Functionality provided:
 * - `toString()`: Returns the `typeName` of the corresponding enum entry, enabling its direct use
 *   as a string representation in the DSL processing logic.
 */
enum class DSLTypeFlow(val typeName: String) {
    SERVICE("ruleService"),
    FLOW("ruleflow");

    override fun toString(): String = typeName
}

/**
 * Extracts a `FlowElement.Flow` from the context of the given class based on the specified flow type
 * and parser configuration.
 *
 * This method identifies the flow property within the class that matches the provided flow type,
 * retrieves the lambda block associated with the property, and processes it to extract flow elements.
 * The process logs both the beginning and end of flow extraction for traceability.
 *
 * @param flowType The type of flow to extract, represented by `DSLTypeFlow`.
 * @param config The parser configuration defining required handling and settings for flow extraction.
 * @return A `Result` wrapping the extracted `FlowElement.Flow` if successful, or an error if the extraction fails.
 */
fun KtClass.extractFlow(flowType: DSLTypeFlow, config: ParserConfig): Result<FlowElement.Flow> =
    findFlowProperty(flowType).flatMap { property ->
        property.getLambdaBlock()
    }.flatMap { block ->
        logger.info("Flow extraction ${flowType.typeName} - BEGIN")
        block.extractFlowElements(config).also {
            logger.info("Flow extraction ${flowType.typeName} - END")
        }
    }

/**
 * Finds a `KtProperty` within the body of the current `KtClass` that matches the specified flow type.
 *
 * This method attempts to locate a property in the class body that both overrides a property and
 * has a name matching the provided flow type's string representation (`typeName`). If no matching
 * property is found, an error is returned.
 *
 * @param flowType The type of flow to locate, represented by the `DSLTypeFlow` enum.
 * @return A `Result` containing the found `KtProperty` if successful, or an error if no matching
 *         property is found or the class body is missing.
 */
fun KtClass.findFlowProperty(flowType: DSLTypeFlow): Result<KtProperty> =
    requireBody().flatMap { body?.properties.findFlowProperty(flowType, this) }

/**
 * Finds a `KtProperty` that matches the given `DSLTypeFlow` from the list of properties.
 * The property must override the specified flow type name.
 *
 * @param flowType The `DSLTypeFlow` representing the flow type to match. This specifies the name
 *     of the property to find.
 * @param caller The `KtElement` on which the search is being performed. Used for generating
 *     error context if no matching property is found.
 * @return A `Result` containing the matching `KtProperty` if found, or an error with a message
 *     describing the failure if no matching property is found or the property list is null.
 */
fun List<KtProperty>?.findFlowProperty(flowType: DSLTypeFlow, caller: KtElement): Result<KtProperty> =
    this?.let {
        filter { it.hasModifier(KtTokens.OVERRIDE_KEYWORD) }
            .find { it.name == flowType.typeName }
            ?.let {
                Result.success(it)
            } ?: Result.failure(caller.illegalState("No override function ${flowType.typeName} found"))
    } ?: Result.failure(caller.illegalState("No properties found"))