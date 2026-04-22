package com.previewcodegen.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter

class PreviewProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    companion object {
        private const val SAMPLE_ANNOTATION = "com.previewcodegen.annotations.Sample"
        private const val GENERATE_PREVIEW_ANNOTATION = "com.previewcodegen.annotations.GeneratePreview"

        // Output package names - can be customized via KSP options
        private const val DEFAULT_SAMPLES_PACKAGE = "com.previewcodegen.generated.samples"
        private const val DEFAULT_PREVIEWS_PACKAGE = "com.previewcodegen.generated.previews"
    }

    private val samplesPackage: String
        get() = options["samplesPackage"] ?: DEFAULT_SAMPLES_PACKAGE

    private val previewsPackage: String
        get() = options["previewsPackage"] ?: DEFAULT_PREVIEWS_PACKAGE

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val sampleClasses = resolver
            .getSymbolsWithAnnotation(SAMPLE_ANNOTATION)
            .filterIsInstance<KSClassDeclaration>()
            .toList()

        val previewFunctions = resolver
            .getSymbolsWithAnnotation(GENERATE_PREVIEW_ANNOTATION)
            .filterIsInstance<KSFunctionDeclaration>()
            .toList()

        if (sampleClasses.isEmpty() && previewFunctions.isEmpty()) {
            return emptyList()
        }

        logger.info("Processing ${sampleClasses.size} @Sample classes")
        logger.info("Processing ${previewFunctions.size} @GeneratePreview functions")

        // Step 1: Generate sample functions for @Sample classes
        if (sampleClasses.isNotEmpty()) {
            generateSampleFunctions(sampleClasses)
        }

        // Step 2: Generate previews, enum, and render function for @GeneratePreview functions
        if (previewFunctions.isNotEmpty()) {
            generatePreviews(previewFunctions, sampleClasses)
            generateComposableItemEnum(previewFunctions)
            generateRenderFunction(previewFunctions, sampleClasses)
        }

        return emptyList()
    }

    // ========== SAMPLE GENERATION ==========

    private fun generateSampleFunctions(sampleClasses: List<KSClassDeclaration>) {
        val imports = mutableSetOf<String>()
        val sampleFunctions = StringBuilder()

        // Collect all sample class qualified names for reference
        val sampleClassNames = sampleClasses.mapNotNull { it.qualifiedName?.asString() }.toSet()

        sampleClasses.forEach { sampleClass ->
            val className = sampleClass.simpleName.asString()
            val qualifiedName = sampleClass.qualifiedName?.asString() ?: return@forEach

            // Add import for the class
            imports.add(qualifiedName)

            // Collect imports from constructor parameters
            sampleClass.primaryConstructor?.parameters?.forEach { param ->
                collectTypeImports(param.type.resolve(), imports)
            }

            // Generate sample function
            val functionBody = generateSampleFunctionBody(sampleClass, sampleClassNames)
            sampleFunctions.append("""
                |
                |fun sample$className(): $className = $functionBody
            """.trimMargin())
        }

        // Write the samples file
        val sourceFiles = sampleClasses.mapNotNull { it.containingFile }.toTypedArray()
        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = true, sources = sourceFiles),
            packageName = samplesPackage,
            fileName = "GeneratedSamples"
        )

        file.bufferedWriter().use { writer ->
            writer.write(buildString {
                appendLine("package $samplesPackage")
                appendLine()
                imports.sorted().forEach { appendLine("import $it") }
                append(sampleFunctions)
            })
        }

        logger.info("Generated GeneratedSamples.kt with ${sampleClasses.size} sample functions")
    }

    private fun generateSampleFunctionBody(
        classDecl: KSClassDeclaration,
        sampleClassNames: Set<String>
    ): String {
        val className = classDecl.simpleName.asString()
        val params = classDecl.primaryConstructor?.parameters ?: return "$className()"

        if (params.isEmpty()) return "$className()"

        val paramStrings = params.mapNotNull { param ->
            val paramName = param.name?.asString() ?: return@mapNotNull null
            val paramType = param.type.resolve()
            val defaultValue = getDefaultValueForType(paramType, sampleClassNames)
            "    $paramName = $defaultValue"
        }

        return buildString {
            appendLine("$className(")
            appendLine(paramStrings.joinToString(",\n"))
            append(")")
        }
    }

    private fun getDefaultValueForType(type: KSType, sampleClassNames: Set<String>): String {
        val qualifiedName = type.declaration.qualifiedName?.asString()
        val simpleName = type.declaration.simpleName.asString()

        // Handle nullable types first
        if (type.isMarkedNullable) {
            return "null"
        }

        return when (qualifiedName) {
            // Primitives
            "kotlin.String" -> "\"\""
            "kotlin.Int" -> "0"
            "kotlin.Long" -> "0L"
            "kotlin.Float" -> "0f"
            "kotlin.Double" -> "0.0"
            "kotlin.Boolean" -> "false"
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

    private fun getCollectionDefaultValue(
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

    private fun collectTypeImports(type: KSType, imports: MutableSet<String>) {
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

    // ========== PREVIEW GENERATION ==========

    private fun generatePreviews(
        previewFunctions: List<KSFunctionDeclaration>,
        sampleClasses: List<KSClassDeclaration>
    ) {
        val sampleClassNames = sampleClasses.mapNotNull { it.qualifiedName?.asString() }.toSet()

        previewFunctions.forEach { func ->
            generatePreviewForFunction(func, sampleClassNames)
        }
    }

    private fun generatePreviewForFunction(
        func: KSFunctionDeclaration,
        sampleClassNames: Set<String>
    ) {
        val functionName = func.simpleName.asString()
        val originalPackage = func.packageName.asString()

        val imports = mutableSetOf(
            "androidx.compose.runtime.Composable",
            "androidx.compose.ui.tooling.preview.Preview",
            "$originalPackage.$functionName"
        )

        // Add samples package import if needed
        val needsSamples = func.parameters.any { param ->
            val type = param.type.resolve()
            typeNeedsSample(type, sampleClassNames)
        }
        if (needsSamples) {
            imports.add("$samplesPackage.*")
        }

        // Collect imports from parameter types
        func.parameters.forEach { param ->
            collectTypeImports(param.type.resolve(), imports)
        }

        // Generate parameter calls
        val paramCalls = generateParamCalls(func.parameters, sampleClassNames)

        val sourceFile = func.containingFile
        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(
                aggregating = false,
                sources = sourceFile?.let { arrayOf(it) } ?: emptyArray()
            ),
            packageName = previewsPackage,
            fileName = "${functionName}Preview"
        )

        file.bufferedWriter().use { writer ->
            writer.write(buildString {
                appendLine("package $previewsPackage")
                appendLine()
                imports.filter { !it.startsWith("kotlin.") || it.startsWith("kotlin.collections.") }
                    .sorted()
                    .forEach { appendLine("import $it") }
                appendLine()
                appendLine("@Preview")
                appendLine("@Composable")
                appendLine("fun ${functionName}Preview() {")
                if (paramCalls.isEmpty()) {
                    appendLine("    $functionName()")
                } else {
                    appendLine("    $functionName(")
                    appendLine(paramCalls.joinToString(",\n") { "        $it" })
                    appendLine("    )")
                }
                appendLine("}")
            })
        }

        logger.info("Generated ${functionName}Preview.kt")
    }

    private fun generateParamCalls(
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

    private fun typeNeedsSample(type: KSType, sampleClassNames: Set<String>): Boolean {
        val qualifiedName = type.declaration.qualifiedName?.asString()
        if (qualifiedName in sampleClassNames) return true

        // Check generic arguments
        return type.arguments.any { arg ->
            arg.type?.resolve()?.let { typeNeedsSample(it, sampleClassNames) } == true
        }
    }

    // ========== ENUM GENERATION ==========

    private fun generateComposableItemEnum(previewFunctions: List<KSFunctionDeclaration>) {
        val enumEntries = previewFunctions.map { func ->
            val functionName = func.simpleName.asString()
            val enumName = camelToSnakeCase(functionName).uppercase()
            val category = getCategory(func)

            EnumEntry(enumName, functionName, category)
        }

        val sourceFiles = previewFunctions.mapNotNull { it.containingFile }.toTypedArray()
        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = true, sources = sourceFiles),
            packageName = previewsPackage,
            fileName = "GeneratedComposableItem"
        )

        file.bufferedWriter().use { writer ->
            writer.write(buildString {
                appendLine("package $previewsPackage")
                appendLine()
                appendLine("enum class GeneratedComposableItem(val displayName: String, val category: String) {")
                enumEntries.forEachIndexed { index, entry ->
                    val separator = if (index < enumEntries.lastIndex) "," else ";"
                    appendLine("    ${entry.enumName}(\"${entry.displayName}\", \"${entry.category}\")$separator")
                }
                appendLine()
                appendLine("    companion object {")
                appendLine("        fun getGroupedItems(): Map<String, List<GeneratedComposableItem>> {")
                appendLine("            return entries.groupBy { it.category }")
                appendLine("        }")
                appendLine("    }")
                appendLine("}")
            })
        }

        logger.info("Generated GeneratedComposableItem.kt with ${enumEntries.size} entries")
    }

    private fun getCategory(func: KSFunctionDeclaration): String {
        val annotation = func.annotations.firstOrNull { anno ->
            anno.annotationType.resolve().declaration.qualifiedName?.asString() == GENERATE_PREVIEW_ANNOTATION
        } ?: return "Uncategorized"

        return annotation.arguments
            .firstOrNull { it.name?.asString() == "category" }
            ?.value as? String ?: "Uncategorized"
    }

    private fun camelToSnakeCase(str: String): String {
        return str.fold(StringBuilder()) { acc, c ->
            if (c.isUpperCase() && acc.isNotEmpty()) {
                acc.append('_')
            }
            acc.append(c)
            acc
        }.toString()
    }

    // ========== RENDER FUNCTION GENERATION ==========

    private fun generateRenderFunction(
        previewFunctions: List<KSFunctionDeclaration>,
        sampleClasses: List<KSClassDeclaration>
    ) {
        val sampleClassNames = sampleClasses.mapNotNull { it.qualifiedName?.asString() }.toSet()

        val imports = mutableSetOf(
            "androidx.compose.runtime.Composable",
            "$samplesPackage.*"
        )

        // Collect imports from all functions
        previewFunctions.forEach { func ->
            val originalPackage = func.packageName.asString()
            val functionName = func.simpleName.asString()
            imports.add("$originalPackage.$functionName")

            func.parameters.forEach { param ->
                collectTypeImports(param.type.resolve(), imports)
            }
        }

        // Generate when branches
        val whenBranches = previewFunctions.map { func ->
            val functionName = func.simpleName.asString()
            val enumName = camelToSnakeCase(functionName).uppercase()
            val paramCalls = generateParamCalls(func.parameters, sampleClassNames)

            val call = if (paramCalls.isEmpty()) {
                "$functionName()"
            } else {
                buildString {
                    appendLine("$functionName(")
                    appendLine(paramCalls.joinToString(",\n") { "                $it" })
                    append("            )")
                }
            }

            "        GeneratedComposableItem.$enumName -> {\n            $call\n        }"
        }

        val sourceFiles = previewFunctions.mapNotNull { it.containingFile }.toTypedArray()
        val file = codeGenerator.createNewFile(
            dependencies = Dependencies(aggregating = true, sources = sourceFiles),
            packageName = previewsPackage,
            fileName = "RenderGeneratedComposable"
        )

        file.bufferedWriter().use { writer ->
            writer.write(buildString {
                appendLine("package $previewsPackage")
                appendLine()
                imports.filter { !it.startsWith("kotlin.") || it.startsWith("kotlin.collections.") }
                    .sorted()
                    .forEach { appendLine("import $it") }
                appendLine()
                appendLine("@Composable")
                appendLine("fun RenderGeneratedComposable(item: GeneratedComposableItem) {")
                appendLine("    when (item) {")
                whenBranches.forEach { appendLine(it) }
                appendLine("    }")
                appendLine("}")
            })
        }

        logger.info("Generated RenderGeneratedComposable.kt")
    }

    private data class EnumEntry(
        val enumName: String,
        val displayName: String,
        val category: String
    )
}

class PreviewProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return PreviewProcessor(
            codeGenerator = environment.codeGenerator,
            logger = environment.logger,
            options = environment.options
        )
    }
}
