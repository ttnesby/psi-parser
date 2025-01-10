package embeddable.compiler

import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.DescriptorToSourceUtils
import org.slf4j.LoggerFactory
import result.addons.flatMap

/**
 * A utility object for resolving Kotlin PSI elements (`KtElement`) to their corresponding descriptors or declarations
 * through a `BindingContext`. This helps in establishing the mapping between Kotlin source elements and their compiled
 * representations.
 *
 * The `BindingContextResolver` requires initialization with a valid `BindingContext` to operate, and it provides
 * functions to resolve a `KtElement` either to a `DeclarationDescriptor` or its corresponding `PsiElement` declaration.
 *
 * The prerequisite is globally constant binding context and no concerns about threading
 */

object BindingContextResolver {

    private val logger = LoggerFactory.getLogger(BindingContextResolver::class.java)

    private var bindingContext: BindingContext? = null

    fun initialize(context: BindingContext) {
        bindingContext = context
    }

    @TestOnly
    fun reset() {
        bindingContext = null
    }

//    fun printBindingContextElements() {
//        val context = bindingContext ?: run {
//            println("BindingContextResolver is not initialized")
//            return
//        }
//
//        context.getSliceContents(BindingContext.RESOLVED_CALL).forEach { (k, v) ->
//            println("Slice: ${BindingContext.RESOLVED_CALL}  |  Key: $k  =>  Value: $v")
//        }
//
//
//    }

    private fun KtElement.resolveToDescriptor(): Result<DeclarationDescriptor?> =
        bindingContext?.let { bctx ->
            when (this) {
                is KtNameReferenceExpression -> Result.success(
                    bctx[BindingContext.REFERENCE_TARGET, this]
                )

                is KtTypeReference -> Result.success(
                    bctx.get(BindingContext.TYPE, this)
                        ?.constructor
                        ?.declarationDescriptor
                )

                is KtReferenceExpression -> Result.success(
                    bctx.getType(this)
                        ?.constructor
                        ?.declarationDescriptor
                )

                else -> Result.failure(
                    IllegalArgumentException(
                        "Unsupported element type: ${this.javaClass.simpleName} for binding context resolution"
                    )
                )
            }
        } ?: Result.failure(illegalState("BindingContextResolver Singleton is not initialized"))

    fun KtElement.resolveToDeclaration(): Result<PsiElement> =
        resolveToDescriptor().flatMap { descriptor ->
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
}