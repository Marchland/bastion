package dev.jacobandersen.bastion.post.metadata

data class PostTypeView(
    val type: String,
    val name: String,
    val h: String,
    val properties: List<String>,
    val requiredProperties: List<String>,
)

data class PostPropertyView(
    val name: String,
    val displayName: String,
    val hints: List<String>,
    val options: List<String>?,
    val defaultValue: String?,
)
