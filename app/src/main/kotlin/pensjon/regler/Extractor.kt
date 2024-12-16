package pensjon.regler

import embeddable.compiler.CompilerContext
import org.example.*
import org.example.DSLTypeAbstract.*
import org.example.DSLTypeAbstractResult.Found
import org.example.DSLTypeAbstractResult.NOTFound
import org.example.DSLTypeService.REQUEST
import org.example.DSLTypeService.RESPONSE
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.resolve.BindingContext
import pensjon.regler.PropertyInfo.Companion.fromParameter
import kotlin.io.path.absolutePathString

class Extractor private constructor(
    private val repo: Repo,
    //private val context: CompilerContext,
    private val psiFiles: List<KtFile>,
    private val bindingContext: BindingContext
) {
    companion object {
        fun new(repo: Repo, context: CompilerContext): Result<Extractor> = runCatching {

            val psiFiles = repo.files().map { fileInfo ->
                context.createKtFile(fileInfo.file.absolutePathString(), fileInfo.content)
            }
            val bindingContext = context.buildBindingContext(psiFiles).getOrThrow()

            Extractor(repo, psiFiles, bindingContext)
        }
    }

    fun toModel(): Result<ModelResult> = runCatching {
        psiFiles
            .chunked(100)
            .fold(ModelResult.empty()) { acc, batch ->
                val batchResults = batch.mapNotNull { file ->

                    when (val result = file.findDSLTypeAbstract().getOrThrow()) {
                        is Found -> {
                            when (result.dslType) {
                                RULE_SERVICE -> {
                                    result.ktClass.extractRuleService().getOrThrow().let { ModelResult.newService(it) }
                                }

                                RULE_FLOW -> {
                                    result.ktClass.extractRuleFlow().getOrThrow().let { ModelResult.newFlow(it) }
                                }

                                RULE_SET -> {
                                    result.ktClass.extractRuleSet().getOrThrow().let { ModelResult.newSet(it) }
                                }
                            }
                        }

                        NOTFound -> null
                    }.also {
                        // rydd opp i cache
                        (file as PsiFileImpl).clearCaches()
                    }
                }
                acc.addBatch(
                    services = batchResults.flatMap { it.services },
                    flows = batchResults.flatMap { it.flows },
                    sets = batchResults.flatMap { it.sets }
                )
            }
    }

    private fun KtClass.extractRuleService(): Result<RuleServiceInfo> = runCatching {
        RuleServiceInfo(
            navn = name!!,
            beskrivelse = getKDocOrEmpty(),
            inndata = extractServiceRequestFields().getOrThrow(),
            utdata = extractServiceResponseFields().getOrThrow(),
            flyt = extractRuleServiceFlow().getOrThrow(),
            gitHubUri = repo.toGithubURI(containingKtFile.name).getOrThrow()
        )
    }

    private fun KtClass.extractServiceRequestFields(): Result<List<PropertyInfo>> = runCatching {

        val primaryConstructor = primaryConstructor
            ?: throw NoSuchElementException(
                "No primary constructor found for $name [${containingKtFile.name}]"
            )

        val (parameter, serviceRequestClass) = primaryConstructor
            .findDSLTypeServiceRequest().getOrThrow()

        val reqFields = serviceRequestClass.primaryConstructor?.let {
            PropertyInfo.fromPrimaryConstructor(it)
        } ?: throw NoSuchElementException(
            String.format(
                "No primary constructor found for %s [%s]",
                serviceRequestClass.name,
                serviceRequestClass.containingKtFile.name
            )
        )

        buildList {
            add(fromParameter(parameter))
            addAll(reqFields)
        }
    }

    private fun KtPrimaryConstructor.findDSLTypeServiceRequest(): Result<Pair<KtParameter, KtClass>> = runCatching {
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

    private fun KtClass.extractServiceResponseFields(): Result<List<PropertyInfo>> = runCatching {

        val superTypeParametertype = superTypeListEntries
            .find { it.typeReference?.text?.contains(RULE_SERVICE.typeName) == true }
            ?.typeReference
            ?.typeElement
            ?.typeArgumentsAsTypes
            ?.firstOrNull()
            ?: throw NoSuchElementException("No service response type found for $name [${containingKtFile.name}]")

        val serviceResponseClass = superTypeParametertype.resolveToKtClass(bindingContext).getOrThrow()

        if (!serviceResponseClass.isSubClassOf(RESPONSE)) {
            throw NoSuchElementException(
                String.format(
                    "%s is not subclass of %s, %s [%s]",
                    serviceResponseClass.name,
                    RESPONSE.typeName,
                    name, containingKtFile.name
                )
            )
        }

        val primaryConstructor = serviceResponseClass.primaryConstructor
            ?: throw NoSuchElementException(
                String.format(
                    "No primary constructor found for %s [%s]",
                    serviceResponseClass.name,
                    serviceResponseClass.containingKtFile.name
                )
            )

        buildList {
            add(
                PropertyInfo(
                    navn = serviceResponseClass.name!!,
                    type = serviceResponseClass.name!!,
                    beskrivelse = "Response for $name"
                )
            )
            addAll(PropertyInfo.fromPrimaryConstructor(primaryConstructor))
        }
    }

    private fun KtClass.extractRuleFlow(): Result<RuleFlowInfo> = runCatching {
        RuleFlowInfo.new(
            navn = name!!,
            beskrivelse = getKDocOrEmpty(),
            inndata = extractFlowRequestFields().getOrThrow(),
            flyt = extractRuleFlowFlow().getOrThrow(),
            gitHubUri = repo.toGithubURI(containingKtFile.name).getOrThrow()
        )
    }

    private fun KtClass.extractFlowRequestFields(): Result<List<PropertyInfo>> = runCatching {
        primaryConstructor
            ?.valueParameters
            ?.firstNotNullOfOrNull { parameter ->
                val resolvedClass = parameter
                    .typeReference
                    ?.resolveToKtClass(bindingContext)
                    ?.getOrThrow()
                    ?: throw NoSuchElementException("No type reference found")

                buildList {
                    add(fromParameter(parameter))
                    addAll(
                        resolvedClass.getProperties().map {
                            PropertyInfo.new(
                                it.name!!,
                                it.typeReference?.text ?: "Unknown",
                                it.children.filterIsInstance<KDoc>().firstOrNull()?.formatOrEmpty() ?: ""
                            )
                        }
                    )
                }
            } ?: throw NoSuchElementException(
            "No flow request fields found $name"
        )
    }

    private fun KtClass.extractRuleServiceFlow(): Result<FlowElement.Flow> = runCatching {

        val errCtx = "$name [${containingKtFile.name}]"

        val properties = body
            ?.properties
            ?: throw NoSuchElementException("No properties found, $errCtx")

        val prop = properties
            .filter { it.hasModifier(KtTokens.OVERRIDE_KEYWORD) }
            .find { it.name == DSLType.RULE_SERVICE.typeName }
            ?: throw NoSuchElementException("No override ${DSLType.RULE_SERVICE.typeName} found, $errCtx")

        prop.getLambdaBlock().flatMap { it.extractRuleServiceFlow(bindingContext) }.getOrThrow()
    }


    private fun KtClass.extractRuleFlowFlow(): Result<FlowElement.Flow> = runCatching {

        val errCtx = "$name [${containingKtFile.name}]"

        val properties = body
            ?.properties
            ?: throw NoSuchElementException("No properties found, $errCtx")

        val prop = properties
            .filter { it.hasModifier(KtTokens.OVERRIDE_KEYWORD) }
            .find { it.name == DSLType.RULE_FLOW.typeName }
            ?: throw NoSuchElementException("No override ${DSLType.RULE_FLOW.typeName} found, $errCtx")

        prop.getLambdaBlock().flatMap { it.extractRuleFlowFlow(bindingContext) }.getOrThrow()
    }

    private fun KtClass.extractRuleSet(): Result<RuleSetInfo> = runCatching {
        RuleSetInfo.new(
            navn = name!!,
            beskrivelse = getKDocOrEmpty(),
            inndata = emptyList(),
            flyt = FlowElement.Flow(emptyList()),
            gitHubUri = repo.toGithubURI(containingKtFile.name).getOrThrow()
        )
    }

}
