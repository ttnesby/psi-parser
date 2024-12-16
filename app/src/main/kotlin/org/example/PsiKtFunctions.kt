package org.example

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import pensjon.regler.FlowElement
import java.io.File


// TODO - hvordan splitte filen i ulike filer, basert på tema?
// resolve/`warp`
// extract
// ...

// TODO - i noen funksjoner kortsluttes det i enkelte funksjoner
// function with Result som brukes med getOrThrow i en funksjon som ikke returnerer Result
enum class DSLType(val typeName: String) {
    FORGRENING("forgrening"),
    GREN("gren"),
    FLYT("flyt");

    override fun toString(): String = typeName
}

enum class DSLTypeFlow(val typeName: String) {
    SERVICE("ruleService"),
    FLOW("ruleflow");

    override fun toString(): String = typeName
}

enum class DSLTypeService(val typeName: String) {
    REQUEST("ServiceRequest"),
    RESPONSE("ServiceResponse");

    override fun toString(): String = typeName
}


///////////////////////////////////////////////////
/** KtFile extension functions */
///////////////////////////////////////////////////

// filter and eventually get subclass of given super class from KtFile
// see test `testExtractRuleServiceKtClass` and `testExtractRuleFlowKtClass`
//
//fun KtFile.getSubClassOfSuperClass(superClassRef: (KtClass) -> Boolean): Result<KtClass> = runCatching {
//    declarations.asSequence().filterIsInstance<KtClass>().firstOrNull(superClassRef)
//        ?: throw NoSuchElementException("No class found with specified superClassRef")
//}

sealed interface DSLTypeAbstractResult {
    data class Found(val dslType: DSLTypeAbstract, val ktClass: KtClass) : DSLTypeAbstractResult
    data object NOTFound : DSLTypeAbstractResult
}

enum class DSLTypeAbstract(val typeName: String) {
    RULE_SERVICE("AbstractPensjonRuleService"),
    RULE_FLOW("AbstractPensjonRuleflow"),
    RULE_SET("AbstractPensjonRuleset");

    override fun toString(): String = typeName
}

fun KtFile.findDSLTypeAbstract(): Result<DSLTypeAbstractResult> = runCatching {
    declarations
        .filterIsInstance<KtClass>()
        .firstOrNull()
        ?.let { ktClass ->
            DSLTypeAbstract
                .entries
                .firstOrNull { dslType ->
                    ktClass.isSubClassOf(dslType)
                }
                ?.let { dslType ->
                    DSLTypeAbstractResult.Found(dslType, ktClass)
                }
                ?: DSLTypeAbstractResult.NOTFound
        } ?: DSLTypeAbstractResult.NOTFound
}

//fun KtFile.isRuleService(): Boolean {
//    return this.getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass).isSuccess
//}
//
//fun KtFile.isRuleflow(): Boolean {
//    return this.getSubClassOfSuperClass(KtClass::isSubClassOfRuleFlowClass).isSuccess
//}
//
//fun KtFile.isRuleset(): Boolean {
//    return this.getSubClassOfSuperClass(KtClass::isSubClassOfRuleSetClass).isSuccess
//}

///////////////////////////////////////////////////
/** KtClass extension functions */
///////////////////////////////////////////////////

//fun KtClass.isSubClassOfRuleServiceClass(): Boolean = isSubClassOf(DSLType.ABSTRACT_RULE_SERVICE)
//
//fun KtClass.isSubClassOfRuleFlowClass(): Boolean = isSubClassOf(DSLType.ABSTRACT_RULE_FLOW)
//
//fun KtClass.isSubClassOfRuleSetClass(): Boolean = isSubClassOf(DSLType.ABSTRACT_RULE_SET)
//
//fun KtClass.isSubClassOfServiceRequestClass(): Boolean = isSubClassOf(DSLType.SERVICE_REQUEST)
//
//fun KtClass.isSubClassOfServiceResponseClass(): Boolean = isSubClassOf(DSLType.SERVICE_RESPONSE)

private fun KtClass.isSubClassOf(type: DSLTypeAbstract): Boolean =
    superTypeListEntries.any { it.typeReference?.text?.contains(type.typeName) == true }

//private fun KtClass.isSubClassOf(type: DSLType): Boolean =
//    getSuperTypeListEntries().any { it.typeReference?.text?.contains(type.typeName) == true }

fun KtClass.isSubClassOf(type: DSLTypeService): Boolean =
    superTypeListEntries.any { it.typeReference?.text?.contains(type.typeName) == true }

// get KDoc for a KtClass, or empty string
// see test `testExtractKDoc`
// see ext. function `getOrEmpty` for KDoc
//
fun KtClass.getKDocOrEmpty(): String = docComment?.formatOrEmpty() ?: ""

//data class ServiceRequestInfo(val parameter: KtParameter, val resolvedClass: KtClass)

// eventually, get the class of the generic param to AbstractPensjonRuleService, which is a subclass
// of ServiceResponse
// see test `testGetResponseClassFromRuleServiceClass`
//fun KtClass.getServiceResponseClass(bindingContext: BindingContext): Result<KtClass> = getClassOfSuperClassParam(
//    superClassRef = DSLType.ABSTRACT_RULE_SERVICE,
//    paramSubClassOf = KtClass::isSubClassOfServiceResponseClass,
//    bindingContext = bindingContext
//)

//private fun KtClass.getClassOfSuperClassParam(
//    superClassRef: DSLType,
//    paramSubClassOf: (KtClass) -> Boolean,
//    bindingContext: BindingContext,
//): Result<KtClass> = runCatching {
//    val resolvedClass =
//        getSuperTypeListEntries().find { it.typeReference?.text?.contains(superClassRef.typeName) == true }?.typeReference?.typeElement?.typeArgumentsAsTypes?.getOrNull(
//            0
//        )?.resolveToKtClass(bindingContext)?.getOrThrow()  // Since we're in runCatching, exceptions will be handled
//            ?: throw NoSuchElementException("No type parameter found for $superClassRef")
//
//    if (paramSubClassOf(resolvedClass)) resolvedClass
//    else throw NoSuchElementException("Class is not of expected type")
//}

//fun KtClass.getRuleServiceFlow(bindingContext: BindingContext): Result<Sequence<FlowElement>> =
//    getOverriddenProperty(DSLType.RULE_SERVICE).mapCatching { property ->
//        property.streamRuleServiceElements(DSLType.ABSTRACT_RULE_FLOW, bindingContext).getOrThrow()
//    }

//fun KtClass.getRuleFlowFlow(bindingContext: BindingContext): Result<FlowElement.Flow> =
//    getOverriddenProperty(DSLType.RULE_FLOW).flatMap { it.getLambdaBlock() }
//        .flatMap { it.extractFlow(bindingContext) }

//private fun KtClass.getOverriddenProperty(method: DSLType): Result<KtProperty> = runCatching {
//    body?.properties?.filter { it.hasModifier(KtTokens.OVERRIDE_KEYWORD) }?.find {
//        it.name == method.typeName
//    } ?: throw NoSuchElementException("No overridden property '$name' found")
//}

/**
 * KDoc extension functions
 */
fun KDoc.formatOrEmpty(): String =
    text?.lines()?.map { it.trim().removePrefix("*").trim() }?.filter { it.isNotEmpty() && it != "/" }
        ?.joinToString("\n")?.removePrefix("/**")?.removeSuffix("*/")?.trim() ?: ""

///////////////////////////////////////////////////
/** KtParameter extension functions */
///////////////////////////////////////////////////

//private fun KtParameter.getSubClassOfSuperClass(
//    superClassRef: (KtClass) -> Boolean,
//    bindingContext: BindingContext,
//): Result<KtClass> = runCatching {
//
//    val resolvedClass = typeReference?.resolveToKtClass(bindingContext)?.getOrThrow()
//        ?: throw NoSuchElementException("No type reference found")
//
//    if (superClassRef(resolvedClass)) resolvedClass
//    else throw NoSuchElementException("Class is not a ServiceRequest")
//}

// get KDoc for a KtParameter, or empty string
// see test `testParameterKDocFromRequestPrimaryConstructor`
// see ext. function `getOrEmpty` for KDoc
//
fun KtParameter.getKDocOrEmpty(): String = docComment?.formatOrEmpty() ?: ""

///////////////////////////////////////////////////
/** KtElement extension functions */
///////////////////////////////////////////////////

// HIGHLY IMPORTANT: eventually resolve different types to PsiElement
// This includes a `warp` to whatever sourcefile declaring the PsiElement,
// Key point - DescriptorToSourceUtils.getSourceFromDescriptor, thanks to BindingContext
//
private fun KtElement.resolveToDeclaration(bindingContext: BindingContext): Result<PsiElement> = runCatching {
    when (
        val descriptor = when (this) {
            is KtNameReferenceExpression -> bindingContext[BindingContext.REFERENCE_TARGET, this]

            is KtTypeReference -> bindingContext.get(BindingContext.TYPE, this)?.constructor?.declarationDescriptor

            is KtReferenceExpression -> bindingContext.getType(this)?.constructor?.declarationDescriptor

            else -> throw IllegalArgumentException(
                "Unsupported element type: ${this.javaClass.simpleName} for binding context resolution"
            )
        }
    ) {
        null -> throw NoSuchElementException("Could not resolve descriptor: ${this.text} [${this.containingKtFile.name}]")
        else -> DescriptorToSourceUtils.getSourceFromDescriptor(descriptor)
            ?: throw NoSuchElementException("Could not resolve to declaration: ${this.text} [${this.containingKtFile.name}]")
    }
}

// HIGHLY IMPORTANT: eventually resolve (KtTypeReference, KtReferenceExpression) to KtClass
//
fun KtElement.resolveToKtClass(bindingContext: BindingContext): Result<KtClass> =
    resolveToDeclaration(bindingContext).map {
        it as? KtClass ?: throw NoSuchElementException("Declaration is not a KtClass")
    }

// HIGHLY IMPORTANT: eventually KtNameReferenceExpression resolve to function declaration
//
private fun KtCallExpression.resolveFunctionDeclaration(
    bindingContext: BindingContext,
): Result<Pair<String, File>> = runCatching {
    val namedReference = this.calleeExpression as? KtNameReferenceExpression ?: throw NoSuchElementException(
        "Call expression does not have a named reference"
    )

    namedReference.resolveToDeclaration(bindingContext)
        .map { declaration -> Pair(namedReference.text, File(declaration.containingFile.name)) }.getOrThrow()
}

/** KtProperty extension functions */
fun KtProperty.getLambdaBlock(): Result<KtBlockExpression> = runCatching {
    (initializer as? KtLambdaExpression)?.bodyExpression
        ?: throw NoSuchElementException("No lambda block found in property")
}

// eventually, get a sequence of FlowReference from a lambda block
// see data class `FlowReference`
// see test `testExtractSequenceFlowKtElements`
//
//fun KtProperty.streamRuleServiceElements(
//    superType: DSLTypeAbstract,
//    bindingContext: BindingContext,
//): Result<Sequence<FlowElement>> = runCatching {
//    this.getLambdaBlock().map { block ->
//        block.children.asSequence().flatMap { element ->
//            sequence {
//                when (element) {
//                    is KtCallExpression -> {
//                        element.resolveFunctionDeclaration(bindingContext)
//                            .map { (name, file) ->
//                                FlowElement.Function(
//                                    navn = name,
//                                    beskrivelse = element.extractKDocOrEmpty(),
//                                    fil = file
//                                )
//                            }.getOrNull()?.let { yield(it) }
//                    }
//
//                    is KtDotQualifiedExpression -> {
//                        element.resolveReceiverClass(superType, bindingContext).map { resolvedClass ->
//                            FlowElement.RuleFlow(
//                                navn = resolvedClass.name ?: "Unknown",
//                                beskrivelse = element.extractKDocOrEmpty(),
//                                fil = File(resolvedClass.containingKtFile.name)
//                            )
//                        }.getOrNull()?.let { yield(it) }
//                    }
//                }
//            }
//        }
//    }.getOrThrow()
//}

/**
 * KDoc er enten et barn av PsiElementet eller ligger som et søsken-element umiddelbart før dette
 * elementet. Det lages en sekvens som starter fra forrige søsken-element og fortsetter til forrige
 * søsken-element for hver iterasjon. Filtrerer ut PsiWhiteSpace og KDoc-elementer og sekvensen stopper når et element er hverken KDoc eller PsiWhiteSpace.
 */
fun KtElement.extractKDocOrEmpty(): String =
    generateSequence(this.prevSibling) { it.prevSibling }
        .takeWhile { it is PsiWhiteSpace || it is KDoc }
        .firstOrNull { it is KDoc }?.let {
            (it as KDoc).formatOrEmpty()
        }
        ?: ""

private fun KtCallExpression.isForgrening(): Boolean =
    (calleeExpression as? KtNameReferenceExpression)?.getReferencedName() == DSLType.FORGRENING.typeName

private fun KtCallExpression.isGren(): Boolean =
    (calleeExpression as? KtNameReferenceExpression)?.getReferencedName() == DSLType.GREN.typeName

private fun KtCallExpression.isFlyt(): Boolean =
    (calleeExpression as? KtNameReferenceExpression)?.getReferencedName() == DSLType.FLYT.typeName

private fun KtCallExpression.getLambdaBlock(): Result<KtBlockExpression> = runCatching {
    // Look for lambda arguments
    if (this.lambdaArguments.isEmpty()) {
        throw IllegalStateException("No lambda arguments found in call expression")
    }

    // Get the first lambda argument
    // What if there are multiple lambda arguments?
    val lambdaArg = this.lambdaArguments.first()

    // Get the function literal (lambda) expression
    val functionLiteral = lambdaArg.getLambdaExpression() ?: throw IllegalStateException("Lambda expression not found")

    // Get the body block
    functionLiteral.bodyExpression ?: throw IllegalStateException("Lambda body is not a block expression")
}

fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> {
    return fold(
        onSuccess = { value -> transform(value) },
        onFailure = { exception -> Result.failure(exception) }
    )
}

fun KtBlockExpression.extractRuleServiceFlow(bctx: BindingContext): Result<FlowElement.Flow> = runCatching {
    FlowElement.Flow(
        children.mapNotNull { element ->
            when (element) {
                is KtCallExpression -> {
                    element.resolveFunctionDeclaration(bctx)
                        .map { (name, file) ->
                            FlowElement.Function(
                                navn = name,
                                beskrivelse = element.extractKDocOrEmpty(),
                                fil = file
                            )
                        }.getOrNull()
                }

                is KtDotQualifiedExpression -> {
                    element.resolveReceiverClass(DSLTypeAbstract.RULE_FLOW, bctx).map { resolvedClass ->
                        FlowElement.RuleFlow(
                            navn = resolvedClass.name ?: "Unknown",
                            beskrivelse = element.extractKDocOrEmpty(),
                            fil = File(resolvedClass.containingKtFile.name)
                        )
                    }.getOrNull()
                }

                else -> null
            }
        }
    )
}

// TODO - hvordan håndtere flyt/regelsett (KtDotQualifiedExpression) som er høyresiden på en property
// TODO - NB! når KDoc er relatert til flow/ruleset/function - this.children -> this.statements

//TODO - må også legge på navn til betingelse i gren: Ex betingelse("ja") { ... }, sistnevnte er allrede trukket ut
// det er ("ja") som mangler
fun KtBlockExpression.extractRuleFlowFlow(bctx: BindingContext): Result<FlowElement.Flow> = runCatching {
    FlowElement.Flow(
        children.mapNotNull { child ->
            when (child) {
                is KtCallExpression -> {
                    when {
                        child.isForgrening() -> {
                            FlowElement.Forgrening(
                                beskrivelse = child.extractKDocOrEmpty(),
                                navn = child.valueArguments.first().text.removeSurrounding("\""),
                                gren = child.getLambdaBlock().flatMap { it.extractGrener(bctx) }.getOrThrow()
                            )
                        }

                        child.isGren() -> {
                            FlowElement.Gren(
                                beskrivelse = child.extractKDocOrEmpty(),
                                betingelse = child.getLambdaBlock().flatMap { it.extractBetingelse() }.getOrThrow(),
                                flyt = child.getLambdaBlock().flatMap { it.extractRuleFlowFlow(bctx) }.getOrThrow()
                            )
                        }

                        child.isFlyt() -> {
                            child.getLambdaBlock().flatMap { it.extractRuleFlowFlow(bctx) }.getOrThrow()
                        }

                        else -> null
                    }
                }

                is KtDotQualifiedExpression -> {
                    val resolvedClass = child.resolveReceiverClass2(bctx)
                    when {
                        resolvedClass?.isSubClassOf(DSLTypeAbstract.RULE_FLOW) == true ->
                            FlowElement.RuleFlow(
                                navn = resolvedClass.name ?: "Unknown",
                                beskrivelse = child.extractKDocOrEmpty(),
                                fil = File(resolvedClass.containingKtFile.name)
                            )

                        resolvedClass?.isSubClassOf(DSLTypeAbstract.RULE_SET) == true ->
                            FlowElement.RuleSet(
                                navn = resolvedClass.name ?: "Unknown",
                                beskrivelse = child.extractKDocOrEmpty(),
                                fil = File(resolvedClass.containingKtFile.name)
                            )

                        else -> null
                    }
                }

                else -> null
            }
        })

}

/**
 * Extracts gren elements from a forgrening lambda block
 */
private fun KtBlockExpression.extractGrener(bctx: BindingContext): Result<List<FlowElement.Gren>> = runCatching {
    this.statements.mapNotNull { statement ->
        (statement as? KtCallExpression)?.let { gren ->
            FlowElement.Gren(
                gren.extractKDocOrEmpty(),
                gren.getLambdaBlock().flatMap { it.extractBetingelse() }.getOrThrow(),
                gren.getLambdaBlock().flatMap { it.extractRuleFlowFlow(bctx) }.getOrThrow()
            )
        }
    }
}

/**
 * Extracts betingelse from a gren lambda block
 */
private fun KtBlockExpression.extractBetingelse(): Result<String> = runCatching {
    this.statements.firstNotNullOfOrNull { statement ->
        (statement as KtCallExpression).getLambdaBlock().map { it.text }.getOrThrow()
    } ?: throw NoSuchElementException("No betingelse found")
}

///////////////////////////////////////////////////
/** KtDotQualifiedExpression extension functions */
///////////////////////////////////////////////////

// eventually resolve a KtDotQualifiedExpression to receiver KtClass
//
private fun KtDotQualifiedExpression.resolveReceiverClass(
    superType: DSLTypeAbstract,
    bindingContext: BindingContext,
): Result<KtClass> = runCatching {
    (receiverExpression as? KtReferenceExpression)?.resolveToKtClass(bindingContext)?.map { resolvedClass ->
        if (resolvedClass.isSubClassOf(superType)) resolvedClass
        else throw NoSuchElementException("Class is not of type ${superType.typeName}")
    }?.getOrThrow() ?: throw NoSuchElementException("Could not resolve receiver expression")
}

private fun KtDotQualifiedExpression.resolveReceiverClass2(
    bindingContext: BindingContext,
): KtClass? = (receiverExpression as? KtReferenceExpression)?.resolveToKtClass(bindingContext)?.getOrNull()

