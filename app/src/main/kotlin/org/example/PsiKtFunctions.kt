package org.example

import java.io.File
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

// type safe way of representing rule super classes
enum class RuleSuperClass(val className: String) {
    SERVICE_REQUEST("ServiceRequest"),
    SERVICE_RESPONSE("ServiceResponse"),
    RULE_SERVICE("AbstractPensjonRuleService"),
    RULE_FLOW("AbstractPensjonRuleflow"),
    RULE_SET("AbstractPensjonRuleSet");

    companion object {
        fun fromClassName(className: String): RuleSuperClass? =
                values().find { it.className == className }
    }

    override fun toString(): String = className
}

// type safe way of representing rule methods
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

// filter and eventually get subclass of given super class from KtFile
// see test `testExtractRuleServiceKtClass` and `testExtractRuleFlowKtClass`
//
fun KtFile.getSubClassOfSuperClass(superClassRef: (KtClass) -> Boolean): Result<KtClass> =
        runCatching {
            declarations.asSequence().filterIsInstance<KtClass>().firstOrNull(superClassRef)
                    ?: throw NoSuchElementException("No class found with specified superClassRef")
        }

/** KtClass extension functions */
//

fun KtClass.isSubClassOfRuleServiceClass(): Boolean = isSubClassOf(RuleSuperClass.RULE_SERVICE)

fun KtClass.isSubClassOfRuleFlowClass(): Boolean = isSubClassOf(RuleSuperClass.RULE_FLOW)

fun KtClass.isSubClassOfRuleSetClass(): Boolean = isSubClassOf(RuleSuperClass.RULE_SET)

fun KtClass.isSubClassOfServiceRequestClass(): Boolean =
        isSubClassOf(RuleSuperClass.SERVICE_REQUEST)

fun KtClass.isSubClassOfServiceResponseClass(): Boolean =
        isSubClassOf(RuleSuperClass.SERVICE_RESPONSE)

private fun KtClass.isSubClassOf(type: RuleSuperClass): Boolean =
        getSuperTypeListEntries().any { it.typeReference?.text?.contains(type.className) == true }

// get KDoc for a KtClass, or empty string
// see test `testExtractKDoc`
// see ext. function `getOrEmpty` for KDoc
//
fun KtClass.getKDocOrEmpty(): String = docComment?.getOrEmpty() ?: ""

data class ServiceRequestInfo(val parameter: KtParameter, val resolvedClass: KtClass)

// eventually, get 1th param of a subclass of ServiceRequest
// see test `testGetRequestClassFromRuleServiceClass`
//
fun KtClass.getServiceRequestInfo(bindingContext: BindingContext): Result<ServiceRequestInfo> =
        runCatching {
            primaryConstructor?.valueParameters?.firstNotNullOfOrNull { parameter ->
                parameter
                        .getSubClassOfSuperClass(
                                KtClass::isSubClassOfServiceRequestClass,
                                bindingContext
                        )
                        .map { resolvedClass -> ServiceRequestInfo(parameter, resolvedClass) }
                        .getOrNull()
            }
                    ?: throw NoSuchElementException(
                            "No ServiceRequest parameter found in primary constructor"
                    )
        }

// eventually, get the class of the generic param to AbstractPensjonRuleService, which is a subclass
// of ServiceResponse
// see test `testGetResponseClassFromRuleServiceClass`
//
fun KtClass.getServiceResponseClass(bindingContext: BindingContext): Result<KtClass> =
        getClassOfSuperClassParam(
                superClassRef = RuleSuperClass.RULE_SERVICE,
                paramSubClassOf = KtClass::isSubClassOfServiceResponseClass,
                bindingContext = bindingContext
        )

private fun KtClass.getClassOfSuperClassParam(
        superClassRef: RuleSuperClass,
        paramSubClassOf: (KtClass) -> Boolean,
        bindingContext: BindingContext
): Result<KtClass> = runCatching {
    getSuperTypeListEntries()
            .find { it.typeReference?.text?.contains(superClassRef.className) == true }
            ?.typeReference
            ?.typeElement
            ?.typeArgumentsAsTypes
            ?.getOrNull(0)
            ?.resolveToKtClass(bindingContext)
            ?.map { resolvedClass ->
                if (paramSubClassOf(resolvedClass)) resolvedClass
                else throw NoSuchElementException("Class is not of expected type")
            }
            ?.getOrThrow()
            ?: throw NoSuchElementException("No type parameter found for $superClassRef")
}

fun KtClass.getRuleServiceFlow(bindingContext: BindingContext): Result<Sequence<FlowReference>> =
        getOverriddenProperty(RuleMethod.RULE_SERVICE).mapCatching { property ->
            property.streamRuleElements(RuleSuperClass.RULE_FLOW, bindingContext).getOrThrow()
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

private fun KtParameter.getSubClassOfSuperClass(
        superClassRef: (KtClass) -> Boolean,
        bindingContext: BindingContext
): Result<KtClass> = runCatching {
    typeReference?.let { typeRef ->
        typeRef.resolveToKtClass(bindingContext)
                .map { resolvedClass ->
                    if (superClassRef(resolvedClass)) resolvedClass
                    else throw NoSuchElementException("Class is not a ServiceRequest")
                }
                .getOrThrow()
    }
            ?: throw NoSuchElementException("No type reference found")
}

// get KDoc for a KtParameter, or empty string
// see test `testParameterKDocFromRequestPrimaryConstructor`
// see ext. function `getOrEmpty` for KDoc
//
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
        superType: RuleSuperClass,
        bindingContext: BindingContext
): Result<Sequence<FlowReference>> = runCatching {
    getLambdaBlock()
            .map { block ->
                block.children.asSequence().flatMap { element ->
                    sequence {
                        when (element) {
                            is KtProperty -> {
                                element.children.filterIsInstance<KDoc>().forEach {
                                    yield(FlowReference.Documentation(it.getOrEmpty()))
                                }
                            }
                            is KDoc -> yield(FlowReference.Documentation(element.getOrEmpty()))
                            is KtDotQualifiedExpression -> {
                                element.resolveReceiverClass(superType, bindingContext)
                                        .map { resolvedClass ->
                                            FlowReference.RuleFlow(
                                                    resolvedClass.name ?: "Unknown",
                                                    File(resolvedClass.containingKtFile.name)
                                            )
                                        }
                                        .getOrNull()
                                        ?.let { yield(it) }
                            }
                        }
                    }
                }
            }
            .getOrThrow()
}

/** KtDotQualifiedExpression extension functions */
//

private fun KtDotQualifiedExpression.resolveReceiverClass(
        superType: RuleSuperClass,
        bindingContext: BindingContext
): Result<KtClass> = runCatching {
    (receiverExpression as? KtReferenceExpression)
            ?.resolveToKtClass(bindingContext)
            ?.map { resolvedClass ->
                if (resolvedClass.isSubClassOf(superType)) resolvedClass
                else throw NoSuchElementException("Class is not of type ${superType.className}")
            }
            ?.getOrThrow()
            ?: throw NoSuchElementException("Could not resolve receiver expression")
}
