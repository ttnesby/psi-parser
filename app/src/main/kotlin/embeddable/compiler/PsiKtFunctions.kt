package embeddable.compiler

import embeddable.compiler.BindingContextResolver.resolveToDeclaration
import org.jetbrains.kotlin.com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import pensjon.regler.Condition
import pensjon.regler.FlowElement
import pensjon.regler.PropertyInfo
import result.addons.flatMap
import result.addons.toResult
import rule.dsl.*
import rule.dsl.DSLTypeAbstract.*
import rule.dsl.DSLTypeBranch.FORGRENING
import rule.dsl.DSLTypeService.REQUEST
import java.io.File

/**
 * Feilhåndtering for parsing av Kotlin PSI elementer gjøres etter følgende prinsipper:
 * 1) Hvis noe er forventet og ikke finnes brukes Kotlin Result
 * 2) Hvis noe er valgfritt brukes Kotlin nullable
 * 3) Hvis en funksjon bruker funksjoner som kan kaste unntak, brukes runCatching der Result<T> er avhengig av (1) eller (2)
 *
 */

///////////////////////////////////////////////////
/** KtFile extension functions */
///////////////////////////////////////////////////

fun KtFile.firstDSLTypeAbstractOrNull(): Pair<KtClass, DSLTypeAbstract>? =
    declarations
        .filterIsInstance<KtClass>()
        .firstOrNull()
        ?.findDSLTypeAbstractOrNull()


///////////////////////////////////////////////////
/** KDoc extension functions */
///////////////////////////////////////////////////

private const val DOC_START = "/**"
private const val DOC_PREFIX = "*"
private const val DOC_END = "*/"
private const val NEW_LINE = "\n"

private fun KDoc.formatOrEmpty(): String =
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

private fun KtClass.findDSLTypeAbstractOrNull(): Pair<KtClass, DSLTypeAbstract>? =
    DSLTypeAbstract
        .entries
        .firstOrNull { dslType -> isSubClassOf(dslType) }
        ?.let { dslType -> Pair(this, dslType) }

fun KtClass.docOrEmpty(): String = docComment?.formatOrEmpty() ?: ""

private fun KtClass.isSubClassOf(type: DSLTypeSuperClass): Boolean =
    superTypeListEntries.any { it.isClassOf(type) }

fun KtClass.mustBeSubClassOf(type: DSLTypeService): Result<KtClass> =
    superTypeListEntries
        .find { it.isClassOf(type) }
        ?.let { Result.success(this) }
        ?: Result.failure(illegalState("$name is not sub class of ${type.typeName}"))

fun KtClass.findResponseTypeForRuleService(): Result<KtTypeReference> =
    superTypeListEntries
        .find { it.isClassOf(RULE_SERVICE) }
        ?.findGenericTypeReference()
        ?: Result.failure(illegalState("No service response type found"))

fun KtClass.requirePrimaryConstructor(): Result<KtPrimaryConstructor> =
    primaryConstructor
        ?.let { Result.success(it) }
        ?: Result.failure(illegalState("No primary constructor found"))

fun KtClass.requireBody(): Result<KtClassBody> =
    body?.let { Result.success(it) } ?: Result.failure(illegalState("No class body found"))

fun KtClass.findFlowProperty(flowType: DSLTypeFlow): Result<KtProperty> =
    requireBody().flatMap { body?.properties.findFlowProperty(flowType, this) }

private fun KtClass.toRuleFlowReference(doc: String): Result<FlowElement.RuleFlow> =
    requireName().map { name ->
        FlowElement.RuleFlow(
            navn = name,
            beskrivelse = doc,
            fil = File(containingKtFile.name)
        )
    }

private fun KtClass.toRuleSetReference(doc: String): Result<FlowElement.RuleSet> =
    requireName().map { name ->
        FlowElement.RuleSet(
            navn = name,
            beskrivelse = doc,
            fil = File(containingKtFile.name)
        )
    }

fun KtClass.toPropertyInfo(): Result<PropertyInfo> =
    requireName().map { name ->
        PropertyInfo(
            navn = name,
            type = name,
            beskrivelse = "Response for $name"
        )
    }


///////////////////////////////////////////////////
/** KtSuperTypeListEntry extension functions */
///////////////////////////////////////////////////

private fun KtSuperTypeListEntry.isClassOf(type: DSLTypeSuperClass): Boolean =
    typeReference?.text?.contains(type.typeName) == true

private fun KtSuperTypeListEntry.findGenericTypeReference(): Result<KtTypeReference> =
    typeReference
        ?.typeElement
        ?.typeArgumentsAsTypes
        ?.firstOrNull()
        ?.let { Result.success(it) } ?: Result.failure(illegalState("No generic type reference found"))


///////////////////////////////////////////////////
/** KtPrimaryConstructor extension functions */
///////////////////////////////////////////////////

// TODO - er det rimelig å anta at service request param er 1. param av typen klasse?

fun KtPrimaryConstructor.findParameterDSLTypeServiceRequest(): Result<Pair<KtParameter, KtClass>> =
    findFirstParameterOfTypeClass().flatMap { pair ->
        pair.second.mustBeSubClassOf(REQUEST).map { pair }
    }

fun KtPrimaryConstructor.findFirstParameterOfTypeClass(): Result<Pair<KtParameter, KtClass>> =
    valueParameters
        .firstNotNullOfOrNull { it.hasTypeClass() }
        ?.let { Result.success(it) }
        ?: Result.failure(illegalState("No parameter of type class found in primary constructor"))

fun KtPrimaryConstructor.toPropertyInfo(): Result<List<PropertyInfo>> =
    valueParameters.map { it.toPropertyInfo() }.toResult()

///////////////////////////////////////////////////
/** KtParameter extension functions */
///////////////////////////////////////////////////

fun KtParameter.docOrEmpty(): String = docComment?.formatOrEmpty() ?: ""

private fun KtParameter.hasTypeClass(): Pair<KtParameter, KtClass>? =
    typeReference
        ?.resolveToKtClass()?.getOrNull()
        ?.let { aClass -> Pair(this, aClass) }

fun KtParameter.toPropertyInfo(): Result<PropertyInfo> =
    requireName().flatMap { name ->
        requireTypeReference().map { typeRef ->
            PropertyInfo(
                navn = name,
                type = typeRef.text,
                beskrivelse = docOrEmpty()
            )
        }
    }

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

fun KtElement.requireName(): Result<String> =
    name?.let { Result.success(it) } ?: Result.failure(illegalState("No name for ${this.javaClass.simpleName}"))

fun KtElement.resolveToKtClass(): Result<KtClass> =
    resolveToDeclaration().flatMap { psiElement ->
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

private fun KtProperty.toPropertyInfo(): Result<PropertyInfo> =
    name?.let { name ->

        val typeRef = typeReference?.text
        val assignedValue = initializer

        val resolvedType = typeRef ?: when (assignedValue) {
            // Handle call expressions for custom types
            is KtCallExpression -> assignedValue.calleeExpression?.text
            // Handle constant expressions (BOOLEAN, INT, FLOAT, etc.)
            is KtConstantExpression -> when (assignedValue.node.elementType.toString()) {
                "BOOLEAN_CONSTANT" -> "Boolean"
                "INTEGER_CONSTANT" -> "Int"
                "FLOAT_CONSTANT" -> "Float"
                "STRING_CONSTANT" -> "String"
                else -> null
            }

            else -> null
        }

        resolvedType?.let { type ->
            Result.success(
                PropertyInfo(
                    navn = name,
                    type = type,
                    beskrivelse = this.children.filterIsInstance<KDoc>().firstOrNull()?.formatOrEmpty() ?: ""
                )
            )
        } ?: Result.failure(illegalState("No type or inferred type for property $name"))
    } ?: Result.failure(illegalState("No name for property"))

private fun KtExpression.extractInitializerExpression(): Result<FlowElement>? =
    when (this) {
        is KtCallExpression -> extractFunctionReference()
        is KtDotQualifiedExpression -> extractFlowReference()
        else -> null
    }

private fun KtProperty.extractInitializer(): Result<FlowElement>? =
    initializer?.extractInitializerExpression()

private fun KtBinaryExpression.extractInitializer(): Result<FlowElement>? =
    right?.extractInitializerExpression()

fun List<KtProperty>.toPropertyInfo(): Result<List<PropertyInfo>> = map { it.toPropertyInfo() }.toResult()

fun List<KtProperty>?.findFlowProperty(flowType: DSLTypeFlow, caller: KtElement): Result<KtProperty> =
    this?.let {
        filter { it.hasModifier(KtTokens.OVERRIDE_KEYWORD) }
        .find { it.name == flowType.typeName }
        ?.let {
            Result.success(it)
        } ?: Result.failure(caller.illegalState("No override function ${flowType.typeName} found"))
    } ?: Result.failure(caller.illegalState("No properties found"))


///////////////////////////////////////////////////
/** KtCallExpression extension functions */
///////////////////////////////////////////////////

private fun KtCallExpression.resolveFunctionDeclaration(): Result<Pair<String, File>> =
    (this.calleeExpression as? KtNameReferenceExpression)
        ?.let { namedReference ->
            namedReference
                .resolveToDeclaration().map { declaration ->
                    Pair(namedReference.text, File(declaration.containingFile.name))
                }
        } ?: Result.failure(illegalState("No named reference for call expression"))

private fun KtCallExpression.findDSLTypeBranchOrNull(): DSLTypeBranch? =
    (calleeExpression as? KtNameReferenceExpression)
        ?.getReferencedName()
        ?.let { name -> DSLTypeBranch.fromString(name) }

private fun KtCallExpression.getLambdaBlock(): Result<KtBlockExpression> =
    lambdaArguments // is multiple lambda args possible?
        .firstOrNull()
        ?.getLambdaExpression()
        ?.requireLambdaBlock(this)
        ?: Result.failure(illegalState("No lambda arguments found in call expression"))

private fun KtCallExpression.firstArgumentOrEmpty(): String =
    // TODO - sjekk detaljene mellom valueArguments versus valueArgumentList
    valueArgumentList
        ?.arguments
        ?.firstOrNull()
        ?.text
        ?.removeSurrounding("\"")
        ?: ""

private fun KtCallExpression.firstArgument(): Result<String> =
    valueArguments
        .firstOrNull()
        ?.let { arg ->
            Result.success(arg.text.removeSurrounding("\""))
        } ?: Result.failure(illegalState("No name found for forgrening"))

/**
 * Extract forgrening
 * ```
 * forgrening(string) {lambda block}
 * ```
 *
 * @return A 'Result' wrapping 'FlowElement.Forgrening'
 */

private fun KtCallExpression.extractForgrening(): Result<FlowElement.Forgrening> =
    firstArgument().flatMap { name ->
        getLambdaBlock().flatMap { blockExpression ->
            blockExpression.extractGrener().map { grener ->
                FlowElement.Forgrening(
                    beskrivelse = extractDocOrEmpty(),
                    navn = name,
                    gren = grener
                )
            }
        }
    }

/**
 * Extract gren
 * ```
 * gren {
 *      betingelse...
 *      flyt...
 * }
 * ```
 */

private fun KtCallExpression.extractGren(): Result<FlowElement.Gren> =
    getLambdaBlock().flatMap { blockExpression ->
        blockExpression.findBetingelseAndFlyt().flatMap { (betingelseExpr, flytExpr) ->
            betingelseExpr.extractBetingelse().flatMap { betingelse ->
                flytExpr.getLambdaBlock().flatMap { flytBlockExpression ->
                    val flytResult =
                        // do not extractFlowElements for empty flyt - `flyt {}` - gives error
                        if (flytBlockExpression.children.isEmpty()) Result.success(FlowElement.Flow(emptyList()))
                        else flytBlockExpression.extractFlowElements()

                    flytResult.map { flyt ->
                        FlowElement.Gren(
                            beskrivelse = extractDocOrEmpty(),
                            betingelse = betingelse,
                            flyt = flyt
                        )
                    }
                }
            }
        }
    }

/**
 * Extract betingelse
 * ```
 *   betingelse(string)? {lambda block}
 * ```
 */

private fun KtCallExpression.extractBetingelse(): Result<Condition> =
    getLambdaBlock().map { blockExpression: KtBlockExpression ->
        Condition(
            navn = this.firstArgumentOrEmpty(),
            uttrykk = blockExpression.text.trim().replace("\\s+".toRegex(), " ")
        )
    }

private fun KtCallExpression.extractForgreningOrNull(): Result<FlowElement>? =
    findDSLTypeBranchOrNull()
        ?.let { dslTypeBranch ->
            when (dslTypeBranch) {
                FORGRENING -> extractForgrening()
            }
        }

private fun KtCallExpression.extractFunctionReference(): Result<FlowElement.Function>? =
    resolveFunctionDeclaration()
        .map { (name, file) ->
            FlowElement.Function(
                navn = name,
                beskrivelse = extractDocOrEmpty(),
                fil = file
            )
        }
        // TODO - must add required functions
        // @TestOnly - temporary handling missing in binding context
        .fold(
            onSuccess = { Result.success(it) },
            onFailure = {
                println("Warning: Missing func ${(this.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()} in binding context ${it.message}")
                null
            }
        )


///////////////////////////////////////////////////
/** KtBlockExpression extension functions */
///////////////////////////////////////////////////

// TODO - se StartVilkårsprøvYtelseFlyt linje 204, hva skal vi gjøre her?
// TODO - NB! når KDoc er relatert til flow/ruleset/function - this.children -> this.statements

fun KtBlockExpression.extractFlowElements(): Result<FlowElement.Flow> =
    children.mapNotNull { child ->
        when (child) {
            is KtBinaryExpression -> child.extractInitializer()
            is KtProperty -> child.extractInitializer()
            is KtCallExpression -> child.extractForgreningOrNull() ?: child.extractFunctionReference()
            is KtDotQualifiedExpression -> child.extractFlowReference()
            is KtWhileExpression -> child.extractWhile() // see FaktoromregnInntekterBatchFlyt as example
            else -> null
        }
    }
        .let { flyt ->
            if (flyt.isEmpty()) {
//                println("Warning: empty flow with current flow extraction logic, ${containingClass()?.name} [${containingKtFile.name}]")
//                Result.success(FlowElement.Flow(emptyList()))
                // later when extraction logic is complete
                Result.failure(illegalState("Empty FlowElements.Flow"))
            } else {
                flyt.toResult().map { FlowElement.Flow(it) }
            }
        }

/**
 * Extract a list of 'gren' call expressions from a forgrening lambda block,
 * and map each call expression to 'FlowElement.Gren'
 * ```
 * {
 *      gren...
 *      gren...
 *      ...
 * }
 * ```
 * @return A 'Result' wrapping a list of 'FlowElement.Gren'
 */

private fun KtBlockExpression.extractGrener(): Result<List<FlowElement.Gren>> =
    this.statements
        .mapNotNull { expression -> (expression as? KtCallExpression) }
        .map { it.extractGren() }
        .let { aList ->
            if (aList.isEmpty()) Result.failure(
                illegalState("Empty 'forgrening', require at least 2 'gren' expressions")
            )
            else aList.toResult()
        }

/**
 * Extract a pair of call expressions from gren lambda block
 *```
 * {
 *      betingelse(string)? {lambda block}
 *      flyt {lambda block}
 * }
 * ```
 * @return A 'Result' wrapping a 'Pair' of call expressions, (betingelse,flyt)
 */

private fun KtBlockExpression.findBetingelseAndFlyt(): Result<Pair<KtCallExpression, KtCallExpression>> =
    this.statements
        .mapNotNull { expression -> (expression as? KtCallExpression) }
        .let { aList ->
            if (aList.size == 2) Result.success(Pair(aList[0], aList[1]))
            else Result.failure(illegalState("Invalid 'gren' content, expected expression 'betingelse' and 'fly'"))
        }

///////////////////////////////////////////////////
/** KtWhileExpression extension functions */
///////////////////////////////////////////////////

private fun KtWhileExpression.extractWhile(): Result<FlowElement> =
    condition
        ?.let { expression ->
            (body as? KtBlockExpression)
                ?.let { blockExpression ->
                    blockExpression.extractFlowElements().map { flyt ->
                        FlowElement.While(
                            betingelse = expression.text,
                            flyt = flyt
                        )
                    }
                } ?: Result.failure(illegalState("No block expression for while"))
        } ?: Result.failure(illegalState("No condition for while"))


///////////////////////////////////////////////////
/** KtDotQualifiedExpression extension functions */
///////////////////////////////////////////////////

private fun KtDotQualifiedExpression.resolveReceiverClass(): Pair<KtClass, DSLTypeAbstract>? =
    (receiverExpression as? KtReferenceExpression)
        ?.resolveToKtClass()?.map {
            it.findDSLTypeAbstractOrNull()
        }
        ?.getOrNull()


private fun KtDotQualifiedExpression.extractFlowReference(): Result<FlowElement>? =
    resolveReceiverClass()
        ?.let { (resolvedClass, dslTypeAbstract) ->
            when (dslTypeAbstract) {
                RULE_FLOW -> resolvedClass.toRuleFlowReference(this.extractDocOrEmpty())
                RULE_SET -> resolvedClass.toRuleSetReference(this.extractDocOrEmpty())
                RULE_SERVICE -> null
            }
        }
