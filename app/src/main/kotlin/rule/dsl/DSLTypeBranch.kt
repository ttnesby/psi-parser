package rule.dsl

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

/**
 * Represents an enumeration of DSL type branches in a domain-specific language (DSL).
 *
 * This enum is designed to categorize and identify specific branches of DSL logic. Each entry in
 * the enum is mapped to a unique string identifier (`typeName`) that provides its textual representation
 * and can be used for matching and type comparisons throughout the system.
 *
 * The enum defines the following functionalities:
 * - `toString()`: Returns the `typeName` associated with the enum entry as its string representation.
 * - `fromString(typeName: String)`: Static method provided in the companion object to retrieve the
 *   corresponding `DSLTypeBranch` instance for a given string name, or returns null if no match exists.
 *
 * This enum currently supports the following type:
 * - `FORGRENING`: Represents a branching logic type in the DSL (denoted by the string "forgrening").
 *
 * Observe that `FORGRENING` implies the following block expression and expected content
 * ```
 * {
 *      gren {...}
 *      gren {...}
 *      ...
 *      gren {..}
 * }
 * ```
 * Thus, there is no need for `GREN` or other terms within `GREN` - it's given. See extraction functions
 * in FlowElement
 */
enum class DSLTypeBranch(val typeName: String) {
    FORGRENING("forgrening");

    override fun toString(): String = typeName

    companion object {
        fun fromString(typeName: String): DSLTypeBranch? = entries.find { it.typeName == typeName }
    }
}

fun KtCallExpression.findDSLTypeBranchOrNull(): DSLTypeBranch? =
    (calleeExpression as? KtNameReferenceExpression)
        ?.getReferencedName()
        ?.let { name -> DSLTypeBranch.fromString(name) }
