package org.example

import java.net.URI
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext

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
        ktFile.getClassOfSuperType(KtClass::isRuleServiceClass)
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
                // flyt = analyzeRuleServiceMethod(klass, bindingContext),
                gitHubUri = URI("$filePath")
        )

// Request fields analysis
fun analyzeRequestFields(ktClass: KtClass, bindingContext: BindingContext): List<PropertyDoc> =
        ktClass.primaryConstructor?.valueParameters
                ?.mapNotNull { parameter ->
                    parameter
                            .getServiceRequestClass(bindingContext)
                            .map { resolvedClass -> parameter to resolvedClass }
                            .orElse(null)
                }
                ?.flatMap { (parameter, resolvedClass) ->
                    extractParameterAndProperties(parameter, resolvedClass, ktClass, bindingContext)
                }
                ?: emptyList()

private fun extractParameterAndProperties(
        parameter: KtParameter,
        resolvedClass: KtClass,
        ktClass: KtClass,
        bindingContext: BindingContext
): List<PropertyDoc> = buildList {
    add(createParameterDoc(parameter, ktClass))
    addAll(extractTypeProperties(resolvedClass, bindingContext))
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
                    beskrivelse = param.getKDocOrEmpty()
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
        ktClass.getServiceResponseClass(bindingContext)
                .map { resolvedClass ->
                    extractResponseProperties(resolvedClass, ktClass, bindingContext)
                }
                .getOrDefault(emptyList()) // or handle error case differently if needed

private fun extractResponseProperties(
        resolvedClass: KtClass,
        ktClass: KtClass,
        bindingContext: BindingContext
): List<PropertyDoc> = buildList {
    add(createResponseClassDoc(resolvedClass, ktClass))
    addAll(extractTypeProperties(resolvedClass, bindingContext))
}

private fun createResponseClassDoc(resolvedClass: KtClass, ktClass: KtClass): PropertyDoc =
        PropertyDoc(
                navn = resolvedClass.name ?: "",
                type = resolvedClass.name ?: "Unknown",
                beskrivelse = resolvedClass.getKDocOrEmpty()
        )

// fun analyzeRuleServiceMethod(ktClass: KtClass, bindingContext: BindingContext): FlowElement.Flow
// {
//     val ruleServiceLambda =
//             ktClass.body
//                     ?.declarations
//                     ?.filterIsInstance<KtProperty>()
//                     ?.find { it.name == "ruleService" }
//                     ?.initializer as?
//                     KtLambdaExpression
//                     ?: return FlowElement.Flow(emptyList())

//     val references =
//             ruleServiceLambda.bodyExpression?.children?.mapNotNull { child ->
//                 when (child) {
//                     is KtProperty -> {
//                         // Get KDoc from property, return null if no KDoc exists
//                         val kdocText =
//                                 child.children
//                                         .filterIsInstance<KDoc>()
//                                         .map { it.text.trimIndent() }
//                                         .takeIf { it.isNotEmpty() } // Only process if KDoc
// exists
//                                         ?.joinToString()

//                         // Only create Documentation reference if KDoc exists
//                         kdocText?.let { FlowReference.Documentation(it) }
//                     }
//                     is KDoc -> {
//
// FlowReference.Documentation(child.getDefaultSection().getContent().trim())
//                     }
//                     is KtDotQualifiedExpression -> {
//                         processFlowExpression(child, bindingContext)
//                     }
//                     else -> null
//                 }
//             }
//                     ?: emptyList()

//     val safeReferences = references.map { ref -> FlowElement.Reference(ref) }

//     return FlowElement.Flow(safeReferences)
// }

// private fun processFlowExpression(
//         expression: KtDotQualifiedExpression,
//         bindingContext: BindingContext
// ): FlowReference? {
//     val referenceExpression = expression.receiverExpression as? KtReferenceExpression ?: return
// null

//     // Get the KotlinType of the expression
//     val type = bindingContext.getType(referenceExpression) ?: return null

//     // Access the declaration descriptor from the type's constructor
//     val descriptor = type.constructor.declarationDescriptor ?: return null

//     // Convert the descriptor to a KtClass
//     val typeDeclaration =
//             DescriptorToSourceUtils.getSourceFromDescriptor(descriptor) as? KtClass ?: return
// null

//     // Check if the class has 'AbstractPensjonRuleflow' as a superclass
//     val isRuleflow =
//             typeDeclaration.superTypeListEntries
//                     .mapNotNull { it.typeReference?.resolveToKtClass(bindingContext) }
//                     .any { it.isRuleflowClass() }

//     if (!isRuleflow) return null

//     val name = referenceExpression.text
//     val file =
//             File(
//                     typeDeclaration.containingFile.virtualFile?.path
//                             ?: typeDeclaration.containingFile.name
//             )

//     return FlowReference.RuleFlow(name, file)
// }
