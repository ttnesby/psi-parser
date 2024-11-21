package org.example

import java.net.URI
import kotlin.collections.emptyList
import kotlin.sequences.sequence
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext

// TODO
// - Add support for RuleFlow and RuleSet, included into analyzeSourceFiles
//

fun analyzeSourceFiles2(sourceFiles: List<KtFile>, bindingContext: BindingContext): AnalysisResult =
        sourceFiles.chunked(100).fold(AnalysisResult(emptyList(), emptyList(), emptyList())) {
                acc,
                batch ->
            val batchResults =
                    batch.mapNotNull { file ->
                        (getRuleService(file, bindingContext).getOrNull()?.let { service ->
                                    AnalysisResult(listOf(service), emptyList(), emptyList())
                                }
                                        ?: getRuleFlow(file, bindingContext).getOrNull()?.let { flow
                                            ->
                                            AnalysisResult(emptyList(), listOf(flow), emptyList())
                                        }
                                                ?: getRuleSet(file, bindingContext)
                                                .getOrNull()
                                                ?.let { set ->
                                                    AnalysisResult(
                                                            emptyList(),
                                                            emptyList(),
                                                            listOf(set)
                                                    )
                                                })
                                .also { (file as PsiFileImpl).clearCaches() }
                    }

            AnalysisResult(
                    services = acc.services + batchResults.flatMap { it.services },
                    flows = acc.flows + batchResults.flatMap { it.flows },
                    sets = acc.sets + batchResults.flatMap { it.sets }
            )
        }

fun analyzeSourceFiles(
        sourceFiles: List<KtFile>,
        bindingContext: BindingContext
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
            RuleServiceDoc.new(
                    navn = ktClass.name!!,
                    beskrivelse = ktClass.getKDocOrEmpty(),
                    inndata = getRequestFields(ktClass, bindingContext),
                    utdata = getResponseFields(ktClass, bindingContext),
                    flyt = getFlow(ktClass, bindingContext),
                    gitHubUri = URI("${ktFile.name}")
            )
        }

fun getRequestFields(ktClass: KtClass, bindingContext: BindingContext): List<PropertyDoc> =
        ktClass.getServiceRequestInfo(bindingContext)
                .map { (parameter, serviceRequestClass) ->
                    buildList {
                        add(PropertyDoc.fromParameter(parameter, ktClass))
                        addAll(
                                serviceRequestClass.primaryConstructor?.let {
                                    PropertyDoc.fromPrimaryConstructor(it)
                                }
                                        ?: emptyList()
                        )
                    }
                }
                .getOrDefault(emptyList())

fun getResponseFields(ktClass: KtClass, bindingContext: BindingContext): List<PropertyDoc> =
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
                                }
                                        ?: emptyList()
                        )
                    }
                }
                .getOrDefault(emptyList())

fun getFlow(ktClass: KtClass, bindingContext: BindingContext): FlowElement.Flow =
        ktClass.getRuleServiceFlow(bindingContext)
                .map { sequence -> FlowElement.Flow(sequence.toList()) }
                .getOrDefault(FlowElement.Flow(emptyList()))

fun getRuleFlow(ktFile: KtFile, bindingContext: BindingContext): Result<RuleFlowDoc> =
        ktFile.getSubClassOfSuperClass(KtClass::isSubClassOfRuleFlowClass).map { ktClass ->
            RuleFlowDoc.new(
                    navn = ktClass.name!!,
                    beskrivelse = ktClass.getKDocOrEmpty(),
                    inndata = emptyList(),
                    flyt = FlowElement.Flow(emptyList()),
                    gitHubUri = URI("${ktFile.name}")
            )
        }

fun getRuleSet(ktFile: KtFile, bindingContext: BindingContext): Result<RuleSetDoc> =
        ktFile.getSubClassOfSuperClass(KtClass::isSubClassOfRuleSetClass).map { ktClass ->
            RuleSetDoc.new(
                    navn = ktClass.name!!,
                    beskrivelse = ktClass.getKDocOrEmpty(),
                    inndata = emptyList(),
                    flyt = FlowElement.Flow(emptyList()),
                    gitHubUri = URI("${ktFile.name}")
            )
        }
