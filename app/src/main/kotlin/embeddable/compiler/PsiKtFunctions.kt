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

/**
 * Feilhåndtering for parsing av Kotlin PSI elementer gjøres etter følgende prinsipper:
 * 1) Hvis noe er forventet og ikke finnes brukes Kotlin Result
 * 2) Hvis noe er valgfritt brukes Kotlin nullable
 * 3) Hvis en funksjon bruker funksjoner som kan kaste unntak, brukes runCatching der Result<T> er avhengig av (1) eller (2)
 *
 */

enum class ParsingError(val message: String) {
    NO_PRIMARY_CONSTRUCTOR("No primary constructor found for %s [%s]"),
    NO_SERVICE_REQUEST_PARAMETER("No service request parameter found in primary constructor for %s [%s]"),
    NO_SERVICE_RESPONSE_TYPE("No service response type found for %s [%s]"),
    NOT_SUBCLASS_OF_SERVICE("%s is not subclass of %s [%s]"),
    NO_FLOW_PARAMETER("No flow parameter of type class found in primary constructor for %s [%s]"),
    NO_PROPERTIES_FOUND("No properties found for %s [%s]"),
    NO_OVERRIDE_FUNCTION("No override function %s found for %s [%s]"),
    NO_FORGRENING_NAME_FOUND("No name found for forgrening for %s [%s]"),
    NO_BETINGELSE_FOUND("No betingelse found for gren for %s [%s]"),;
}

private fun noSuchElement(message: String): NoSuchElementException = NoSuchElementException(message)

///////////////////////////////////////////////////
/** KtFile extension functions */
///////////////////////////////////////////////////

fun KtFile.findDSLTypeAbstract(): Pair<KtClass, DSLTypeAbstract>?  {
    val firstKtClass = declarations.filterIsInstance<KtClass>().firstOrNull()
        ?: return null

    val matchingDslType = findMatchingDSLTypeAbstract(firstKtClass)
        ?: return null

    return Pair(firstKtClass, matchingDslType)
}

private fun findMatchingDSLTypeAbstract(ktClass: KtClass): DSLTypeAbstract? {
    return DSLTypeAbstract.entries.firstOrNull { dslType ->
        ktClass.isSubClassOf(dslType)
    }
}


///////////////////////////////////////////////////
/** KDoc extension functions */
///////////////////////////////////////////////////

private const val DOC_START = "/**"
private const val DOC_END = "*/"

private fun KDoc.formatOrEmpty(): String =
    text?.lines()
        ?.map { it.trim().removePrefix("*").trim() }
        ?.filterNot { it.isBlank() || it == "/" }
        ?.joinToString("\n")
        ?.removePrefix(DOC_START)
        ?.removeSuffix(DOC_END)
        ?.trim()
        ?: ""


///////////////////////////////////////////////////
/** KtClass extension functions */
///////////////////////////////////////////////////

// helper function to create a NoSuchElementException with a formatted message with class - and file name
private fun KtClass.noSuchElement(exceptionType: ParsingError): NoSuchElementException =
    noSuchElement(exceptionType.message.format(name,containingKtFile.name))

fun KtClass.getKDocOrEmpty(): String = docComment?.formatOrEmpty() ?: ""

private fun KtClass.isSubClassOf(type: DSLTypeAbstract): Boolean =
    superTypeListEntries.any { it.typeReference?.text?.contains(type.typeName) == true }

fun KtClass.findResponseTypeForRuleService(): Result<KtTypeReference> =
    superTypeListEntries
        .find { it.typeReference?.text?.contains(RULE_SERVICE.typeName) == true }
        // get the generic type argument for the rule service = response type
        ?.typeReference
        ?.typeElement
        ?.typeArgumentsAsTypes
        ?.firstOrNull()
        ?.let { Result.success(it) }
        ?: Result.failure(noSuchElement(ParsingError.NO_SERVICE_RESPONSE_TYPE))


fun KtClass.isSubClassOf(type: DSLTypeService): Boolean =
    superTypeListEntries.any { it.typeReference?.text?.contains(type.typeName) == true }

fun KtClass.mustBeSubClassOf(type: DSLTypeService): Result<KtClass> =
    superTypeListEntries
        .find { it.typeReference?.text?.contains(type.typeName) == true }
        ?.let { Result.success(this) }
        ?: Result.failure(noSuchElement(
            ParsingError.NOT_SUBCLASS_OF_SERVICE.message.format(
                name,
                type.typeName,
                containingKtFile.name
            )))

fun KtClass.requirePrimaryConstructor(): Result<KtPrimaryConstructor> =
    primaryConstructor
        ?.let { Result.success(it) }
        ?: Result.failure(
            noSuchElement(ParsingError.NO_PRIMARY_CONSTRUCTOR)
        )

fun KtClass.findMatchingProperty(flowType: DSLTypeFlow): Result<KtProperty> =
    body?.properties
        ?.let { properties ->
            properties
                .filter { it.hasModifier(KtTokens.OVERRIDE_KEYWORD) }
                .find { it.name == flowType.typeName }
                ?.let { Result.success(it) }
                ?: Result.failure(
                    noSuchElement(ParsingError.NO_OVERRIDE_FUNCTION.message
                        .format(
                            flowType.typeName, name, containingKtFile.name
                        )
                    )
                )
        }
        ?: Result.failure(noSuchElement(ParsingError.NO_PROPERTIES_FOUND))

///////////////////////////////////////////////////
/** KtPrimaryConstructor extension functions */
///////////////////////////////////////////////////

private fun KtPrimaryConstructor.noSuchElement(exceptionType: ParsingError): NoSuchElementException =
    noSuchElement(exceptionType.message.format(containingClass()?.name, containingKtFile.name))

fun KtPrimaryConstructor.findDSLTypeServiceRequest(
    bindingContext: BindingContext
): Result<Pair<KtParameter, KtClass>> {

    val findServiceRequestParameter: (KtParameter) -> Pair<KtParameter, KtClass>? = { parameter ->
        parameter.typeReference
            ?.resolveToKtClass(bindingContext)
            ?.getOrNull()
            ?.let{ ktClass ->
                if (ktClass.isSubClassOf(REQUEST)) Pair(parameter, ktClass) else null
            }
    }

    return valueParameters
        .firstNotNullOfOrNull(findServiceRequestParameter)
        ?.let { Result.success(it) }
        ?: Result.failure(noSuchElement(ParsingError.NO_SERVICE_REQUEST_PARAMETER))
}

fun KtPrimaryConstructor.findFirstParameterOfTypeClass(
    bindingContext: BindingContext
): Result<Pair<KtParameter, KtClass>> =
    valueParameters
        .firstNotNullOfOrNull { parameter ->
            parameter
                .typeReference
                ?.resolveToKtClass(bindingContext)?.getOrNull()
                ?.let { aClass -> Pair(parameter, aClass) }
        }
        ?.let { Result.success(it) }
        ?: Result.failure(noSuchElement(ParsingError.NO_FLOW_PARAMETER))

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
    resolveToDeclaration(bindingContext).mapCatching {
        it as? KtClass ?: throw NoSuchElementException("Declaration is not a KtClass, but ${it.javaClass.simpleName}, [${this.containingKtFile.name}]")
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
    ?: throw noSuchElement(
        ParsingError.NO_FORGRENING_NAME_FOUND.message.format(
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

private fun KtBlockExpression.noSuchElement(exceptionType: ParsingError): NoSuchElementException =
    noSuchElement(exceptionType.message.format(containingClass()?.name, containingKtFile.name))

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
private fun KtBlockExpression.extractGrener(bindingContext: BindingContext): Result<List<FlowElement.Gren>> =
    this.statements.mapNotNull { statement ->
        (statement as? KtCallExpression)
            ?.let { gren ->
                val block = gren.getLambdaBlock()
                block
                    .flatMap { it.extractBetingelse() }
                    .flatMap { betingelse ->
                        block
                            .flatMap { it.extractRuleFlowFlow(bindingContext) }
                            .map { flyt ->
                                FlowElement.Gren(
                                    beskrivelse = gren.extractKDocOrEmpty(),
                                    betingelse = betingelse,
                                    flyt = flyt
                                )
                            }
                    }
            } // here is null in mapNotNull context
    } // here is List<Result<FlowElement.Gren>> - need to flip
        .toResult()


/**
 * Combines a list of `Result` objects into a single `Result` containing a list of all successful values
 * or a failure if any of the results is a failure.
 *
 * @return A `Result` wrapping a list of successful values if all results are successful,
 * or a failure wrapping the first encountered exception if any of the results fail.
 */
fun <T> List<Result<T>>.toResult(): Result<List<T>> {
    return fold(Result.success(emptyList())) { acc, element ->
        acc.fold(
            onSuccess = { list ->
                element.map { value -> list + value }
            },
            onFailure = { Result.failure(it) }
        )
    }
}

/**
 * Extracts betingelse from a gren lambda block
 */
private fun KtBlockExpression.extractBetingelse(): Result<Condition> =
    this.statements.firstNotNullOfOrNull { statement ->
        (statement as? KtCallExpression)
            ?.let { callExpression ->
                callExpression.getLambdaBlock()
                    .map { block ->
                        Condition(
                            navn = callExpression.firstArgumentOrEmpty(),
                            uttrykk = block.text
                        )
                    } // here is Result success/failure
            }
    } ?: Result.failure( noSuchElement(ParsingError.NO_BETINGELSE_FOUND))



///////////////////////////////////////////////////
/** KtDotQualifiedExpression extension functions */
///////////////////////////////////////////////////

private fun KtDotQualifiedExpression.resolveReceiverClass(
    bindingContext: BindingContext
): Pair<KtClass, DSLTypeAbstract>? =
    (receiverExpression as? KtReferenceExpression)
        ?.resolveToKtClass(bindingContext)
        ?.map { ktClass ->
            findMatchingDSLTypeAbstract(ktClass)
                ?.let { matchingDslType -> Pair(ktClass, matchingDslType) }
        }?.getOrNull()
