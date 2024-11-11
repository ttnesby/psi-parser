package org.example

import org.jetbrains.kotlin.cli.jvm.compiler.*
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

fun analyzeRuleService(ktFile: KtFile, bindingContext: BindingContext): List<RuleServiceDoc> =
        ktFile.declarations
                .asSequence()
                .filterIsInstance<KtClass>()
                .filter(::isRuleServiceClass)
                .map { createRuleServiceDoc(it, bindingContext) }
                .toList()

private fun isRuleServiceClass(klass: KtClass): Boolean =
        klass.getSuperTypeListEntries().any {
            it.typeReference?.text?.contains("AbstractPensjonRuleService") == true
        }

private fun createRuleServiceDoc(klass: KtClass, bindingContext: BindingContext): RuleServiceDoc =
        RuleServiceDoc(
                navn = klass.name ?: "anonymous",
                beskrivelse = "to be defined - test beskrivelse",
                inndata = analyzeRequestFields(klass, bindingContext),
                utdata = analyzeResponseFields(klass, bindingContext)
        )

// Request fields analysis
fun analyzeRequestFields(ktClass: KtClass, bindingContext: BindingContext): List<PropertyDoc> =
        ktClass.primaryConstructor?.valueParameters?.flatMap { parameter ->
            extractParameterAndProperties(parameter, ktClass, bindingContext)
        }
                ?: emptyList()

private fun extractParameterAndProperties(
        parameter: KtParameter,
        ktClass: KtClass,
        bindingContext: BindingContext
): List<PropertyDoc> {
    val typeRef = parameter.typeReference ?: return emptyList()

    return buildList {
        add(createParameterDoc(parameter, ktClass))
        addAll(extractTypeProperties(typeRef, bindingContext))
    }
}

private fun createParameterDoc(parameter: KtParameter, ktClass: KtClass): PropertyDoc =
        PropertyDoc(
                navn = parameter.name ?: "",
                type = parameter.typeReference?.text ?: "Unknown",
                beskrivelse = "Parameter of ${ktClass.name}"
        )

private fun extractTypeProperties(
        typeRef: KtTypeReference,
        bindingContext: BindingContext
): List<PropertyDoc> {
    val declaration = typeRef.resolveToKtClass(bindingContext) ?: return emptyList()
    return declaration.body?.properties?.map(::createPropertyDoc) ?: emptyList()
}

private fun createPropertyDoc(property: KtProperty): PropertyDoc =
        PropertyDoc(
                navn = property.name ?: "",
                type = property.typeReference?.text ?: "Unknown",
                beskrivelse = property.docComment?.getText() ?: ""
        )

// Response fields analysis
fun analyzeResponseFields(ktClass: KtClass, bindingContext: BindingContext): List<PropertyDoc> =
        findResponseType(ktClass)?.let { responseTypeRef ->
            extractResponseTypeProperties(responseTypeRef, ktClass, bindingContext)
        }
                ?: emptyList()

private fun findResponseType(ktClass: KtClass): KtTypeReference? =
        ktClass.getSuperTypeListEntries()
                .find { it.typeReference?.text?.contains("AbstractPensjonRuleService") == true }
                ?.typeReference
                ?.typeElement
                ?.typeArgumentsAsTypes
                ?.getOrNull(0)

private fun extractResponseTypeProperties(
        responseTypeRef: KtTypeReference,
        ktClass: KtClass,
        bindingContext: BindingContext
): List<PropertyDoc> {
    val declaration = responseTypeRef.resolveToKtClass(bindingContext) ?: return emptyList()

    return buildList {
        add(createResponseClassDoc(declaration, responseTypeRef, ktClass))
        addAll(extractClassProperties(declaration))
    }
}

private fun createResponseClassDoc(
        declaration: KtClass,
        responseTypeRef: KtTypeReference,
        ktClass: KtClass
): PropertyDoc =
        PropertyDoc(
                navn = declaration.name ?: "",
                type = responseTypeRef.text,
                beskrivelse = declaration.docComment?.getText() ?: "Response for ${ktClass.name}"
        )

private fun extractClassProperties(declaration: KtClass): List<PropertyDoc> =
        declaration.body?.properties?.map { prop ->
            PropertyDoc(
                    navn = prop.name ?: "",
                    type = prop.typeReference?.text ?: "Unknown",
                    beskrivelse = prop.docComment?.getText() ?: ""
            )
        }
                ?: emptyList()

// Utility extension function to reduce code duplication
private fun KtTypeReference.resolveToKtClass(bindingContext: BindingContext): KtClass? =
        bindingContext.get(BindingContext.TYPE, this)?.constructor?.declarationDescriptor?.let {
            DescriptorToSourceUtils.getSourceFromDescriptor(it) as? KtClass
        }
