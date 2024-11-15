package org.example

import java.io.File
import java.net.URI
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.jetbrains.kotlin.resolve.calls.util.getType

fun analyzeSourceFilesForRuleServices(
        sourceFiles: List<KtFile>,
        bindingContext: BindingContext
): List<RuleServiceDoc> =
        sourceFiles.chunked(100).flatMap { batch ->
            batch.flatMap { file ->
                analyzeRuleService(file, bindingContext).also {
                    (file as PsiFileImpl).clearCaches()
                }
            }
        }

fun analyzeRuleService(ktFile: KtFile, bindingContext: BindingContext): List<RuleServiceDoc> =
        ktFile.declarations
                .asSequence()
                .filterIsInstance<KtClass>()
                .filter(::isRuleServiceClass)
                .map { createRuleServiceDoc(it, bindingContext, ktFile.name) }
                .toList()

private fun isRuleServiceClass(klass: KtClass): Boolean =
        klass.getSuperTypeListEntries().any {
            it.typeReference?.text?.contains("AbstractPensjonRuleService") == true
        }

private fun createRuleServiceDoc(
        klass: KtClass,
        bindingContext: BindingContext,
        filePath: String
): RuleServiceDoc =
        RuleServiceDoc(
                navn = klass.name ?: "anonymous",
                beskrivelse =
                        klass.docComment?.let { kdoc -> kdoc.getDefaultSection().getContent() }
                                ?: "",
                inndata = analyzeRequestFields(klass, bindingContext),
                utdata = analyzeResponseFields(klass, bindingContext),
                flyt = analyzeRuleServiceMethod(klass, bindingContext),
                gitHubUri = URI("$filePath")
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
): List<PropertyDoc> =
        parameter.typeReference?.let { typeRef ->
            typeRef.resolveToKtClass(bindingContext)?.takeIf { isServiceRequestClass(it) }?.let {
                    resolvedClass ->
                buildList {
                    add(createParameterDoc(parameter, ktClass))
                    addAll(extractTypeProperties(resolvedClass, bindingContext))
                }
            }
        }
                ?: emptyList()

private fun isServiceRequestClass(klass: KtClass): Boolean =
        klass.getSuperTypeListEntries().any {
            it.typeReference?.text?.contains("ServiceRequest") == true
        }

private fun createParameterDoc(parameter: KtParameter, ktClass: KtClass): PropertyDoc =
        PropertyDoc(
                navn = parameter.name ?: "",
                type = parameter.typeReference?.text ?: "Unknown",
                beskrivelse = "Parameter of ${ktClass.name}"
        )

private fun extractTypeProperties(
        declaration: KtClass,
        _bindingContext: BindingContext // in case of recursvity of custom types
): List<PropertyDoc> =
        declaration.primaryConstructor?.valueParameters?.map { param ->
            PropertyDoc(
                    navn = param.name ?: "",
                    type = param.typeReference?.text ?: "Unknown",
                    beskrivelse = param.docComment?.text ?: ""
            )
        }
                ?: emptyList()

private fun createPropertyDoc(property: KtProperty): PropertyDoc =
        PropertyDoc(
                navn = property.name ?: "",
                type = property.typeReference?.text ?: "Unknown",
                beskrivelse = property.docComment?.text ?: ""
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
): List<PropertyDoc> =
        responseTypeRef.let { typeRef ->
            typeRef.resolveToKtClass(bindingContext)?.takeIf { isServiceResponseClass(it) }?.let {
                    resolvedClass ->
                buildList {
                    add(createResponseClassDoc(resolvedClass, responseTypeRef, ktClass))
                    addAll(extractTypeProperties(resolvedClass, bindingContext))
                }
            }
        }
                ?: emptyList()

private fun isServiceResponseClass(klass: KtClass): Boolean =
        klass.getSuperTypeListEntries().any {
            it.typeReference?.text?.contains("ServiceResponse") == true
        }

private fun createResponseClassDoc(
        declaration: KtClass,
        responseTypeRef: KtTypeReference,
        ktClass: KtClass
): PropertyDoc =
        PropertyDoc(
                navn = declaration.name ?: "",
                type = responseTypeRef.text,
                beskrivelse = declaration.docComment?.text ?: "Response for ${ktClass.name}"
        )

fun analyzeRuleServiceMethod(ktClass: KtClass, bindingContext: BindingContext): FlowElement.Flow {
    // Get the ruleService property
    val ruleServiceLambda =
            ktClass.body
                    ?.declarations
                    ?.filterIsInstance<KtProperty>()
                    ?.find { it.name == "ruleService" }
                    ?.initializer as?
                    KtLambdaExpression
                    ?: return FlowElement.Flow(emptyList()) // or appropriate default

    // Process the lambda body
    val references =
            ruleServiceLambda.bodyExpression?.children?.mapNotNull { child ->
                when (child) {
                    is KDoc -> {
                        FlowReference.Documentation(child.getDefaultSection().getContent().trim())
                    }
                    is KtDotQualifiedExpression -> {
                        processFlowExpression(child, bindingContext)
                    }
                    else -> null
                }
            }
                    ?: emptyList()

    return FlowElement.Flow(references as List<FlowElement.Reference>)
}

private fun processFlowExpression(
        expression: KtDotQualifiedExpression,
        bindingContext: BindingContext
): FlowReference? {
    // Get the type of the receiver (left side of the dot)
    val receiverType = expression.receiverExpression.getType(bindingContext)

    // Check if it's a subtype of AbstractPensjonRuleflow
    val isRuleFlow =
            receiverType?.let { type ->
                type.constructor.supertypes.any {
                    it.toString().contains("AbstractPensjonRuleflow")
                }
            }
                    ?: false

    if (!isRuleFlow) return null

    val callExpression = expression.selectorExpression as? KtCallExpression
    val name = callExpression?.calleeExpression?.text ?: return null

    // Get the containing file
    val file =
            expression.containingFile.virtualFile?.let { File(it.path) }
                    ?: File("") // or handle missing file case appropriately

    return FlowReference.RuleFlow(name, file)
}

// private fun extractKDoc(blockExpr: KtBlockExpression): List<String> =
//         blockExpr.children.flatMap { child ->
//             when (child) {
//                 is KDoc -> listOf(child.text.trimIndent())
//                 is KtProperty ->
//                         child.children.filterIsInstance<KDoc>().map { it.text.trimIndent() }
//                 else -> emptyList()
//             }
//         }

// Utility extension functions to reduce code duplication
//
private fun KtTypeReference.resolveToKtClass(bindingContext: BindingContext): KtClass? =
        bindingContext.get(BindingContext.TYPE, this)?.constructor?.declarationDescriptor?.let {
            DescriptorToSourceUtils.getSourceFromDescriptor(it) as? KtClass
        }

// Extension function to help with type resolution
// private fun KtExpression.resolveToType(bindingContext: BindingContext) =
//         bindingContext[BindingContext.EXPRESSION_TYPE_INFO, this]?.type
