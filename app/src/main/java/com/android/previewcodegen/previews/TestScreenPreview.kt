package com.android.previewcodegen.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.previewcodegen.TestContainer
import com.android.previewcodegen.TestScreen
import com.android.previewcodegen.previews.*

@Preview
@Composable
fun TestScreenPreview() {
    TestScreen(
        data = sampleTestContainer()
    )
}
