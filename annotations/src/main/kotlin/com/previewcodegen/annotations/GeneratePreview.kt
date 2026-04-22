package com.previewcodegen.annotations

import kotlin.reflect.KClass

/**
 * Annotate @Composable functions with @GeneratePreview to generate:
 * 1. A preview composable with @Preview annotation
 * 2. An enum entry in GeneratedComposableItem
 *
 * The processor will use @Sample annotated data classes to provide
 * sample data for composable parameters.
 *
 * Example:
 * ```
 * @GeneratePreview(category = "Data Visualization")
 * @Composable
 * fun TimelineUiWithGlow(
 *     modifier: Modifier = Modifier,
 *     data: List<TimelineUiLayer>  // Must have @Sample on TimelineUiLayer
 * ) { ... }
 *
 * // Generated Preview:
 * @Preview
 * @Composable
 * fun TimelineUiWithGlowPreview() {
 *     TimelineUiWithGlow(
 *         data = listOf(sampleTimelineUiLayer())
 *     )
 * }
 *
 * // Generated Enum Entry:
 * enum class GeneratedComposableItem {
 *     TIMELINE_UI_WITH_GLOW("TimelineUiWithGlow", "Data Visualization")
 * }
 * ```
 *
 * @param category The category for grouping in the UI catalog
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
annotation class GeneratePreview(
    val category: String = "Uncategorized"
)
