package org.example

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import java.io.File

// type safe way of representing rule super classes
enum class RuleSuperClass(val className: String) {
    SERVICE_REQUEST("ServiceRequest"),
    SERVICE_RESPONSE("ServiceResponse"),
    RULE_SERVICE("AbstractPensjonRuleService"),
    RULE_FLOW("AbstractPensjonRuleflow"),
    RULE_SET("AbstractPensjonRuleset");

    companion object {
        fun fromClassName(className: String): RuleSuperClass? =
            values().find { it.className == className }
    }

    override fun toString(): String = className
}

// type safe way of representing rule methods
enum class RuleMethod(val methodName: String) {
    RULE_SERVICE("ruleService"),
    RULE_FLOW("ruleflow");

    companion object {
        fun fromClassName(methodName: String): RuleMethod? =
            values().find { it.methodName == methodName }
    }

    override fun toString(): String = methodName
}

enum class DSLType(val typeName: String) {
    FORGRENING("forgrening"),
    GREN("gren"),
    FLYT("flyt");

    companion object {
        fun fromClassName(className: String): DSLType? = values().find { it.typeName == className }
    }

    override fun toString(): String = typeName
}

///////////////////////////////////////////////////
/** KtFile extension functions */
///////////////////////////////////////////////////

// filter and eventually get subclass of given super class from KtFile
// see test `testExtractRuleServiceKtClass` and `testExtractRuleFlowKtClass`
//
fun KtFile.getSubClassOfSuperClass(superClassRef: (KtClass) -> Boolean): Result<KtClass> =
    runCatching {
        declarations.asSequence().filterIsInstance<KtClass>().firstOrNull(superClassRef)
            ?: throw NoSuchElementException("No class found with specified superClassRef")
    }

///////////////////////////////////////////////////
/** KtClass extension functions */
///////////////////////////////////////////////////

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
    bindingContext: BindingContext,
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

fun KtClass.getRuleServiceFlow(bindingContext: BindingContext): Result<Sequence<FlowElement>> =
    getOverriddenProperty(RuleMethod.RULE_SERVICE).mapCatching { property ->
        property.streamRuleServiceElements(RuleSuperClass.RULE_FLOW, bindingContext)
            .getOrThrow()
    }

fun KtClass.getRuleFlowFlow(bindingContext: BindingContext): Result<FlowElement.Flow> =
    getOverriddenProperty(RuleMethod.RULE_FLOW).mapCatching { property ->
//        property.streamRuleFlowElements(RuleSuperClass.RULE_FLOW, bindingContext).getOrThrow()
        property.getLambdaBlock().getOrThrow().extractFlow(bindingContext)
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
    text?.lines()
        ?.map { it.trim().removePrefix("*").trim() }
        ?.filter { it.isNotEmpty() && it != "/" }
        ?.joinToString("\n")
        ?.removePrefix("/**")
        ?.removeSuffix("*/")
        ?.trim()
        ?: ""

///////////////////////////////////////////////////
/** KtParameter extension functions */
///////////////////////////////////////////////////

private fun KtParameter.getSubClassOfSuperClass(
    superClassRef: (KtClass) -> Boolean,
    bindingContext: BindingContext,
): Result<KtClass> = runCatching {
    typeReference
        ?.resolveToKtClass(bindingContext)
        ?.map { resolvedClass ->
            if (superClassRef(resolvedClass)) resolvedClass
            else throw NoSuchElementException("Class is not a ServiceRequest")
        }
        ?.getOrThrow()
        ?: throw NoSuchElementException("No type reference found")
}

// get KDoc for a KtParameter, or empty string
// see test `testParameterKDocFromRequestPrimaryConstructor`
// see ext. function `getOrEmpty` for KDoc
//
fun KtParameter.getKDocOrEmpty(): String = docComment?.getOrEmpty() ?: ""

///////////////////////////////////////////////////
/** KtElement extension functions */
///////////////////////////////////////////////////

// HIGHLY IMPORTANT: eventually resolve different types to PsiElement
// This includes a `warp` to whatever sourcefile declaring the PsiElement,
// Key point - DescriptorToSourceUtils.getSourceFromDescriptor, thanks to BindingContext
//
private fun KtElement.resolveToDeclaration(bindingContext: BindingContext): Result<PsiElement> =
    runCatching {
        when (val descriptor =
            when (this) {
                is KtNameReferenceExpression ->
                    bindingContext[BindingContext.REFERENCE_TARGET, this]

                is KtTypeReference ->
                    bindingContext.get(BindingContext.TYPE, this)
                        ?.constructor
                        ?.declarationDescriptor

                is KtReferenceExpression ->
                    bindingContext.getType(this)
                        ?.constructor
                        ?.declarationDescriptor

                else ->
                    throw IllegalArgumentException(
                        "Unsupported element type: ${this.javaClass.simpleName}"
                    )
            }
        ) {
            null -> throw NoSuchElementException("Could not resolve descriptor ${this.text}")
            else -> DescriptorToSourceUtils.getSourceFromDescriptor(descriptor)
                ?: throw NoSuchElementException("Could not resolve to declaration")
        }
    }

// HIGHLY IMPORTANT: eventually resolve (KtTypeReference, KtReferenceExpression) to KtClass
//
private fun KtElement.resolveToKtClass(bindingContext: BindingContext): Result<KtClass> =
    resolveToDeclaration(bindingContext).map {
        it as? KtClass ?: throw NoSuchElementException("Declaration is not a KtClass")
    }

// HIGHLY IMPORTANT: eventually KtNameReferenceExpression resolve to function declaration
//
private fun KtCallExpression.resolveFunctionDeclaration(
    bindingContext: BindingContext,
): Result<Pair<String, File>> = runCatching {
    val namedReference =
        calleeExpression as? KtNameReferenceExpression
            ?: throw NoSuchElementException(
                "Call expression does not have a named reference"
            )

    namedReference
        .resolveToDeclaration(bindingContext)
        .map { declaration -> Pair(namedReference.text, File(declaration.containingFile.name)) }
        .getOrThrow()
}

/** KtProperty extension functions */
//

private fun KtProperty.getLambdaBlock(): Result<KtBlockExpression> = runCatching {
    (initializer as? KtLambdaExpression)?.bodyExpression
        ?: throw NoSuchElementException("No lambda block found in property")
}

// eventually, get a sequence of FlowReference from a lambda block
// see data class `FlowReference`
// see test `testExtractSequenceFlowKtElements`
//
private fun KtProperty.streamRuleServiceElements(
    superType: RuleSuperClass,
    bindingContext: BindingContext,
): Result<Sequence<FlowElement>> = runCatching {
    getLambdaBlock()
        .map { block ->
            block.children.asSequence().flatMap { element ->
                sequence {
                    when (element) {
                        is KtCallExpression -> {
                            element.resolveFunctionDeclaration(bindingContext)
                                .map { (name, file) -> FlowElement.Function(name, file) }
                                .getOrNull()
                                ?.let { yield(it) }
                        }

                        is KtProperty -> {
                            element.children.filterIsInstance<KDoc>().forEach {
                                yield(FlowElement.Documentation(it.getOrEmpty()))
                            }
                        }

                        is KDoc -> yield(FlowElement.Documentation(element.getOrEmpty()))
                        is KtDotQualifiedExpression -> {
                            element.resolveReceiverClass(superType, bindingContext)
                                .map { resolvedClass ->
                                    FlowElement.RuleFlow(
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

/**
 * KDoc er enten et barn av PsiElementet eller ligger som et søsken-element umiddelbart før dette
 * elementet. Det lages en sekvens som starter fra forrige søsken-element og fortsetter til forrige
 * søsken-element for hver iterasjon. Filtrerer ut PsiWhiteSpace og KDoc-elementer og sekvensen
 * stopper når et element er hverken KDoc eller PsiWhiteSpace.
 */
fun PsiElement.getKDoc(): String =
    generateSequence(this.prevSibling) { it.prevSibling }
        .takeWhile { it is PsiWhiteSpace || it is KDoc }
        .firstOrNull { it is KDoc }
        ?.let { (it as KDoc).getOrEmpty() }
        ?: ""

private fun KtCallExpression.isForgrening(): Boolean =
    (calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ==
            DSLType.FORGRENING.typeName

private fun KtCallExpression.isGren(): Boolean =
    (calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ==
            DSLType.GREN.typeName

private fun KtCallExpression.isFlyt(): Boolean =
    (calleeExpression as? KtNameReferenceExpression)?.getReferencedName() ==
            DSLType.FLYT.typeName

private fun KtCallExpression.getLambdaBlock(): Result<KtBlockExpression> = runCatching {
    // Look for lambda arguments
    val lambdaArguments = lambdaArguments
    if (lambdaArguments.isEmpty()) {
        throw IllegalStateException("No lambda arguments found in call expression")
    }

    // Get the first lambda argument
    val lambdaArg = lambdaArguments.first()

    // Get the function literal (lambda) expression
    val functionLiteral =
        lambdaArg.getLambdaExpression()
            ?: throw IllegalStateException("Lambda expression not found")

    // Get the body block
    functionLiteral.bodyExpression
        ?: throw IllegalStateException("Lambda body is not a block expression")
}

//private fun KtProperty.streamRuleFlowElements(
//    superType: RuleSuperClass,
//    bindingContext: BindingContext,
//): Result<Sequence<FlowElement>> = runCatching {
//    getLambdaBlock()
//        .map { block ->
//            block.children.asSequence().flatMap { element ->
//                sequence {
//                    when (element) {
//                        is KtCallExpression -> {
//                            when {
//                                element.isForgrening() -> {
//                                    yield(
//                                        FlowElement.Forgrening(
//                                            element.getKDoc(),
//                                            element.valueArguments
//                                                .first()
//                                                .text
//                                                .removeSurrounding("\""),
//                                            element.extractGrener()
//                                        )
//                                    )
//                                }
//
//                                element.isGren() -> {}
//                                // else -> {
//                                //     element.resolveFunctionDeclaration(bindingContext)
//                                //             .map { (name, file) -> FlowElement.Function(name,
//                                // file) }
//                                //             .getOrNull()
//                                //             ?.let { yield(it) }
//                                // }
//                            }
//                        }
//
//                        is KtProperty -> {
//                            element.children.filterIsInstance<KDoc>().forEach {
//                                yield(FlowElement.Documentation(it.getOrEmpty()))
//                            }
//                        }
//
//                        is KDoc -> yield(FlowElement.Documentation(element.getOrEmpty()))
//                        is KtDotQualifiedExpression -> {
//                            element.resolveReceiverClass(superType, bindingContext)
//                                .map { resolvedClass ->
//                                    FlowElement.RuleFlow(
//                                        resolvedClass.name ?: "Unknown",
//                                        File(resolvedClass.containingKtFile.name)
//                                    )
//                                }
//                                .getOrNull()
//                                ?.let { yield(it) }
//                        }
//                    }
//                }
//            }
//        }
//        .getOrThrow()
//}

// TODO - Usikker på om det her blir korrekt
private fun KtBlockExpression.extractFlow(bctx: BindingContext): FlowElement.Flow {
    return FlowElement.Flow(
        this.children.mapNotNull { child ->
            when (child) {
                is KtCallExpression -> {
                    when {
                        child.isForgrening() -> {
                            FlowElement.Forgrening(
                                child.getKDoc(),
                                child
                                    .valueArguments
                                    .first()
                                    .text
                                    .removeSurrounding("\""),
                                child.getLambdaBlock().getOrThrow().extractGrener(bctx)
                            )
                        }

                        child.isGren() -> {
                            FlowElement.Gren(
                                child.getKDoc(),
                                child.extractBetingelse(),
                                child.getLambdaBlock().getOrThrow().extractFlow(bctx)
                                // block.extractFlow()
                            )
                        }

                        child.isFlyt() -> {
                            child.getLambdaBlock().map { it.extractFlow(bctx) }.getOrNull()
                        }

                        else -> null
                    }
                }

                is KtProperty -> FlowElement.Documentation(child.children.filterIsInstance<KDoc>().first().getOrEmpty())
                is KDoc -> FlowElement.Documentation(child.getOrEmpty())
                is KtDotQualifiedExpression -> {
                    val resolvedClass = child.resolveReceiverClass2(bctx).getOrThrow()
                    when {
                        resolvedClass.isSubClassOfRuleFlowClass() -> FlowElement.RuleFlow(
                            resolvedClass.name ?: "Unknown",
                            File(resolvedClass.containingKtFile.name)
                        )
                        resolvedClass.isSubClassOfRuleSetClass() -> FlowElement.RuleSet(
                            resolvedClass.name ?: "Unknown",
                            File(resolvedClass.containingKtFile.name)
                        )
                        else -> null
                    }
                }

                else -> null
            }
        }
    )
}

/**
 *
 */
private fun KtBlockExpression.extractGrener(bctx : BindingContext): List<FlowElement.Gren> {
    return this.statements.map { gren ->
        FlowElement.Gren(
            gren.getKDoc(),
            gren.extractBetingelse(),
            (gren as KtCallExpression).lambdaArguments.first().getLambdaExpression()?.bodyExpression?.extractFlow(bctx) ?: FlowElement.Flow(emptyList())
        )
    }
}

private fun PsiElement.extractBetingelse(): String {
    return (this as? KtCallExpression)
        ?.lambdaArguments
        ?.firstOrNull()
        ?.getLambdaExpression()
        ?.bodyExpression
        ?.statements
        ?.firstOrNull()
        ?.let { statement ->
            (statement as? KtCallExpression)?.valueArguments?.firstOrNull()?.text
        }
        ?: ""
}
/*private fun KtProperty.streamRuleFlowElements(
        superType: RuleSuperClass,
        bindingContext: BindingContext
): Result<List<FlowElement>> = runCatching {
    getLambdaBlock()
            .map { block ->
                block.children.flatMap { element ->
                    buildList {
                        when (element) {
                            // is KtCallExpression -> {
                            //     element.resolveFunctionDeclaration(bindingContext)
                            //         .map { (name, file) -> FlowElement.Function(name, file) }
                            //         .getOrNull()
                            //         ?.let { add(it) }
                            // }
                            is KtProperty -> {
                                element.children
                                        .filterIsInstance<KDoc>()
                                        .map { FlowElement.Documentation(it.getOrEmpty()) }
                                        .forEach { add(it) }
                            }
                            is KDoc -> add(FlowElement.Documentation(element.getOrEmpty()))
                            is KtDotQualifiedExpression -> {
                                val superClass = element.resolveReceiverClass(superType, bindingContext)
                                if (superClass in listOf("${RuleSuperClass.RULE_FLOW}", "AbstractPensjonRuleSet") {
                                    add
                                    }
                                // else - we don't care about non-relevant ktdotexpression,

                                element.resolveReceiverClass(superType, bindingContext)
                                        .onSuccess { resolvedClass ->
                                            add(
                                                    FlowElement.RuleFlow(
                                                            resolvedClass.name ?: "Unknown",
                                                            File(
                                                                    resolvedClass
                                                                            .containingKtFile
                                                                            .name
                                                            )
                                                    )
                                            )
                                        }
                                        .onFailure {
                                            throw NoSuchElementException(
                                                    "Could not resolve receiver expression"
                                            )
                                        }
                                // .getOrNull()
                                // ?.let { add(it) }
                            }
                        }
                    }
                }
            }
            .getOrThrow()
}*/

///////////////////////////////////////////////////
/** KtDotQualifiedExpression extension functions */
///////////////////////////////////////////////////

// eventually resolve a KtDotQualifiedExpression to receiver KtClass
//
private fun KtDotQualifiedExpression.resolveReceiverClass(
    superType: RuleSuperClass,
    bindingContext: BindingContext,
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

private fun KtDotQualifiedExpression.resolveReceiverClass2(
    bindingContext: BindingContext,
): Result<KtClass> =
    (receiverExpression as KtReferenceExpression)
        .resolveToKtClass(bindingContext)

