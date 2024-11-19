package org.example

import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

enum class RuleSuperType(val className: String) {
    SERVICE_REQUEST("ServiceRequest"),
    SERVICE_RESPONSE("ServiceResponse"),
    RULE_SERVICE("AbstractPensjonRuleService"),
    RULE_FLOW("AbstractPensjonRuleflow"),
    RULE_SET("AbstractPensjonRuleSet");

    companion object {
        fun fromClassName(className: String): RuleSuperType? =
                values().find { it.className == className }
    }

    override fun toString(): String = className
}

enum class RuleMethod(val methodName: String) {
    RULE_SERVICE("ruleService"),
    RULE_FLOW("ruleFlow");

    companion object {
        fun fromClassName(methodName: String): RuleMethod? =
                values().find { it.methodName == methodName }
    }

    override fun toString(): String = methodName
}

/** KtFile extension functions */
//

// filter and eventually get class of given super type from KtFile
fun KtFile.getClassWithSuperType(superTypeRef: (KtClass) -> Boolean): Result<KtClass> =
        runCatching {
            declarations.asSequence().filterIsInstance<KtClass>().firstOrNull(superTypeRef)
                    ?: throw NoSuchElementException("No class found with specified supertype")
        }

/** KtClass extension functions */
//

// functions to be superTypeRef parameter in KtFile.getClassWithSuperType
fun KtClass.isRuleServiceClass(): Boolean = isSubTypeOf(RuleSuperType.RULE_SERVICE)

fun KtClass.isRuleFlowClass(): Boolean = isSubTypeOf(RuleSuperType.RULE_FLOW)

fun KtClass.isRuleSetClass(): Boolean = isSubTypeOf(RuleSuperType.RULE_SET)

fun KtClass.isServiceRequestClass(): Boolean = isSubTypeOf(RuleSuperType.SERVICE_REQUEST)

fun KtClass.isServiceResponseClass(): Boolean = isSubTypeOf(RuleSuperType.SERVICE_RESPONSE)

private fun KtClass.isSubTypeOf(type: RuleSuperType): Boolean =
        getSuperTypeListEntries().any { it.typeReference?.text?.contains(type.className) == true }

fun KtClass.getKDocOrEmpty(): String = docComment?.getOrEmpty() ?: ""

data class ServiceRequestInfo(val parameter: KtParameter, val resolvedClass: KtClass)

fun KtClass.getServiceRequestInfo(bindingContext: BindingContext): Result<ServiceRequestInfo> =
        runCatching {
            primaryConstructor?.valueParameters?.firstNotNullOfOrNull { parameter ->
                parameter
                        .getClassWithSuperType(KtClass::isServiceRequestClass, bindingContext)
                        .map { resolvedClass -> ServiceRequestInfo(parameter, resolvedClass) }
                        .getOrNull()
            }
                    ?: throw NoSuchElementException(
                            "No ServiceRequest parameter found in primary constructor"
                    )
        }

fun KtClass.getServiceResponseClass(bindingContext: BindingContext): Result<KtClass> =
        getClassOfSuperTypeParam(
                supertype = RuleSuperType.RULE_SERVICE,
                classTypeRef = KtClass::isServiceResponseClass,
                bindingContext = bindingContext
        )

private fun KtClass.getClassOfSuperTypeParam(
        supertype: RuleSuperType, // e.g., "AbstractPensjonRuleService"
        classTypeRef: (KtClass) -> Boolean, // e.g., KtClass::isServiceResponseClass
        bindingContext: BindingContext
): Result<KtClass> = runCatching {
    getSuperTypeListEntries()
            .find { it.typeReference?.text?.contains(supertype.className) == true }
            ?.typeReference
            ?.typeElement
            ?.typeArgumentsAsTypes
            ?.getOrNull(0)
            ?.resolveToKtClass(bindingContext)
            ?.map { resolvedClass ->
                if (classTypeRef(resolvedClass)) resolvedClass
                else throw NoSuchElementException("Class is not of expected type")
            }
            ?.getOrThrow()
            ?: throw NoSuchElementException("No type parameter found for $supertype")
}

fun KtClass.getRuleServiceMethod(bindingContext: BindingContext): Result<Sequence<KtElement>> =
        getOverriddenProperty(RuleMethod.RULE_SERVICE).mapCatching { property ->
            property.streamRuleElements(RuleSuperType.RULE_FLOW, bindingContext).getOrThrow()
        }

private fun KtClass.getOverriddenProperty(method: RuleMethod): Result<KtProperty> = runCatching {
    body?.properties?.filter { it.hasModifier(KtTokens.OVERRIDE_KEYWORD) }?.find {
        it.name == method.methodName
    }
            ?: throw NoSuchElementException("No overridden property '$name' found")
}

/** KDoc extension functions */
//

fun KDoc.getOrEmpty(): String =
        text?.let { text ->
            text.lines()
                    .map { it.trim().removePrefix("*").trim() }
                    .filter { it.isNotEmpty() && it != "/" } // Add filter for lone "/"
                    .joinToString("\n")
                    .removePrefix("/**")
                    .removeSuffix("*/")
                    .trim()
        }
                ?: ""

/** KtParameter extension functions */
//

private fun KtParameter.getClassWithSuperType(
        superTypeRef: (KtClass) -> Boolean,
        bindingContext: BindingContext
): Result<KtClass> = runCatching {
    typeReference?.let { typeRef ->
        typeRef.resolveToKtClass(bindingContext)
                .map { resolvedClass ->
                    if (superTypeRef(resolvedClass)) resolvedClass
                    else throw NoSuchElementException("Class is not a ServiceRequest")
                }
                .getOrThrow()
    }
            ?: throw NoSuchElementException("No type reference found")
}

fun KtParameter.getKDocOrEmpty(): String = docComment?.getOrEmpty() ?: ""

/** KtTypeReference extension functions */
//

private fun KtTypeReference.resolveToKtClass(bindingContext: BindingContext): Result<KtClass> =
        runCatching {
            bindingContext.get(BindingContext.TYPE, this)?.constructor?.declarationDescriptor?.let {
                DescriptorToSourceUtils.getSourceFromDescriptor(it) as? KtClass
            }
                    ?: throw NoSuchElementException("Could not resolve type reference to KtClass")
        }

/** KtReferenceExpression extension functions */
//

private fun KtReferenceExpression.resolveToKtClass(
        bindingContext: BindingContext
): Result<KtClass> = runCatching {
    bindingContext.getType(this)?.constructor?.declarationDescriptor?.let {
        DescriptorToSourceUtils.getSourceFromDescriptor(it) as? KtClass
    }
            ?: throw NoSuchElementException("Could not resolve reference expression to KtClass")
}

/** KtProperty extension functions */
//

private fun KtProperty.getLambdaBlock(): Result<KtBlockExpression> = runCatching {
    (initializer as? KtLambdaExpression)?.bodyExpression
            ?: throw NoSuchElementException("No lambda block found in property")
}

private fun KtProperty.streamRuleElements(
        superType: RuleSuperType,
        bindingContext: BindingContext
): Result<Sequence<KtElement>> = runCatching {
    getLambdaBlock()
            .map { block ->
                block.children.asSequence().filterIsInstance<KtElement>().flatMap { element ->
                    sequence {
                        when (element) {
                            is KtProperty -> {
                                // Yield KDoc from property
                                element.children.filterIsInstance<KDoc>().forEach {
                                    yield(it as KtElement)
                                }
                            }
                            is KDoc -> yield(element)
                            is KtDotQualifiedExpression -> {
                                (element.receiverExpression as? KtReferenceExpression)
                                        ?.resolveToKtClass(bindingContext)
                                        ?.map { resolvedClass ->
                                            if (resolvedClass.isSubTypeOf(superType)) {
                                                element.receiverExpression
                                            } else null
                                        }
                                        ?.getOrNull()
                                        ?.let { yield(it) }
                            }
                        }
                    }
                }
            }
            .getOrThrow()
}
