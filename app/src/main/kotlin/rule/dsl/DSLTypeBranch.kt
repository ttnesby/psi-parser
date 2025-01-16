package rule.dsl

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression

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
