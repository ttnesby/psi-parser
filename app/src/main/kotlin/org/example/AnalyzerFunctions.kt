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
            batch.mapNotNull { file ->
                getRuleService(file, bindingContext).getOrNull().also {
                    (file as PsiFileImpl).clearCaches()
                }
            }
        }

fun getRuleService(ktFile: KtFile, bindingContext: BindingContext): Result<RuleServiceDoc> =
        ktFile.getClassOfSuperType(KtClass::isRuleServiceClass).map { klass ->
            newRuleServiceDoc(klass, bindingContext, ktFile.name)
        }

private fun newRuleServiceDoc(
        klass: KtClass,
        bindingContext: BindingContext,
        filePath: String
): RuleServiceDoc =
        RuleServiceDoc(
                navn = klass.name ?: "anonymous",
                beskrivelse = klass.getKDocOrEmpty(),
                inndata = getRequestFields(klass, bindingContext),
                utdata = getResponseFields(klass, bindingContext),
                // flyt = analyzeRuleServiceMethod(klass, bindingContext),
                gitHubUri = URI("$filePath")
        )

fun getRequestFields(ktClass: KtClass, bindingContext: BindingContext): List<PropertyDoc> =
        ktClass.getServiceRequestInfo(bindingContext)
                .map { (parameter, serviceRequestClass) ->
                    buildList {
                        add(PropertyDoc.fromParameter(parameter, ktClass))
                        addAll(
                                serviceRequestClass.primaryConstructor?.let {
                                    PropertyDoc.fromPrimaryConstructor(it)
                                }
                                        ?: emptyList()
                        )
                    }
                }
                .getOrDefault(emptyList())

fun getResponseFields(ktClass: KtClass, bindingContext: BindingContext): List<PropertyDoc> =
        ktClass.getServiceResponseClass(bindingContext)
                .map { serviceResponseClass ->
                    buildList {
                        add(
                                PropertyDoc.new(
                                        serviceResponseClass.name!!,
                                        serviceResponseClass.name!!,
                                        ""
                                )
                        )
                        addAll(
                                serviceResponseClass.primaryConstructor?.let {
                                    PropertyDoc.fromPrimaryConstructor(it)
                                }
                                        ?: emptyList()
                        )
                    }
                }
                .getOrDefault(emptyList())

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
