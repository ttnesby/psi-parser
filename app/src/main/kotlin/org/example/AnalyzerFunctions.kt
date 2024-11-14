package org.example

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
                flyt =
                        FlowElement.RuleFlowDoc(
                                beskrivelse = "to be defined - test beskrivelse",
                                inndata = emptyList(),
                                flyt = FlowElement.Flow(elementer = emptyList()),
                                gitHubUrl = "to be defined later",
                        ),
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

fun analyzeRuleServiceMethod(
        ktClass: KtClass,
        bindingContext: BindingContext
): RuleServiceMethodDoc? =
        ktClass.body
                ?.declarations
                ?.filterIsInstance<KtNamedDeclaration>()
                ?.find { it.name == "ruleService" }
                ?.let { ruleServiceDecl ->
                    (ruleServiceDecl as? KtProperty)?.initializer as? KtLambdaExpression
                }
                ?.bodyExpression
                ?.let { blockExpr ->
                    RuleServiceMethodDoc(
                            kdoc = extractKDoc(blockExpr),
                            flows = extractFlowCalls(blockExpr, bindingContext)
                    )
                }
/**
 * Extracts the KDoc comments from the block expression BE AWARE of that KDoc can be attached to
 * properties as well, and several other places Other places for KDoc is unknwon at the moment
 */
private fun extractKDoc(blockExpr: KtBlockExpression): List<String> =
        blockExpr.children.flatMap { child ->
            when (child) {
                is KDoc -> listOf(child.text.trimIndent())
                is KtProperty ->
                        child.children.filterIsInstance<KDoc>().map { it.text.trimIndent() }
                else -> emptyList()
            }
        }

// TODO: Implement flow call extraction
private fun extractFlowCalls(
        _blockExpr: KtBlockExpression,
        _bindingContext: BindingContext
): List<FlowCall> = emptyList()

// Utility extension functions to reduce code duplication
//
private fun KtTypeReference.resolveToKtClass(bindingContext: BindingContext): KtClass? =
        bindingContext.get(BindingContext.TYPE, this)?.constructor?.declarationDescriptor?.let {
            DescriptorToSourceUtils.getSourceFromDescriptor(it) as? KtClass
        }

// Extension function to help with type resolution
// private fun KtExpression.resolveToType(bindingContext: BindingContext) =
//         bindingContext[BindingContext.EXPRESSION_TYPE_INFO, this]?.type
