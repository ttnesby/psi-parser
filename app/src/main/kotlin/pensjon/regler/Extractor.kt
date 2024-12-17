package pensjon.regler

import embeddable.compiler.CompilerContext
import org.example.*
import org.example.DSLTypeAbstract.*
import org.example.DSLTypeAbstractResult.Found
import org.example.DSLTypeAbstractResult.NOTFound
import org.example.DSLTypeFlow.FLOW
import org.example.DSLTypeFlow.SERVICE
import org.example.DSLTypeService.REQUEST
import org.example.DSLTypeService.RESPONSE
import org.jetbrains.kotlin.com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.psiUtil.containingClass
import org.jetbrains.kotlin.resolve.BindingContext
import pensjon.regler.PropertyInfo.Companion.fromParameter
import pensjon.regler.PropertyInfo.Companion.fromProperties
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
            println("Building binding context for ${psiFiles.size} files\n")
            val bindingContext = context.buildBindingContext(psiFiles).getOrThrow()

            Extractor(repo, psiFiles, bindingContext)
        }
    }

    fun toModel(): Result<List<RuleInfo>> {
        val resultList = mutableListOf<RuleInfo>()

        psiFiles
            .asSequence()
            .map { file ->
                file.findDSLTypeAbstract().flatMap { result ->
                    when (result) {
                        is Found -> {
                            when (result.dslType) {
                                RULE_SERVICE -> result.ktClass.extractRuleService()
                                RULE_FLOW -> result.ktClass.extractRuleFlow()
                                RULE_SET -> result.ktClass.extractRuleSet()
                            }
                        }

                        NOTFound -> Result.failure(IllegalStateException("No DSL type found in file"))
                    }.also {
                        (file as PsiFileImpl).clearCaches()
                    }
                }
            }
            .forEach { item ->
                item.onSuccess { rule -> resultList.add(rule) }
            }

        return Result.success(resultList)
    }


    private fun KtClass.extractRuleService(): Result<RuleServiceInfo> = runCatching {
        RuleServiceInfo(
            navn = name!!,
            beskrivelse = getKDocOrEmpty(),
            inndata = extractServiceRequestFields().getOrThrow(),
            utdata = extractServiceResponseFields().getOrThrow(),
            flyt = extractFlow(SERVICE).getOrThrow(),
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

        val abstractRuleServiceGenericParameterType = superTypeListEntries
            .find { it.typeReference?.text?.contains(RULE_SERVICE.typeName) == true }
            ?.typeReference
            ?.typeElement
            ?.typeArgumentsAsTypes
            ?.firstOrNull()
            ?: throw NoSuchElementException("No service response type found for $name [${containingKtFile.name}]")

        val serviceResponseClass = abstractRuleServiceGenericParameterType
            .resolveToKtClass(bindingContext).getOrThrow()

        if (!serviceResponseClass.isSubClassOf(RESPONSE)) {
            throw NoSuchElementException(
                String.format(
                    "%s is not subclass of %s, %s [%s]",
                    serviceResponseClass.name,
                    RESPONSE.typeName,
                    name,
                    containingKtFile.name
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
        RuleFlowInfo(
            navn = name!!,
            beskrivelse = getKDocOrEmpty(),
            inndata = extractFlowRequestFields().getOrThrow(),
            flyt = extractFlow(FLOW).getOrThrow(),
            gitHubUri = repo.toGithubURI(containingKtFile.name).getOrThrow()
        )
    }

    private fun KtClass.extractFlowRequestFields(): Result<List<PropertyInfo>> = runCatching {

        val errCtx = "$name [${containingKtFile.name}]"

        val primaryConstructor = primaryConstructor
            ?: throw NoSuchElementException(
                "No primary constructor found for $errCtx"
            )

        val (parameter, parameterClass) = primaryConstructor
            .valueParameters
            .firstNotNullOfOrNull { parameter ->
                parameter
                    .typeReference
                    ?.resolveToKtClass(bindingContext)
                    ?.getOrNull()
                    ?.let { resolvedClass -> Pair(parameter, resolvedClass) }
            } ?: throw NoSuchElementException(
            "No flow parameter of type class found in primary constructor for $errCtx"
        )

        buildList {
            add(fromParameter(parameter))
            addAll(fromProperties(parameterClass.getProperties()))
        }
    }

    private fun KtClass.extractFlow(flowType: DSLTypeFlow): Result<FlowElement.Flow> = runCatching {

        val errCtx = "$name [${containingKtFile.name}]"

        val properties = body
            ?.properties
            ?: throw NoSuchElementException("No properties found, $errCtx")

        val prop = properties
            .filter { it.hasModifier(KtTokens.OVERRIDE_KEYWORD) }
            .find { it.name == flowType.typeName }
            ?: throw NoSuchElementException("No override ${flowType.typeName} found, $errCtx")

        prop.getLambdaBlock().flatMap {
            when (flowType) {
                SERVICE -> it.extractRuleServiceFlow(bindingContext)
                FLOW -> it.extractRuleFlowFlow(bindingContext)
            }
        }.getOrThrow()
    }

    private fun KtClass.extractRuleSet(): Result<RuleSetInfo> = runCatching {
        RuleSetInfo(
            navn = name!!,
            beskrivelse = getKDocOrEmpty(),
            inndata = emptyList(),
            flyt = FlowElement.Flow(emptyList()),
            gitHubUri = repo.toGithubURI(containingKtFile.name).getOrThrow()
        )
    }

}
