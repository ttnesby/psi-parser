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
import rule.dsl.DSLTypeBranch.*
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
        ?.matchingDSLTypeAbstractOrNull()


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

private fun KtClass.matchingDSLTypeAbstractOrNull(): Pair<KtClass, DSLTypeAbstract>? =
    DSLTypeAbstract
        .entries
        .firstOrNull { dslType -> isSubClassOf(dslType) }
        ?.let { dslType -> Pair(this, dslType) }

fun KtClass.docOrEmpty(): String = docComment?.formatOrEmpty() ?: ""

private fun KtClass.isSubClassOf(type: DSLTypeSuperClass): Boolean =
    superTypeListEntries.any { it.isClassOf(type) }

fun KtClass.findResponseTypeForRuleService(): Result<KtTypeReference> =
    superTypeListEntries
        .find { it.isClassOf(RULE_SERVICE) }
        ?.genericTypeReference()
        ?: Result.failure(illegalState("No service response type found"))

fun KtClass.mustBeSubClassOf(type: DSLTypeService): Result<KtClass> =
    superTypeListEntries
        .find { it.isClassOf(type) }
        ?.let { Result.success(this) }
        ?: Result.failure(illegalState("$name is not sub class of ${type.typeName}"))

fun KtClass.requirePrimaryConstructor(): Result<KtPrimaryConstructor> =
    primaryConstructor
        ?.let { Result.success(it) }
        ?: Result.failure(illegalState("No primary constructor found"))

fun KtClass.findMatchingProperty(flowType: DSLTypeFlow): Result<KtProperty> =
    body
        ?.properties
        ?.let { properties ->
            properties
                .filter { it.hasModifier(KtTokens.OVERRIDE_KEYWORD) }
                .find { it.name == flowType.typeName }
                ?.let {
                    Result.success(it)
                } ?: Result.failure(illegalState("No override function ${flowType.typeName} found"))
        } ?: Result.failure(illegalState("No properties found"))

fun KtClass.requireName(): Result<String> =
    name?.let { Result.success(it) } ?: Result.failure(illegalState("No class name"))

private fun KtClass.toRuleFlowReference(): Result<FlowElement.RuleFlow> =
    requireName().map { name ->
        FlowElement.RuleFlow(
            navn = name,
            beskrivelse = extractDocOrEmpty(),
            fil = File(containingKtFile.name)
        )
    }

private fun KtClass.toRuleSetReference(): Result<FlowElement.RuleSet> =
    requireName().map { name ->
        FlowElement.RuleSet(
            navn = name,
            beskrivelse = extractDocOrEmpty(),
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

private fun KtSuperTypeListEntry.genericTypeReference(): Result<KtTypeReference> =
    typeReference
        ?.typeElement
        ?.typeArgumentsAsTypes
        ?.firstOrNull()
        ?.let { Result.success(it) } ?: Result.failure(illegalState("No generic type reference found"))


///////////////////////////////////////////////////
/** KtPrimaryConstructor extension functions */
///////////////////////////////////////////////////

fun KtPrimaryConstructor.findParameterDSLTypeServiceRequest(): Result<Pair<KtParameter, KtClass>> =
    valueParameters
        .firstNotNullOfOrNull {
            it.hasTypeClass()?.let { pair ->
                if (pair.second.isSubClassOf(REQUEST)) pair else null
            }
        }
        ?.let { Result.success(it) }
        ?: Result.failure(illegalState("No service request parameter found in primary constructor"))

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
    name?.let { name ->
        typeReference?.let { type ->
            Result.success(
                PropertyInfo(
                    navn = name,
                    type = type.text,
                    beskrivelse = docOrEmpty()
                )
            )
        } ?: Result.failure(illegalState("No type for parameter $name"))
    } ?: Result.failure(illegalState("No name for parameter"))


///////////////////////////////////////////////////
/** KtElement extension functions */
///////////////////////////////////////////////////

fun KtElement.illegalState(msg: String): IllegalStateException =
    IllegalStateException("$msg, ${containingClass()?.name} [${containingKtFile.name}]")

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
/** KtProperty extension functions */
///////////////////////////////////////////////////

fun KtProperty.getLambdaBlock(): Result<KtBlockExpression> =
    (initializer as? KtLambdaExpression)
        ?.bodyExpression
        ?.let { Result.success(it) } ?: Result.failure(illegalState("No lambda block found in property"))

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

fun List<KtProperty>.toPropertyInfo(): Result<List<PropertyInfo>> = map { it.toPropertyInfo() }.toResult()


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
        } ?: Result.failure(illegalState("No named reference for called expression"))

private fun KtCallExpression.resolveToDSLTypeBranch(): DSLTypeBranch? =
    (calleeExpression as? KtNameReferenceExpression)
        ?.getReferencedName()
        ?.let { name -> DSLTypeBranch.fromString(name) }

private fun KtCallExpression.getLambdaBlock(): Result<KtBlockExpression> =
    lambdaArguments // is multiple lambda args possible?
        .firstOrNull()
        ?.let { lambdaArg ->
            lambdaArg
                .getLambdaExpression()
                ?.let { functionLiteral ->
                    functionLiteral
                        .bodyExpression
                        ?.let { ktBlockExpression ->
                            Result.success(ktBlockExpression)
                        } // do we need to raise high resolution failure?
                } // do we need to raise high resolution failure?
        } ?: Result.failure(illegalState("No lambda arguments found in call expression"))

private fun KtCallExpression.firstArgumentOrEmpty(): String =
    valueArguments
        .firstOrNull()
        ?.text
        ?.removeSurrounding("\"")
        ?: ""

private fun KtCallExpression.firstArgument(): Result<String> =
    valueArguments
        .firstOrNull()
        ?.let { arg ->
            Result.success(arg.text.removeSurrounding("\""))
        } ?: Result.failure(illegalState("No name found for forgrening"))

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

private fun KtCallExpression.extractGren(): Result<FlowElement.Gren> =
    getLambdaBlock().flatMap { blockExpression ->
        blockExpression.extractBetingelse().flatMap { betingelse ->
            blockExpression.extractRuleFlowFlow().map { flyt ->
                FlowElement.Gren(
                    beskrivelse = extractDocOrEmpty(),
                    betingelse = betingelse,
                    flyt = flyt
                )
            }
        }
    }

private fun KtCallExpression.extractBetingelse(): Result<Condition> =
    getLambdaBlock().map { blockExpression: KtBlockExpression ->
        Condition(
            navn = firstArgumentOrEmpty(),
            uttrykk = blockExpression.text
        )
    }

private fun KtCallExpression.extractFlyt(): Result<FlowElement.Flow> =
    getLambdaBlock().flatMap { blockExpression ->
        blockExpression.extractRuleFlowFlow()
    }

private fun KtCallExpression.extractBranch(): Result<FlowElement>? =
    resolveToDSLTypeBranch()
        ?.let { dslTypeBranch ->
            when (dslTypeBranch) {
                FORGRENING -> extractForgrening()
                GREN -> extractGren()
                FLYT -> extractFlyt()
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
                println("Warning: Missing func in binding context ${it.message}")
                null
            }
        )


///////////////////////////////////////////////////
/** KtBlockExpression extension functions */
///////////////////////////////////////////////////

fun KtBlockExpression.extractRuleServiceFlow(): Result<FlowElement.Flow> =
    children.mapNotNull { child ->
        when (child) {
            is KtCallExpression -> child.extractFunctionReference()
            is KtDotQualifiedExpression -> child.extractFlowReference()
            else -> null
        }
    }
        .let { flyt ->
            if (flyt.isEmpty()) {
                val context = "${containingClass()?.name} [${containingKtFile.name}]"
                println("Warning: empty flow with current flow extraction logic, $context")
                Result.success(FlowElement.Flow(emptyList()))
                // later when extraction logic is complete
                // Result.failure(noSuchElement(ParsingError.EMPTY_RULE_SERVICE_FLOW))
            } else {
                flyt.toResult().map { FlowElement.Flow(it) }
            }
        }


// TODO - hvordan håndtere flyt/regelsett (KtDotQualifiedExpression) som er høyresiden på en property
// TODO - NB! når KDoc er relatert til flow/ruleset/function - this.children -> this.statements

fun KtBlockExpression.extractRuleFlowFlow(): Result<FlowElement.Flow> =
    children.mapNotNull { child ->
        when (child) {
            is KtCallExpression -> child.extractBranch()
            is KtDotQualifiedExpression -> child.extractFlowReference()
            else -> null
        }
    }
        .let { flyt ->
            if (flyt.isEmpty()) {
                println("Warning: empty flow with current flow extraction logic, ${containingClass()?.name} [${containingKtFile.name}]")
                Result.success(FlowElement.Flow(emptyList()))
                // later when extraction logic is complete
                // Result.failure(noSuchElement(ParsingError.EMPTY_RULE_SERVICE_FLOW))
            } else {
                flyt.toResult().map { FlowElement.Flow(it) }
            }
        }

/**
 * Extracts gren elements from a forgrening lambda block
 */
private fun KtBlockExpression.extractGrener(): Result<List<FlowElement.Gren>> =
    this.statements
        .mapNotNull { statement ->
            (statement as? KtCallExpression)?.extractGren()
        }
        .toResult()

/**
 * Extracts betingelse from a gren lambda block
 */
private fun KtBlockExpression.extractBetingelse(): Result<Condition> =
    this.statements
        .firstNotNullOfOrNull { statement ->
            (statement as? KtCallExpression)?.extractBetingelse()
        } ?: Result.failure(illegalState("No betingelse found for gren"))


///////////////////////////////////////////////////
/** KtDotQualifiedExpression extension functions */
///////////////////////////////////////////////////

private fun KtDotQualifiedExpression.resolveReceiverClass(): Pair<KtClass, DSLTypeAbstract>? =
    (receiverExpression as? KtReferenceExpression)
        ?.resolveToKtClass()?.map { ktClass ->
            ktClass.matchingDSLTypeAbstractOrNull()
        }
        ?.getOrNull()


private fun KtDotQualifiedExpression.extractFlowReference(): Result<FlowElement>? =
    resolveReceiverClass()?.let { (resolvedClass, dslTypeAbstract) ->
        when (dslTypeAbstract) {
            RULE_FLOW -> resolvedClass.toRuleFlowReference()
            RULE_SET -> resolvedClass.toRuleSetReference()
            RULE_SERVICE -> null
        }
    }
