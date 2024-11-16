package org.example

import java.io.File
import java.net.URI
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils

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
        ktFile.getClassOfSuperType(::isRuleServiceClass)
                .map { klass -> listOf(createRuleServiceDoc(klass, bindingContext, ktFile.name)) }
                .getOrDefault(emptyList())

private fun createRuleServiceDoc(
        klass: KtClass,
        bindingContext: BindingContext,
        filePath: String
): RuleServiceDoc =
        RuleServiceDoc(
                navn = klass.name ?: "anonymous",
                beskrivelse = klass.getKDocOrEmpty(),
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
    val ruleServiceLambda =
            ktClass.body
                    ?.declarations
                    ?.filterIsInstance<KtProperty>()
                    ?.find { it.name == "ruleService" }
                    ?.initializer as?
                    KtLambdaExpression
                    ?: return FlowElement.Flow(emptyList())

    val references =
            ruleServiceLambda.bodyExpression?.children?.mapNotNull { child ->
                when (child) {
                    is KtProperty -> {
                        // Get KDoc from property, return null if no KDoc exists
                        val kdocText =
                                child.children
                                        .filterIsInstance<KDoc>()
                                        .map { it.text.trimIndent() }
                                        .takeIf { it.isNotEmpty() } // Only process if KDoc exists
                                        ?.joinToString()

                        // Only create Documentation reference if KDoc exists
                        kdocText?.let { FlowReference.Documentation(it) }
                    }
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

    val safeReferences = references.map { ref -> FlowElement.Reference(ref) }

    return FlowElement.Flow(safeReferences)
}

private fun processFlowExpression(
        expression: KtDotQualifiedExpression,
        bindingContext: BindingContext
): FlowReference? {
    val referenceExpression = expression.receiverExpression as? KtReferenceExpression ?: return null

    // Get the KotlinType of the expression
    val type = bindingContext.getType(referenceExpression) ?: return null

    // Access the declaration descriptor from the type's constructor
    val descriptor = type.constructor.declarationDescriptor ?: return null

    // Convert the descriptor to a KtClass
    val typeDeclaration =
            DescriptorToSourceUtils.getSourceFromDescriptor(descriptor) as? KtClass ?: return null

    // Check if the class has 'AbstractPensjonRuleflow' as a superclass
    val isRuleflow =
            typeDeclaration.superTypeListEntries
                    .mapNotNull { it.typeReference?.resolveToKtClass(bindingContext) }
                    .any { it.name == "AbstractPensjonRuleflow" }

    if (!isRuleflow) return null

    val name = referenceExpression.text
    val file =
            File(
                    typeDeclaration.containingFile.virtualFile?.path
                            ?: typeDeclaration.containingFile.name
            )

    return FlowReference.RuleFlow(name, file)
}

// private fun KtTypeReference.resolveToKtClass(bindingContext: BindingContext): KtClass? =
//         bindingContext[BindingContext.TYPE, this]?.constructor?.declarationDescriptor?.let {
//             DescriptorToSourceUtils.getSourceFromDescriptor(it) as? KtClass
//         }

// Helper function to check if class is a Ruleflow
private fun isRuleflowClass(ktClass: KtClass, bindingContext: BindingContext): Boolean =
        ktClass.superTypeListEntries
                .mapNotNull { it.typeReference?.resolveToKtClass(bindingContext) }
                .any {
                    it.name == "AbstractPensjonRuleflow"
                } // or check full qualified name if needed

// private fun processFlowExpression(
//         expression: KtDotQualifiedExpression,
//         bindingContext: BindingContext
// ): FlowReference? {
//     // Get the type of the receiver (left side of the dot)
//     val receiverType = expression.receiverExpression.getType(bindingContext)

//     // Check if it's a subtype of AbstractPensjonRuleflow
//     val isRuleFlow =
//             receiverType?.let { type ->
//                 type.constructor.supertypes.any {
//                     it.toString().contains("AbstractPensjonRuleflow")
//                 }
//             }
//                     ?: false

//     if (!isRuleFlow) return null

//     val callExpression = expression.selectorExpression as? KtCallExpression
//     val name = callExpression?.calleeExpression?.text ?: return null

//     // Get the containing file
//     val file =
//             expression.containingFile.virtualFile?.let { File(it.path) }
//                     ?: File("") // or handle missing file case appropriately

//     return FlowReference.RuleFlow(name, file)
// }

// Utility extension functions to reduce code duplication
//
private fun KtTypeReference.resolveToKtClass(bindingContext: BindingContext): KtClass? =
        bindingContext.get(BindingContext.TYPE, this)?.constructor?.declarationDescriptor?.let {
            DescriptorToSourceUtils.getSourceFromDescriptor(it) as? KtClass
        }

// Extension function to help with type resolution
// private fun KtExpression.resolveToType(bindingContext: BindingContext) =
//         bindingContext[BindingContext.EXPRESSION_TYPE_INFO, this]?.type
