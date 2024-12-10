package org.example

import kotlin.io.path.*

//@TestOnly
//fun analyzeSourceFilesTest(
//    sourceFiles: List<KtFile>,
//    bindingContext: BindingContext,
//): List<RuleServiceDoc> =
//    sourceFiles.chunked(100).flatMap { batch ->
//        batch.mapNotNull { file ->
//            getRuleService(file, bindingContext).getOrNull().also {
//                (file as PsiFileImpl).clearCaches()
//            }
//        }
//    }

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


// TODO: Denne kan trolig skrives om. Må finne en god måte å håndtere GithubUrl for tester.
fun String.convertToGitHubUrl(): String =
    this.indexOf("pensjon-regler")
        .takeIf { it != -1 }
        ?.let { index ->
            "https://github.com/navikt/" + this.substring(index)
                .replace('\\', '/')
                .replaceFirst("pensjon-regler/", "pensjon-regler/blob/master/")
        } ?: "https://github.com/navikt/$this"