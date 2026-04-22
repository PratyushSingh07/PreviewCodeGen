package com.previewcodegen.processor

import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import kotlin.random.Random

object Utils {

    const val SAMPLE_ANNOTATION = "com.previewcodegen.annotations.Sample"

    const val GENERATE_PREVIEW_ANNOTATION = "com.previewcodegen.annotations.GeneratePreview"

    fun collectTypeImports(type: KSType, imports: MutableSet<String>) {
        type.declaration.qualifiedName?.asString()?.let { qn ->
            // Don't import kotlin built-ins
            if (!qn.startsWith("kotlin.") || qn.startsWith("kotlin.collections.")) {
                imports.add(qn)
            }
        }

        // Collect generic type arguments
        type.arguments.forEach { arg ->
            arg.type?.resolve()?.let { collectTypeImports(it, imports) }
        }
    }

    fun generateParamCalls(
        parameters: List<KSValueParameter>,
        sampleClassNames: Set<String>
    ): List<String> {
        return parameters.mapNotNull { param ->
            // Skip parameters with default values unless they need sample data
            if (param.hasDefault) {
                val type = param.type.resolve()
                if (!typeNeedsSample(type, sampleClassNames)) {
                    return@mapNotNull null
                }
            }

            val paramName = param.name?.asString() ?: return@mapNotNull null
            val paramType = param.type.resolve()
            val value = getDefaultValueForType(paramType, sampleClassNames)
            "$paramName = $value"
        }
    }

    fun typeNeedsSample(type: KSType, sampleClassNames: Set<String>): Boolean {
        val qualifiedName = type.declaration.qualifiedName?.asString()
        if (qualifiedName in sampleClassNames) return true

        // Check generic arguments
        return type.arguments.any { arg ->
            arg.type?.resolve()?.let { typeNeedsSample(it, sampleClassNames) } == true
        }
    }

    fun getDefaultValueForType(type: KSType, sampleClassNames: Set<String>): String {
        val qualifiedName = type.declaration.qualifiedName?.asString()
        val simpleName = type.declaration.simpleName.asString()

        // Handle nullable types first
        if (type.isMarkedNullable) {
            return "null"
        }

        return when (qualifiedName) {
            // Primitives
            "kotlin.String" -> "\"Android\""
            "kotlin.Int", "kotlin.Long" -> Random.nextInt(0, 100).toString()
            "kotlin.Float" -> "0.4f"
            "kotlin.Double" -> "10.5"
            "kotlin.Boolean" -> Random.nextBoolean().toString()
            "kotlin.Byte" -> "0"
            "kotlin.Short" -> "0"
            "kotlin.Char" -> "'\\u0000'"

            // Collections
            "kotlin.collections.List", "java.util.List" -> {
                getCollectionDefaultValue("listOf", type, sampleClassNames)
            }

            "kotlin.collections.Set", "java.util.Set" -> {
                getCollectionDefaultValue("setOf", type, sampleClassNames)
            }

            "kotlin.collections.Map", "java.util.Map" -> "emptyMap()"
            "kotlin.collections.MutableList" -> {
                getCollectionDefaultValue("mutableListOf", type, sampleClassNames)
            }

            "kotlin.collections.MutableSet" -> {
                getCollectionDefaultValue("mutableSetOf", type, sampleClassNames)
            }

            "kotlin.collections.MutableMap" -> "mutableMapOf()"

            // Compose types
            "androidx.compose.ui.Modifier" -> "Modifier"
            "androidx.compose.ui.graphics.Color" -> "Color.Unspecified"
            "androidx.compose.ui.unit.Dp" -> "0.dp"
            "androidx.compose.ui.unit.Sp" -> "0.sp"
            "androidx.compose.ui.text.font.FontWeight" -> "FontWeight.Normal"
            "androidx.compose.ui.text.font.FontFamily" -> "FontFamily.Default"
            "androidx.compose.foundation.shape.RoundedCornerShape" -> "RoundedCornerShape(0.dp)"

            else -> {
                // Check if it's an enum
                val declaration = type.declaration
                if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.ENUM_CLASS) {
                    "$simpleName.entries.first()"
                }
                // Check if it's a @Sample annotated class
                else if (qualifiedName in sampleClassNames) {
                    "sample$simpleName()"
                }
                // Function types (lambdas)
                else if (qualifiedName?.startsWith("kotlin.Function") == true ||
                    simpleName.contains("Function") ||
                    type.isFunctionType
                ) {
                    "{}"
                }
                // Unknown type - generate a TODO
                else {
                    "TODO(\"Provide default for $simpleName\")"
                }
            }
        }
    }

    fun getCollectionDefaultValue(
        emptyFn: String,
        type: KSType,
        sampleClassNames: Set<String>
    ): String {
        val typeArg = type.arguments.firstOrNull()?.type?.resolve() ?: return "$emptyFn()"
        val argQualifiedName = typeArg.declaration.qualifiedName?.asString()
        val argSimpleName = typeArg.declaration.simpleName.asString()

        return if (argQualifiedName in sampleClassNames) {
            "$emptyFn(sample$argSimpleName())"
        } else {
            "emptyList()"
        }
    }
}