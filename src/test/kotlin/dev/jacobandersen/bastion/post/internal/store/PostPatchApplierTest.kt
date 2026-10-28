package dev.jacobandersen.bastion.post.internal.store

import dev.jacobandersen.bastion.post.commands.PostPatch
import dev.jacobandersen.microformats2.Mf2Object
import dev.jacobandersen.microformats2.Mf2Value
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class PostPatchApplierTest {
    private fun post(vararg properties: Pair<String, List<Mf2Value>>): Mf2Object =
        Mf2Object(
            type = listOf("h-entry"),
            properties = properties.toMap().toMutableMap(),
            children = null,
        )

    private fun strings(vararg values: String): List<Mf2Value> = values.map(Mf2Value::String)

    @Test
    fun `replace sets a property`() {
        val result =
            post("name" to strings("Before"))
                .applyPatch(PostPatch(replace = mapOf("name" to strings("After"))))

        assertEquals(strings("After"), result.getProperty("name"))
    }

    @Test
    fun `replace with an empty list deletes the property`() {
        val result =
            post("name" to strings("Before"))
                .applyPatch(PostPatch(replace = mapOf("name" to emptyList())))

        assertFalse(result.properties.containsKey("name"))
    }

    @Test
    fun `add appends values`() {
        val result =
            post("category" to strings("a"))
                .applyPatch(PostPatch(add = mapOf("category" to strings("b"))))

        assertEquals(strings("a", "b"), result.getProperty("category"))
    }

    @Test
    fun `remove deletes only the requested values`() {
        val result =
            post("category" to strings("a", "b", "c"))
                .applyPatch(PostPatch(remove = mapOf("category" to strings("b"))))

        assertEquals(strings("a", "c"), result.getProperty("category"))
    }

    @Test
    fun `remove deletes the property when all values are removed`() {
        val result =
            post("category" to strings("a", "b"))
                .applyPatch(PostPatch(remove = mapOf("category" to strings("a", "b"))))

        assertFalse(result.properties.containsKey("category"))
    }

    @Test
    fun `removeAll deletes whole properties`() {
        val result =
            post("name" to strings("Before"), "content" to strings("Body"))
                .applyPatch(PostPatch(removeAll = setOf("name")))

        assertFalse(result.properties.containsKey("name"))
        assertEquals(strings("Body"), result.getProperty("content"))
    }

    @Test
    fun `operations apply in replace add remove removeAll order`() {
        val result =
            post("name" to strings("Before"), "category" to strings("a"))
                .applyPatch(
                    PostPatch(
                        replace = mapOf("name" to strings("After")),
                        add = mapOf("category" to strings("b")),
                        remove = mapOf("category" to strings("a")),
                        removeAll = setOf("name"),
                    ),
                )

        assertFalse(result.properties.containsKey("name"))
        assertEquals(strings("b"), result.getProperty("category"))
    }

    @Test
    fun `an empty patch leaves the document unchanged`() {
        val original = post("name" to strings("Before"))

        val result = original.applyPatch(PostPatch())

        assertEquals(original, result)
    }
}
