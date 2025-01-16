package rule.dsl

import embeddable.compiler.illegalState
import embeddable.compiler.requireBody
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtProperty
import result.addons.flatMap

enum class DSLTypeFlow(val typeName: String) {
    SERVICE("ruleService"),
    FLOW("ruleflow");

    override fun toString(): String = typeName
}

fun KtClass.findFlowProperty(flowType: DSLTypeFlow): Result<KtProperty> =
    requireBody().flatMap { body?.properties.findFlowProperty(flowType, this) }

fun List<KtProperty>?.findFlowProperty(flowType: DSLTypeFlow, caller: KtElement): Result<KtProperty> =
    this?.let {
        filter { it.hasModifier(KtTokens.OVERRIDE_KEYWORD) }
            .find { it.name == flowType.typeName }
            ?.let {
                Result.success(it)
            } ?: Result.failure(caller.illegalState("No override function ${flowType.typeName} found"))
    } ?: Result.failure(caller.illegalState("No properties found"))