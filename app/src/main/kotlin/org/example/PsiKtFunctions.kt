package org.example

import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

const val SERVICE_REQUEST_CLASS_NAME = "ServiceRequest"
const val SERVICE_RESPONSE_CLASS_NAME = "ServiceResponse"
const val RULE_SERVICE_CLASS_NAME = "AbstractPensjonRuleService"
const val RULE_FLOW_CLASS_NAME = "AbstractPensjonRuleflow"
const val RULE_SET_CLASS_NAME = "AbstractPensjonRuleSet"

/** KtFile extension functions */
//

// filter and eventually get class of given super type from KtFile
fun KtFile.getClassOfSuperType(superTypeRef: (KtClass) -> Boolean): Result<KtClass> = runCatching {
    declarations.asSequence().filterIsInstance<KtClass>().firstOrNull(superTypeRef)
            ?: throw NoSuchElementException("No class found with specified supertype")
}

/** KtClass extension functions */
//

// functions to be superTypeRef parameter in KtFile.getClassOfSuperType
fun KtClass.isRuleServiceClass(): Boolean = isSubTypeOf(RULE_SERVICE_CLASS_NAME)

fun KtClass.isRuleFlowClass(): Boolean = isSubTypeOf(RULE_FLOW_CLASS_NAME)

fun KtClass.isRuleSetClass(): Boolean = isSubTypeOf(RULE_SET_CLASS_NAME)

fun KtClass.isServiceRequestClass(): Boolean = isSubTypeOf(SERVICE_REQUEST_CLASS_NAME)

fun KtClass.isServiceResponseClass(): Boolean = isSubTypeOf(SERVICE_RESPONSE_CLASS_NAME)

private fun KtClass.isSubTypeOf(simpleName: String): Boolean =
        getSuperTypeListEntries().any { it.typeReference?.text?.contains(simpleName) == true }

fun KtClass.getKDocOrEmpty(): String = docComment?.getOrEmpty() ?: ""

data class ServiceRequestInfo(val parameter: KtParameter, val resolvedClass: KtClass)

fun KtClass.getServiceRequestInfo(bindingContext: BindingContext): Result<ServiceRequestInfo> =
        runCatching {
            val parameter =
                    primaryConstructor?.valueParameters?.firstNotNullOfOrNull { parameter ->
                        parameter
                                .getClassOfSuperType(KtClass::isServiceRequestClass, bindingContext)
                                .map { resolvedClass ->
                                    ServiceRequestInfo(parameter, resolvedClass)
                                }
                                .getOrNull()
                    }
                            ?: throw NoSuchElementException(
                                    "No ServiceRequest parameter found in primary constructor"
                            )

            parameter
        }

fun KtClass.getServiceResponseClass(bindingContext: BindingContext): Result<KtClass> =
        getClassOfSuperTypeParam(
                supertype = RULE_SERVICE_CLASS_NAME,
                classTypeRef = KtClass::isServiceResponseClass,
                bindingContext = bindingContext
        )

private fun KtClass.getClassOfSuperTypeParam(
        supertype: String, // e.g., "AbstractPensjonRuleService"
        classTypeRef: (KtClass) -> Boolean, // e.g., KtClass::isServiceResponseClass
        bindingContext: BindingContext
): Result<KtClass> = runCatching {
    getSuperTypeListEntries()
            .find { it.typeReference?.text?.contains(supertype) == true }
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

private fun KtParameter.getClassOfSuperType(
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
