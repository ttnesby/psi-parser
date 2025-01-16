package rule.dsl

import embeddable.compiler.findReferenceExpressionInChain
import embeddable.compiler.illegalState
import embeddable.compiler.resolveToKtClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import pensjon.regler.ParserConfig

/**
 * Represents a sealed interface for defining different types of DSL classifications related to
 * abstract or service structures in a rule-based domain-specific language.
 *
 * Implementing classes or enums must override the `typeName` property to define a unique string
 * identifier for the specific DSL type. This is used for type checking and matching purposes in
 * various Kotlin PSI (Program Structure Interface) analysis functions.
 */
sealed interface DSLTypeAbstractOrService {
    val typeName: String
}

private fun KtClass.isSubClassOf(type: DSLTypeAbstractOrService): Boolean =
    superTypeListEntries.any { it.isClassOf(type) }

fun KtSuperTypeListEntry.isClassOf(type: DSLTypeAbstractOrService): Boolean =
    typeReference?.text?.contains(type.typeName) == true


/**
 * Represents a specific type classification for service-related structures in a domain-specific language (DSL).
 *
 * This enum defines the types of services available within the DSL, each having a unique string identifier.
 * It implements the `DSLTypeAbstractOrService` interface, allowing integration with other DSL components
 * utilizing this hierarchy.
 *
 * The types defined in this enum are:
 * - `REQUEST`: Represents a Service Request type.
 * - `RESPONSE`: Represents a Service Response type.
 *
 * `typeName` is overridden to provide the unique string identifier for each type, and `toString()`
 * returns the `typeName` for textual representation.
 */
enum class DSLTypeService(override val typeName: String) : DSLTypeAbstractOrService {
    REQUEST("ServiceRequest"),
    RESPONSE("ServiceResponse");

    override fun toString(): String = typeName
}

fun KtClass.mustBeSubClassOf(type: DSLTypeService): Result<KtClass> =
    superTypeListEntries
        .find { it.isClassOf(type) }
        ?.let { Result.success(this) }
        ?: Result.failure(illegalState("$name is not sub class of ${type.typeName}"))


/**
 * Enumeration representing different types of abstract structures in a rule-based domain-specific language (DSL).
 *
 * Each enum entry corresponds to a specific type of abstract structure, such as rule services,
 * rule flows, or rule sets, and associates a unique string identifier (`typeName`) with it.
 *
 * Implements the `DSLTypeAbstractOrService` interface, which requires providing a `typeName`
 * property used for classification and type matching purposes.
 */
enum class DSLTypeAbstract(override val typeName: String) : DSLTypeAbstractOrService{
    RULE_SERVICE("AbstractPensjonRuleService"),
    RULE_FLOW("AbstractPensjonRuleflow"),
    RULE_SET("AbstractPensjonRuleset");

    override fun toString(): String = typeName
}

fun KtFile.firstDSLTypeAbstractOrNull(): Pair<KtClass, DSLTypeAbstract>? =
    declarations
        .filterIsInstance<KtClass>()
        .firstOrNull()
        ?.findDSLTypeAbstractOrNull()

fun KtClass.findDSLTypeAbstractOrNull(): Pair<KtClass, DSLTypeAbstract>? =
    DSLTypeAbstract
        .entries
        .firstOrNull { dslType -> isSubClassOf(dslType) }
        ?.let { dslType -> Pair(this, dslType) }

fun KtDotQualifiedExpression.resolveReceiverClass(config: ParserConfig): Pair<KtClass, DSLTypeAbstract>? =
    findReferenceExpressionInChain()
        ?.resolveToKtClass(config)?.map {
            it.findDSLTypeAbstractOrNull()
        }
        ?.getOrNull()


