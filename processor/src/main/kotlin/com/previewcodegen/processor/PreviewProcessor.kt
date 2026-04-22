package com.previewcodegen.processor

import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.previewcodegen.processor.Utils.GENERATE_PREVIEW_ANNOTATION
import com.previewcodegen.processor.Utils.SAMPLE_ANNOTATION
import com.previewcodegen.processor.Utils.collectTypeImports
import com.previewcodegen.processor.Utils.generateParamCalls
import com.previewcodegen.processor.Utils.getDefaultValueForType
import com.previewcodegen.processor.Utils.typeNeedsSample
import java.io.File

class PreviewProcessor(
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    private val outputDir: String?
        get() = options["outputDir"]

    /**
     * `outputPackage` is defined as a ksp argument in app/build.gradle
     */
    private val outputPackage: String
        get() = options["outputPackage"] ?: "com.android.previewcodegen"

    private val previewsPackage: String
        get() = "$outputPackage.previews"

    private val samplesPackage: String
        get() = previewsPackage

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
        logger.info("Output directory: $outputDir")
        logger.info("Output package: $outputPackage")

        if (sampleClasses.isNotEmpty()) {
            generateSampleFunctions(sampleClasses)
        }

        if (previewFunctions.isNotEmpty()) {
            generatePreviews(previewFunctions, sampleClasses)
        }

        return emptyList()
    }

    private fun generateSampleFunctions(sampleClasses: List<KSClassDeclaration>) {
        val imports = mutableSetOf<String>()
        val sampleFunctions = StringBuilder()

        val sampleClassNames = sampleClasses.mapNotNull { it.qualifiedName?.asString() }.toSet()

        sampleClasses.forEach { sampleClass ->
            val className = sampleClass.simpleName.asString()
            val qualifiedName = sampleClass.qualifiedName?.asString() ?: return@forEach

            imports.add(qualifiedName)

            sampleClass.primaryConstructor?.parameters?.forEach { param ->
                collectTypeImports(param.type.resolve(), imports)
            }

            val functionBody = generateSampleFunctionBody(sampleClass, sampleClassNames)
            sampleFunctions.append(
                """
                |
                |fun sample$className(): $className = $functionBody
            """.trimMargin()
            )
        }

        val content = buildString {
            appendLine("package $samplesPackage")
            appendLine()
            imports.sorted().forEach { appendLine("import $it") }
            append(sampleFunctions)
        }

        writeToSourceDir(samplesPackage, "GeneratedSamples", content)
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

        val needsSamples = func.parameters.any { param ->
            val type = param.type.resolve()
            typeNeedsSample(type, sampleClassNames)
        }
        if (needsSamples) {
            imports.add("$samplesPackage.*")
        }

        func.parameters.forEach { param ->
            collectTypeImports(param.type.resolve(), imports)
        }

        val paramCalls = generateParamCalls(func.parameters, sampleClassNames)

        val content = buildString {
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
        }

        writeToSourceDir(previewsPackage, "${functionName}Preview", content)
        logger.info("Generated ${functionName}Preview.kt")
    }

    private fun writeToSourceDir(packageName: String, fileName: String, content: String) {
        val outputDirPath = outputDir
        if (outputDirPath == null) {
            logger.error("outputDir KSP option is not set. Add ksp { arg(\"outputDir\", \"...\") } to build.gradle.kts")
            return
        }

        val packageDir = packageName.replace('.', File.separatorChar)
        val fullDir = File(outputDirPath, packageDir)

        if (!fullDir.exists()) {
            fullDir.mkdirs()
        }

        val file = File(fullDir, "$fileName.kt")
        file.writeText(content)
        logger.info("Wrote file: ${file.absolutePath}")
    }
}

class PreviewProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return PreviewProcessor(
            logger = environment.logger,
            options = environment.options
        )
    }
}
