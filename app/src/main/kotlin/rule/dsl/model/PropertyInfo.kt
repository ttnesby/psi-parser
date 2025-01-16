package rule.dsl.model

import embeddable.compiler.*
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.*
import result.addons.flatMap
import result.addons.toResult

data class PropertyInfo(
    val navn: String,
    val type: String,
    val beskrivelse: String,
)

fun KtClass.toPropertyInfo(): Result<PropertyInfo> =
    requireName().map { name ->
        PropertyInfo(
            navn = name,
            type = name,
            beskrivelse = "Response for $name"
        )
    }

fun KtPrimaryConstructor.toPropertyInfo(): Result<List<PropertyInfo>> =
    valueParameters.map { it.toPropertyInfo() }.toResult()

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

fun List<KtProperty>.toPropertyInfo(): Result<List<PropertyInfo>> = map { it.toPropertyInfo() }.toResult()

// TODO - need to wrap up type management with more for;
// - KtProperty.toPropertyInfo()
// - KtExpression.extractInitializerExpression()
// - KtProperty.extractInitializer()
// - KtBinaryExpression.extractInitializer()

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
