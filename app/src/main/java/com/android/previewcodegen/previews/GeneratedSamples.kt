package com.android.previewcodegen.previews

import com.android.previewcodegen.TestContainer
import com.android.previewcodegen.TestItem
import kotlin.collections.List

fun sampleTestItem(): TestItem = TestItem(
    id = 29,
    name = "Android"
)
fun sampleTestContainer(): TestContainer = TestContainer(
    items = listOf(sampleTestItem())
)