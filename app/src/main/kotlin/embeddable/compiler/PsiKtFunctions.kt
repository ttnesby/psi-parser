package embeddable.compiler

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import pensjon.regler.FlowElement
import pensjon.regler.PropertyInfo
import rule.dsl.DSLType
import rule.dsl.DSLTypeAbstract
import rule.dsl.DSLTypeService
import rule.dsl.DSLTypeService.REQUEST
import java.io.File

///////////////////////////////////////////////////
/** KtFile extension functions */
///////////////////////////////////////////////////

fun KtFile.findDSLTypeAbstract(): Result<Pair<KtClass, DSLTypeAbstract>?> = runCatching {
    val firstKtClass = declarations.filterIsInstance<KtClass>().firstOrNull()
        ?: return@runCatching null

    val matchingDslType = findMatchingDSLTypeAbstract(firstKtClass)
        ?: return@runCatching null

    Pair(firstKtClass, matchingDslType)
}

private fun findMatchingDSLTypeAbstract(ktClass: KtClass): DSLTypeAbstract? {
    return DSLTypeAbstract.entries.firstOrNull { dslType ->
        ktClass.isSubClassOf(dslType)
    }
}


///////////////////////////////////////////////////
/** KDoc extension functions */
///////////////////////////////////////////////////

fun KDoc.formatOrEmpty(): String =
    text?.lines()?.map { it.trim().removePrefix("*").trim() }?.filter { it.isNotEmpty() && it != "/" }
        ?.joinToString("\n")?.removePrefix("/**")?.removeSuffix("*/")?.trim() ?: ""


///////////////////////////////////////////////////
/** KtClass extension functions */
///////////////////////////////////////////////////

fun KtClass.getKDocOrEmpty(): String = docComment?.formatOrEmpty() ?: ""

private fun KtClass.isSubClassOf(type: DSLTypeAbstract): Boolean =
    superTypeListEntries.any { it.typeReference?.text?.contains(type.typeName) == true }

fun KtClass.isSubClassOf(type: DSLTypeService): Boolean =
    superTypeListEntries.any { it.typeReference?.text?.contains(type.typeName) == true }


///////////////////////////////////////////////////
/** KtPrimaryConstructor extension functions */
///////////////////////////////////////////////////

fun KtPrimaryConstructor.findDSLTypeServiceRequest(
    bindingContext: BindingContext
): Result<Pair<KtParameter, KtClass>> = runCatching {
    valueParameters
        .firstNotNullOfOrNull { parameter ->
            parameter
                .typeReference
                ?.resolveToKtClass(bindingContext)
                ?.getOrNull()
                ?.let { resolvedClass ->
                    if (resolvedClass.isSubClassOf(REQUEST)) {
                        Pair(parameter, resolvedClass)
                    } else {
                        null
                    }
                }
        }
        ?: throw NoSuchElementException(
            String.format(
                "No service request parameter found in primary constructor for %s [%s]",
                containingClass()?.name,
                containingKtFile.name
            )
        )
}

fun KtPrimaryConstructor.toPropertyInfo(): List<PropertyInfo> =
    valueParameters.map { parameter ->
        PropertyInfo(
            navn = parameter.name ?: "",
            type = parameter.typeReference?.text ?: "Unknown",
            beskrivelse = parameter.getKDocOrEmpty()
        )
    }


///////////////////////////////////////////////////
/** KtParameter extension functions */
///////////////////////////////////////////////////

fun KtParameter.getKDocOrEmpty(): String = docComment?.formatOrEmpty() ?: ""

fun KtParameter.toPropertyInfo(): PropertyInfo = PropertyInfo(
    navn = name ?: "",
    type = typeReference?.text ?: "Unknown",
    beskrivelse = "Parameter in primary constructor of ${containingClass()?.name}"
)


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


///////////////////////////////////////////////////
/** KtProperty extension functions */
///////////////////////////////////////////////////

fun KtProperty.getLambdaBlock(): Result<KtBlockExpression> = runCatching {
    (initializer as? KtLambdaExpression)?.bodyExpression
        ?: throw NoSuchElementException("No lambda block found in property")
}

fun List<KtProperty>.toPropertyInfo(): List<PropertyInfo> = map { property ->
    PropertyInfo(
        navn = property.name!!,
        type = property.typeReference?.text ?: "Unknown",
        beskrivelse = property.children.filterIsInstance<KDoc>().firstOrNull()?.formatOrEmpty() ?: ""
    )
}
///////////////////////////////////////////////////
/** KtCallExpression extension functions */
///////////////////////////////////////////////////

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


///////////////////////////////////////////////////
/** KtBlockExpression extension functions */
///////////////////////////////////////////////////

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

