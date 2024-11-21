package org.example

const val FILE_NAME = "app/src/test/kotlin/org/example/Test.kt"
const val DEFAULT_PATH = "app/src/test/kotlin/org/example/"

data class SourceCode(val code: String, val fileName: String = FILE_NAME)
