package com.previewcodegen.processor

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration

class PreviewProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
    private val options: Map<String, String>
) : SymbolProcessor {

    companion object {
        private const val SAMPLE_ANNOTATION = "com.previewcodegen.annotations.Sample"
        private const val GENERATE_PREVIEW_ANNOTATION = "com.previewcodegen.annotations.GeneratePreview"
    }

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

        logger.warn("Found ${sampleClasses.size} @Sample classes")
        logger.warn("Found ${previewFunctions.size} @GeneratePreview functions")

        // TODO: Process @Sample classes and generate sample functions
        // For each @Sample class:
        // 1. Get the class name and package
        // 2. Get all constructor parameters
        // 3. For each parameter, determine the default value:
        //    - String -> ""
        //    - Int, Long, Float, Double -> 0
        //    - Boolean -> false
        //    - List<T> -> emptyList() or listOf(sampleT()) if T has @Sample
        //    - Nullable -> null
        //    - Another @Sample class -> call its sample function
        //    - Enum -> first enum value
        // 4. Generate: fun sampleClassName() = ClassName(param1 = default1, ...)

        // TODO: Process @GeneratePreview functions and generate:
        // 1. Preview composables with @Preview annotation
        // 2. GeneratedComposableItem enum entries
        // 3. RenderGeneratedComposable function

        return emptyList()
    }
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
