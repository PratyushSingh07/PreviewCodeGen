package com.previewcodegen.annotations

/**
 * Annotate data classes with @Sample to generate sample data functions.
 *
 * The processor will generate a `sampleClassName()` function that creates
 * an instance with default values for all parameters.
 *
 * For nested types that are also annotated with @Sample, the generated
 * function will call their respective sample functions.
 *
 * Example:
 * ```
 * @Sample
 * data class TimelineUiLayer(
 *     val title: String,
 *     val segments: List<TimelineSegment>
 * )
 *
 * // Generated:
 * fun sampleTimelineUiLayer() = TimelineUiLayer(
 *     title = "",
 *     segments = listOf(sampleTimelineSegment())
 * )
 * ```
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Sample
