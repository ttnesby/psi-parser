package org.example

import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

/** Get class of super type from KtFile */
fun KtFile.getClassOfSuperType(superTypeRef: (KtClass) -> Boolean): Result<KtClass> = runCatching {
    declarations.asSequence().filterIsInstance<KtClass>().firstOrNull(superTypeRef)
            ?: throw NoSuchElementException("No class found with specified supertype")
}

// functions to be superTypeRef parameter in getClassOfSuperType
//
fun isRuleServiceClass(klass: KtClass): Boolean =
        klass.getSuperTypeListEntries().any {
            it.typeReference?.text?.contains("AbstractPensjonRuleService") == true
        }

/** Get the KDoc from a class */
fun KtClass.getKDocOrEmpty(): String =
        docComment?.text?.let { kdoc ->
            kdoc.lines()
                    .map { it.trim().removePrefix("*").trim() }
                    .filter { it.isNotEmpty() && it != "/" } // Add filter for lone "/"
                    .joinToString("\n")
                    .removePrefix("/**")
                    .removeSuffix("*/")
                    .trim()
        }
                ?: ""
