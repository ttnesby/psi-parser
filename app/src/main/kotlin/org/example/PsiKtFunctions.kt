package org.example

import java.util.Optional
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

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
fun KtClass.isRuleServiceClass(): Boolean = isSubTypeOf("AbstractPensjonRuleService")

fun KtClass.isRuleFlowClass(): Boolean = isSubTypeOf("AbstractPensjonRuleflow")

fun KtClass.isRuleSetClass(): Boolean = isSubTypeOf("AbstractPensjonRuleSet")

fun KtClass.isServiceRequestClass(): Boolean = isSubTypeOf("ServiceRequest")

fun KtClass.isServiceResponseClass(): Boolean = isSubTypeOf("ServiceResponse")

private fun KtClass.isSubTypeOf(simpleName: String): Boolean =
        getSuperTypeListEntries().any { it.typeReference?.text?.contains(simpleName) == true }

fun KtClass.getKDocOrEmpty(): String = docComment?.getOrEmpty() ?: ""

fun KtClass.getServiceResponseClass(bindingContext: BindingContext): Result<KtClass> = runCatching {
    getSuperTypeListEntries()
            .find { it.typeReference?.text?.contains("AbstractPensjonRuleService") == true }
            ?.typeReference
            ?.typeElement
            ?.typeArgumentsAsTypes
            ?.getOrNull(0)
            ?.getServiceResponseClass(bindingContext)
            ?.getOrThrow()
            ?: throw NoSuchElementException("No ServiceResponse type found")
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

fun KtParameter.getServiceRequestClass(bindingContext: BindingContext): Optional<KtClass> =
        Optional.ofNullable(typeReference).flatMap { typeRef ->
            typeRef.resolveToKtClass(bindingContext)
                    .map { resolvedClass ->
                        if (resolvedClass.isServiceRequestClass()) Optional.of(resolvedClass)
                        else Optional.empty()
                    }
                    .getOrElse { Optional.empty() }
        }

fun KtParameter.getKDocOrEmpty(): String = docComment?.getOrEmpty() ?: ""

/** KtTypeReference extension functions */
//

fun KtTypeReference.resolveToKtClass(bindingContext: BindingContext): Result<KtClass> =
        runCatching {
            bindingContext.get(BindingContext.TYPE, this)?.constructor?.declarationDescriptor?.let {
                DescriptorToSourceUtils.getSourceFromDescriptor(it) as? KtClass
            }
                    ?: throw NoSuchElementException("Could not resolve type reference to KtClass")
        }

fun KtTypeReference.getServiceResponseClass(bindingContext: BindingContext): Result<KtClass> =
        resolveToKtClass(bindingContext).map { resolvedClass ->
            if (resolvedClass.isServiceResponseClass()) resolvedClass
            else throw NoSuchElementException("Class is not a ServiceResponse")
        }
