package com.menulens.app.model

data class MenuPreview(
    val enTitle: String,
    val enDescription: String,
    val tags: List<String>,
    val images: List<String>
)

data class MenuItem(
    val itemId: String,
    val jpText: String,
    val priceText: String?,
    val imageGenerationToken: String? = null,
    val preview: MenuPreview
)

