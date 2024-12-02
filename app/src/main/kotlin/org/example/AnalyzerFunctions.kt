package org.example

import org.example.PropertyDoc.Companion.fromParameter
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import java.net.URI

data class AnalysisResult(
    val services: List<RuleServiceDoc>,
    val flows: List<RuleFlowDoc>,
    val sets: List<RuleSetDoc>
)

// TODO
// - Add support for RuleFlow and RuleSet, included into analyzeSourceFiles
//
fun analyzeSourceFiles(sourceFiles: List<KtFile>, bindingContext: BindingContext): Result<AnalysisResult> =
    runCatching {
        sourceFiles
            .chunked(100)
            .fold(AnalysisResult(emptyList(), emptyList(), emptyList())) { acc, batch ->
                val batchResults = batch.mapNotNull { file ->
                    // TODO - Vurder om å kategorisere en fil ihht. dsl types er en god idé
                    when {
                        file.isRuleService() -> {
                            getRuleService(file, bindingContext).map { service ->
                                AnalysisResult(listOf(service), emptyList(), emptyList())
                            }.getOrThrow()
                        }

                        file.isRuleflow() -> {
                            getRuleFlow(file, bindingContext).map { flow ->
                                AnalysisResult(emptyList(), listOf(flow), emptyList())
                            }.getOrThrow()
                        }

                        file.isRuleset() -> {
                            getRuleSet(file, bindingContext).map { set ->
                                AnalysisResult(emptyList(), emptyList(), listOf(set))
                            }.getOrThrow()
                        }

                        else -> null
                    }.also {
                        (file as PsiFileImpl).clearCaches()
                    }
                }
                AnalysisResult(
                    services = acc.services + batchResults.flatMap { it.services },
                    flows = acc.flows + batchResults.flatMap { it.flows },
                    sets = acc.sets + batchResults.flatMap { it.sets }
                )
            }.also {
                println("Analyzed Source Files")
            }
    }

@TestOnly
fun analyzeSourceFilesTest(
    sourceFiles: List<KtFile>,
    bindingContext: BindingContext,
): List<RuleServiceDoc> =
    sourceFiles.chunked(100).flatMap { batch ->
        batch.mapNotNull { file ->
            getRuleService(file, bindingContext).getOrNull().also {
                (file as PsiFileImpl).clearCaches()
            }
        }
    }

/**
 * Extract rule service documentation from a Kotlin file
 *
 * @param ktFile Kotlin file to analyze
 * @param bindingContext Binding context for type resolution
 * @return Result containing RuleServiceDoc if a rule service class is found,
 * ```
 *         or failure if no rule service class exists in the file
 * ```
 */
fun getRuleService(ktFile: KtFile, bindingContext: BindingContext): Result<RuleServiceDoc> =
    ktFile.getSubClassOfSuperClass(KtClass::isSubClassOfRuleServiceClass).map { ktClass ->
        RuleServiceDoc(
            navn = ktClass.name!!,
            beskrivelse = ktClass.getKDocOrEmpty(),
            inndata = getRequestFields(ktClass, bindingContext).getOrThrow(),
            utdata = getResponseFields(ktClass, bindingContext).getOrThrow(),
            flyt = getRuleService(ktClass, bindingContext).getOrThrow(),
            gitHubUri = URI(ktFile.name.convertToGitHubUrl())
        )
    }

fun getRequestFields(ktClass: KtClass, bindingContext: BindingContext): Result<List<PropertyDoc>> = runCatching {
    ktClass.getServiceRequestInfo(bindingContext)
        .map { (parameter, serviceRequestClass) ->
            buildList {
                add(fromParameter(parameter, ktClass))
                addAll(
                    serviceRequestClass.primaryConstructor?.let {
                        PropertyDoc.fromPrimaryConstructor(it)
                    } ?: throw IllegalStateException("No primary constructor found for ${serviceRequestClass.name}")
                )
            }
        }.getOrThrow()
}

fun getResponseFields(ktClass: KtClass, bindingContext: BindingContext): Result<List<PropertyDoc>> = runCatching {
    ktClass.getServiceResponseClass(bindingContext)
        .map { serviceResponseClass ->
            buildList {
                add(
                    PropertyDoc.new(
                        serviceResponseClass.name!!,
                        serviceResponseClass.name!!,
                        "Response for ${ktClass.name}"
                    )
                )
                addAll(
                    serviceResponseClass.primaryConstructor?.let {
                        PropertyDoc.fromPrimaryConstructor(it)
                    } ?: throw IllegalArgumentException("No primary constructor found for ${serviceResponseClass.name}")
                )
            }
        }.getOrThrow()
}

fun getRuleService(ktClass: KtClass, bindingContext: BindingContext): Result<FlowElement.Flow> = runCatching {
    ktClass.getRuleServiceFlow(bindingContext)
        .map { sequence -> FlowElement.Flow(sequence.toList()) }
        .getOrThrow()
}

fun getRuleFlow(ktFile: KtFile, bindingContext: BindingContext): Result<RuleFlowDoc> =
    ktFile.getSubClassOfSuperClass(KtClass::isSubClassOfRuleFlowClass).map { ktClass ->
        RuleFlowDoc.new(
            navn = ktClass.name!!,
            beskrivelse = ktClass.getKDocOrEmpty(),
            inndata = emptyList(),
            flyt = FlowElement.Flow(emptyList()),
            gitHubUri = URI(ktFile.name.convertToGitHubUrl())
        )
    }

fun getRuleSet(ktFile: KtFile, bindingContext: BindingContext): Result<RuleSetDoc> =
    ktFile.getSubClassOfSuperClass(KtClass::isSubClassOfRuleSetClass).map { ktClass ->
        RuleSetDoc.new(
            navn = ktClass.name!!,
            beskrivelse = ktClass.getKDocOrEmpty(),
            inndata = emptyList(),
            flyt = FlowElement.Flow(emptyList()),
            gitHubUri = URI(ktFile.name.convertToGitHubUrl())
        )
    }

// TODO: Denne kan trolig skrives om. Må finne en god måte å håndtere GithubUrl for tester.
fun String.convertToGitHubUrl(): String =
    this.indexOf("pensjon-regler")
        .takeIf { it != -1 }
        ?.let { index ->
            "https://github.com/navikt/" + this.substring(index)
                .replace('\\', '/')
                .replaceFirst("pensjon-regler/", "pensjon-regler/blob/master/")
        } ?: "https://github.com/navikt/$this"