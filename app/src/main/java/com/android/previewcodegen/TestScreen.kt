package com.android.previewcodegen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.previewcodegen.annotations.GeneratePreview
import com.previewcodegen.annotations.Sample

@Sample
data class TestItem(val id: Int, val name: String)

@Sample
data class TestContainer(val items: List<TestItem>)

@Composable
@GeneratePreview(category = "Misc")
fun TestScreen(
    data: TestContainer
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        containerColor = Color.Transparent
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(it)
        ) {
            items(data.items) { item ->
                Text(
                    text = item.name,
                    color = Color.White,
                    fontSize = 15.sp
                )
            }
        }
    }
}
