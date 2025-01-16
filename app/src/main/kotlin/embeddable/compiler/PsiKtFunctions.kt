package embeddable.compiler

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.slf4j.LoggerFactory
import pensjon.regler.ParserConfig
import result.addons.flatMap
import java.io.File

private val logger = LoggerFactory.getLogger("PsiKtFunctions")

/**
 * Feilhåndtering for parsing av Kotlin PSI elementer gjøres etter følgende prinsipper:
 * 1) Hvis noe er forventet og ikke finnes brukes Kotlin Result
 * 2) Hvis noe er valgfritt brukes Kotlin nullable
 * 3) Hvis en funksjon bruker funksjoner som kan kaste unntak, brukes runCatching der Result<T> er avhengig av (1) eller (2)
 *
 */

///////////////////////////////////////////////////
/** KDoc extension functions */
///////////////////////////////////////////////////

private const val DOC_START = "/**"
private const val DOC_PREFIX = "*"
private const val DOC_END = "*/"
private const val NEW_LINE = "\n"

fun KDoc.formatOrEmpty(): String =
    text?.lines()
        ?.map { line -> line.trim().removePrefix(DOC_PREFIX).trim() }
        ?.filterNot { line -> line.isBlank() || line == "/" }
        ?.joinToString(NEW_LINE)
        ?.removePrefix(DOC_START)
        ?.removeSuffix(DOC_END)
        ?.trim()
        ?: ""


///////////////////////////////////////////////////
/** KtClass extension functions */
///////////////////////////////////////////////////

fun KtClass.docOrEmpty(): String = docComment?.formatOrEmpty() ?: ""

fun KtClass.requirePrimaryConstructor(): Result<KtPrimaryConstructor> =
    primaryConstructor
        ?.let { Result.success(it) }
        ?: Result.failure(illegalState("No primary constructor found"))

fun KtClass.requireBody(): Result<KtClassBody> =
    body?.let { Result.success(it) } ?: Result.failure(illegalState("No class body found"))


///////////////////////////////////////////////////
/** KtSuperTypeListEntry extension functions */
///////////////////////////////////////////////////

fun KtSuperTypeListEntry.findGenericTypeReference(): Result<KtTypeReference> =
    typeReference
        ?.typeElement
        ?.typeArgumentsAsTypes
        ?.firstOrNull()
        ?.let { Result.success(it) } ?: Result.failure(illegalState("No generic type reference found"))


///////////////////////////////////////////////////
/** KtPrimaryConstructor extension functions */
///////////////////////////////////////////////////

fun KtPrimaryConstructor.findFirstParameterOfTypeClass(config: ParserConfig): Result<Pair<KtParameter, KtClass>> =
    valueParameters
        .firstNotNullOfOrNull { it.hasTypeClass(config) }
        ?.let { Result.success(it) }
        ?: Result.failure(illegalState("No parameter of type class found in primary constructor"))


///////////////////////////////////////////////////
/** KtParameter extension functions */
///////////////////////////////////////////////////

fun KtParameter.docOrEmpty(): String = docComment?.formatOrEmpty() ?: ""

private fun KtParameter.hasTypeClass(config: ParserConfig): Pair<KtParameter, KtClass>? =
    typeReference
        ?.resolveToKtClass(config)?.getOrNull()
        ?.let { aClass -> Pair(this, aClass) }

fun KtParameter.requireTypeReference(): Result<KtTypeReference> =
    typeReference
        ?.let { Result.success(it) }
        ?: Result.failure(illegalState("No type reference for parameter $name"))


///////////////////////////////////////////////////
/** KtElement extension functions */
///////////////////////////////////////////////////

fun KtElement.illegalState(msg: String): IllegalStateException {
    val document = containingKtFile.viewProvider.document
    val lineNumber = document?.getLineNumber(this.textOffset)?.plus(1) ?: "unknown"
    return IllegalStateException(
        "$msg, ${containingClass()?.name} [${containingKtFile.name}] at line $lineNumber"
    )
}

typealias KtElementToDescriptorResult = KtElement.() -> Result<DeclarationDescriptor?>

val initResolveToDescriptorFunction: (BindingContext) -> KtElementToDescriptorResult =
    { bindingContext ->
        {
            when (this) {
                is KtNameReferenceExpression -> Result.success(
                    bindingContext[BindingContext.REFERENCE_TARGET, this]
                )

                is KtTypeReference -> Result.success(
                    bindingContext.get(BindingContext.TYPE, this)
                        ?.constructor
                        ?.declarationDescriptor
                )

                is KtReferenceExpression -> Result.success(
                    bindingContext.getType(this)
                        ?.constructor
                        ?.declarationDescriptor
                )

                else -> Result.failure(
                    IllegalArgumentException(
                        "Unsupported element type: ${this.javaClass.simpleName} " +
                                "for binding context resolution"
                    )
                )
            }
        }
    }

fun KtElement.resolveToDeclaration(resolver: KtElementToDescriptorResult): Result<PsiElement> =
    resolver().flatMap { descriptor ->
        descriptor
            ?.let {
                logger.debug("Descriptor: {}", descriptor.name)
                DescriptorToSourceUtils
                    .getSourceFromDescriptor(descriptor)
                    ?.let { psiElement ->
                        Result.success(psiElement)
                    } ?: Result.failure(illegalState("Could not resolve to declaration"))
            } ?: Result.failure(illegalState("Could not resolve descriptor"))
    }

fun KtElement.requireName(): Result<String> =
    name?.let { Result.success(it) } ?: Result.failure(illegalState("No name for ${this.javaClass.simpleName}"))

fun KtElement.resolveToKtClass(config: ParserConfig): Result<KtClass> =
    resolveToDeclaration(config.resolveToDescriptor).flatMap { psiElement ->
        (psiElement as? KtClass)
            ?.let { ktClass ->
                Result.success(ktClass)
            } ?: Result.failure(
            illegalState("Declaration is not a KtClass, but ${psiElement.javaClass.simpleName}")
        )
    }

/**
 * KDoc er enten et barn av PsiElementet eller ligger som et søsken-element umiddelbart før dette
 * elementet. Det lages en sekvens som starter fra forrige søsken-element og fortsetter til forrige
 * søsken-element for hver iterasjon. Filtrerer ut PsiWhiteSpace og KDoc-elementer og sekvensen stopper når et element er hverken KDoc eller PsiWhiteSpace.
 */
fun KtElement.extractDocOrEmpty(): String =
    generateSequence(this.prevSibling) { it.prevSibling }
        .takeWhile { it is PsiWhiteSpace || it is KDoc }
        .firstOrNull { it is KDoc }?.let {
            (it as KDoc).formatOrEmpty()
        }
        ?: ""


///////////////////////////////////////////////////
/** KtLambdaExpression extension functions */
///////////////////////////////////////////////////

private fun KtLambdaExpression?.requireLambdaBlock(caller: KtElement): Result<KtBlockExpression> =
    this
        ?.let {
            bodyExpression
                ?.let { Result.success(it) }
                ?: Result.failure(illegalState("No lambda block found for lambda expression"))
        } ?: Result.failure(caller.illegalState("No lambda expression"))


///////////////////////////////////////////////////
/** KtProperty extension functions */
///////////////////////////////////////////////////

fun KtProperty.getLambdaBlock(): Result<KtBlockExpression> =
    (initializer as? KtLambdaExpression).requireLambdaBlock(this)


///////////////////////////////////////////////////
/** KtCallExpression extension functions */
///////////////////////////////////////////////////

fun KtCallExpression.resolveFunctionDeclaration(config: ParserConfig): Result<Pair<String, File>> =
    (this.calleeExpression as? KtNameReferenceExpression)
        ?.let { namedReference ->
            namedReference
                .resolveToDeclaration(config.resolveToDescriptor).map { declaration ->
                    Pair(namedReference.text, File(declaration.containingFile.name))
                }
        } ?: Result.failure(illegalState("No named reference for call expression"))

fun KtCallExpression.getLambdaBlock(): Result<KtBlockExpression> =
    lambdaArguments // is multiple lambda args possible?
        .firstOrNull()
        ?.getLambdaExpression()
        ?.requireLambdaBlock(this)
        ?: Result.failure(illegalState("No lambda arguments found in call expression"))

fun KtCallExpression.firstArgumentOrEmpty(): String =
    // TODO - sjekk detaljene mellom valueArguments versus valueArgumentList
    valueArgumentList
        ?.arguments
        ?.firstOrNull()
        ?.text
        ?.removeSurrounding("\"")
        ?: ""



///////////////////////////////////////////////////
/** KtDotQualifiedExpression extension functions */
///////////////////////////////////////////////////

fun KtDotQualifiedExpression.findReferenceExpressionInChain(): KtReferenceExpression? =
// can have deep nesting of KtDotQualifiedExpression, see BeregnPoengrekke_EØStilAPFlyt, line 44
// run is the first, the receiver class is the second, then all the package prefixed
// TODO - all KIs are talking about the deepest level, FQN - to be done when understood...

    when (val nextReceiver = this.receiverExpression) {
        is KtDotQualifiedExpression -> nextReceiver.selectorExpression
        else -> this.receiverExpression
    }.let {
        it as? KtReferenceExpression
    }