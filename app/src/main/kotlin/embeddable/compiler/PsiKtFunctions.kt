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
import org.jetbrains.kotlin.lexer.KtTokens
import pensjon.regler.Condition
import rule.dsl.DSLTypeAbstract.*
import rule.dsl.DSLTypeFlow

enum class ParsingExceptionType(val message: String) {
    ERROR_NO_PRIMARY_CONSTRUCTOR("No primary constructor found for %s [%s]"),
    ERROR_NO_SERVICE_REQUEST_PARAMETER("No service request parameter found in primary constructor for %s [%s]"),
    ERROR_NO_SERVICE_RESPONSE_TYPE("No service response type found for %s [%s]"),
    ERROR_NOT_SUBCLASS_OF_SERVICE("%s is not subclass of %s [%s]"),
    ERROR_NO_FLOW_PARAMETER("No flow parameter of type class found in primary constructor for %s [%s]"),
    ERROR_NO_PROPERTIES_FOUND("No properties found for %s [%s]"),
    ERROR_NO_OVERRIDE_FUNCTION("No override function %s found for %s [%s]"),
    ERROR_FORGRENING_NAME_MISSING("No name found for forgrening for %s [%s]"),
    ERROR_NO_BETINGELSE_FOUND("No betingelse found for gren for %s [%s]"),;
}

private fun bail(message: String): NoSuchElementException = NoSuchElementException(message)

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

private fun KDoc.formatOrEmpty(): String =
    text?.lines()?.map { it.trim().removePrefix("*").trim() }?.filter { it.isNotEmpty() && it != "/" }
        ?.joinToString("\n")?.removePrefix("/**")?.removeSuffix("*/")?.trim() ?: ""


///////////////////////////////////////////////////
/** KtClass extension functions */
///////////////////////////////////////////////////

fun KtClass.getKDocOrEmpty(): String = docComment?.formatOrEmpty() ?: ""

private fun KtClass.isSubClassOf(type: DSLTypeAbstract): Boolean =
    superTypeListEntries.any { it.typeReference?.text?.contains(type.typeName) == true }

fun KtClass.findGenericParameterForRuleServiceOrThrow(): KtTypeReference {
    return superTypeListEntries
        .find { it.typeReference?.text?.contains(RULE_SERVICE.typeName) == true }
        ?.typeReference
        ?.typeElement
        ?.typeArgumentsAsTypes
        ?.firstOrNull()
        ?: throw bail(ParsingExceptionType.ERROR_NO_SERVICE_RESPONSE_TYPE)
}

fun KtClass.isSubClassOf(type: DSLTypeService): Boolean =
    superTypeListEntries.any { it.typeReference?.text?.contains(type.typeName) == true }

fun KtClass.isSubClassOfOrThrow(type: DSLTypeService): KtClass =
    superTypeListEntries
        .find { it.typeReference?.text?.contains(type.typeName) == true }
        ?.let { this }
        ?: throw bail(
            ParsingExceptionType.ERROR_NOT_SUBCLASS_OF_SERVICE.message.format(
                name,
                type.typeName,
                containingKtFile.name
            )
        )

fun KtClass.primaryConstructorOrThrow(): KtPrimaryConstructor =
    primaryConstructor ?: throw bail(ParsingExceptionType.ERROR_NO_PRIMARY_CONSTRUCTOR)

fun KtClass.findMatchingPropertyOrThrow(flowType: DSLTypeFlow): KtProperty =
    body?.properties
        ?.let { properties ->
            properties
                .filter { it.hasModifier(KtTokens.OVERRIDE_KEYWORD) }
                .find { it.name == flowType.typeName }
                ?: throw bail(
                    ParsingExceptionType.ERROR_NO_OVERRIDE_FUNCTION.message.format(
                        flowType.typeName, name, containingKtFile.name
                    ))
        }
        ?: throw bail(ParsingExceptionType.ERROR_NO_PROPERTIES_FOUND)

private fun KtClass.bail(exceptionType: ParsingExceptionType): NoSuchElementException =
    bail(exceptionType.message.format(name,containingKtFile.name))

///////////////////////////////////////////////////
/** KtPrimaryConstructor extension functions */
///////////////////////////////////////////////////

fun KtPrimaryConstructor.findDSLTypeServiceRequest(
    bindingContext: BindingContext
): Result<Pair<KtParameter, KtClass>> = runCatching {

    val findServiceRequestParameter: (KtParameter) -> Pair<KtParameter, KtClass>? = { parameter ->
        val ktClass = parameter.typeReference?.resolveToKtClass(bindingContext)?.getOrNull()
        if (ktClass?.isSubClassOf(REQUEST) == true) Pair(parameter, ktClass) else null
    }

    valueParameters
        .firstNotNullOfOrNull(findServiceRequestParameter)
        ?: throw bail(ParsingExceptionType.ERROR_NO_SERVICE_REQUEST_PARAMETER)
}

fun KtPrimaryConstructor.findFirstParameterOfTypeClassOrThrow(bindingContext: BindingContext): Pair<KtParameter, KtClass> =
    valueParameters
        .firstNotNullOfOrNull { parameter ->
            parameter
                .typeReference
                ?.resolveToKtClass(bindingContext)?.getOrNull()
                ?.let { resolved -> Pair(parameter, resolved) }
        }
        ?: throw bail(ParsingExceptionType.ERROR_NO_FLOW_PARAMETER)

fun KtPrimaryConstructor.toPropertyInfo(): List<PropertyInfo> =
    valueParameters.map { parameter ->
        PropertyInfo(
            navn = parameter.name ?: "",
            type = parameter.typeReference?.text ?: "Unknown",
            beskrivelse = parameter.getKDocOrEmpty()
        )
    }

private fun KtPrimaryConstructor.bail(exceptionType: ParsingExceptionType): NoSuchElementException =
    bail(exceptionType.message.format(containingClass()?.name, containingKtFile.name))

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

private fun KtCallExpression.firstArgumentOrEmpty(): String = valueArguments
    .firstOrNull()
    ?.text
    ?.removeSurrounding("\"")
    ?: ""

private fun KtCallExpression.firstArgumentOrThrow(): String = valueArguments
    .firstOrNull()
    ?.text
    ?.removeSurrounding("\"")
    ?: throw bail(
        ParsingExceptionType.ERROR_FORGRENING_NAME_MISSING.message.format(
            containingClass()?.name,
            containingKtFile.name
    ))


fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> {
    return fold(
        onSuccess = { value -> transform(value) },
        onFailure = { exception -> Result.failure(exception) }
    )
}


///////////////////////////////////////////////////
/** KtBlockExpression extension functions */
///////////////////////////////////////////////////

fun KtBlockExpression.extractRuleServiceFlow(bindingContext: BindingContext): Result<FlowElement.Flow> = runCatching {
    FlowElement.Flow(
        children.mapNotNull { child ->
            when (child) {
                is KtCallExpression -> {
                    child.resolveFunctionDeclaration(bindingContext)
                        .map { (name, file) ->
                            FlowElement.Function(
                                navn = name,
                                beskrivelse = child.extractKDocOrEmpty(),
                                fil = file
                            )
                        }.getOrNull()
                }

                is KtDotQualifiedExpression -> {
                    child
                        .resolveReceiverClass(bindingContext)
                        ?.let { (resolvedClass, dslTypeAbstract) ->
                            when(dslTypeAbstract) {
                                RULE_FLOW ->
                                    FlowElement.RuleFlow(
                                        navn = resolvedClass.name ?: "Unknown",
                                        beskrivelse = child.extractKDocOrEmpty(),
                                        fil = File(resolvedClass.containingKtFile.name)
                                    )
                                else -> null
                            }
                        }
                }

                else -> null
            }
        }
    )
}

// TODO - hvordan håndtere flyt/regelsett (KtDotQualifiedExpression) som er høyresiden på en property
// TODO - NB! når KDoc er relatert til flow/ruleset/function - this.children -> this.statements

fun KtBlockExpression.extractRuleFlowFlow(bindingContext: BindingContext): Result<FlowElement.Flow> = runCatching {
    FlowElement.Flow(
        children.mapNotNull { child ->
            when (child) {
                is KtCallExpression -> {
                    when {
                        child.isForgrening() -> {
                            FlowElement.Forgrening(
                                beskrivelse = child.extractKDocOrEmpty(),
                                navn = child.firstArgumentOrThrow(),
                                gren = child.getLambdaBlock().flatMap { it.extractGrener(bindingContext) }.getOrThrow()
                            )
                        }

                        child.isGren() -> {
                            FlowElement.Gren(
                                beskrivelse = child.extractKDocOrEmpty(),
                                betingelse = child.getLambdaBlock().flatMap { it.extractBetingelse() }.getOrThrow(),
                                flyt = child.getLambdaBlock().flatMap { it.extractRuleFlowFlow(bindingContext) }.getOrThrow()
                            )
                        }

                        child.isFlyt() -> {
                            child.getLambdaBlock().flatMap { it.extractRuleFlowFlow(bindingContext) }.getOrThrow()
                        }

                        else -> null
                    }
                }

                is KtDotQualifiedExpression -> {
                    child
                        .resolveReceiverClass(bindingContext)
                        ?.let { (resolvedClass, dslTypeAbstract) ->
                            when(dslTypeAbstract) {
                                RULE_FLOW ->
                                    FlowElement.RuleFlow(
                                        navn = resolvedClass.name ?: "Unknown",
                                        beskrivelse = child.extractKDocOrEmpty(),
                                        fil = File(resolvedClass.containingKtFile.name)
                                    )
                                RULE_SET ->
                                    FlowElement.RuleSet(
                                        navn = resolvedClass.name ?: "Unknown",
                                        beskrivelse = child.extractKDocOrEmpty(),
                                        fil = File(resolvedClass.containingKtFile.name)
                                    )
                                else -> null
                            }
                        }
                }

                else -> null
            }
        })

}

/**
 * Extracts gren elements from a forgrening lambda block
 */
private fun KtBlockExpression.extractGrener(bindingContext: BindingContext): Result<List<FlowElement.Gren>> = runCatching {
    this.statements.mapNotNull { statement ->
        (statement as? KtCallExpression)?.let { gren ->
            FlowElement.Gren(
                beskrivelse = gren.extractKDocOrEmpty(),
                betingelse = gren.getLambdaBlock().flatMap { it.extractBetingelse() }.getOrThrow(),
                flyt = gren.getLambdaBlock().flatMap { it.extractRuleFlowFlow(bindingContext) }.getOrThrow()
            )
        }
    }
}

/**
 * Extracts betingelse from a gren lambda block
 */
private fun KtBlockExpression.extractBetingelse(): Result<Condition> = runCatching {
    this.statements.firstNotNullOfOrNull { statement ->

        val callExpression = statement as KtCallExpression

        Condition(
            navn = callExpression.firstArgumentOrEmpty(),
            uttrykk = callExpression.getLambdaBlock().map { it.text }.getOrThrow()
        )
    } ?: throw bail(
        ParsingExceptionType.ERROR_NO_BETINGELSE_FOUND.message.format(
            containingClass()?.name,
            containingKtFile.name
        ))
}


///////////////////////////////////////////////////
/** KtDotQualifiedExpression extension functions */
///////////////////////////////////////////////////

private fun KtDotQualifiedExpression.resolveReceiverClass(
    bindingContext: BindingContext
): Pair<KtClass, DSLTypeAbstract>? =
    (receiverExpression as? KtReferenceExpression)
        ?.resolveToKtClass(bindingContext)?.getOrNull()
        ?.let { ktClass ->
            findMatchingDSLTypeAbstract(ktClass)?.let { matchingDslType ->
                Pair(ktClass, matchingDslType)
            }
        }
