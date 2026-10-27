package dev.jacobandersen.bastion.post.commands

import dev.jacobandersen.bastion.post.types.PostTagFilter
import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import dev.jacobandersen.microformats2.plainTextOrNull
import java.util.Locale

private const val CATEGORY_PROPERTY = "category"

/**
 * Removes the reserved `none` value from a post's categories. The value is
 * matched after trimming and case normalization because public tag filtering is
 * case-insensitive. Other category value forms are retained.
 */
internal fun Mf2Object.withoutUntaggedCategory(): Mf2Object {
    val categories = getProperty(CATEGORY_PROPERTY)
    if (categories.isEmpty()) return this

    val filtered = categories.filterNot(::isUntaggedCategory)
    return if (filtered.size == categories.size) this else setProperty(CATEGORY_PROPERTY, filtered)
}

private fun isUntaggedCategory(value: Mf2Value): Boolean = value.plainTextOrNull?.trim()?.lowercase(Locale.ROOT) == PostTagFilter.UNTAGGED
