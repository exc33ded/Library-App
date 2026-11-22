package com.exc33ded.library.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogTest {
    @Test fun idsAreUnique() = assertEquals(ModelCatalog.all.size, ModelCatalog.all.map { it.id }.toSet().size)

    @Test fun everyEntryIsWellFormed() = ModelCatalog.all.forEach { m ->
        assertTrue(m.id, m.url.startsWith("https://huggingface.co/") && m.url.endsWith(m.fileName))
        assertTrue(m.id, Regex("[0-9a-f]{64}").matches(m.sha256))
        assertTrue(m.id, m.sizeBytes > 100_000_000L)
        assertTrue(m.id, m.fileName.endsWith(".litertlm"))
    }

    @Test fun exactlyOneRecommendedAndItIsText() {
        val rec = ModelCatalog.all.filter { it.recommended }
        assertEquals(1, rec.size)
        assertEquals(ModelKind.TEXT, rec.single().kind)
    }

    @Test fun lookupById() {
        assertEquals("Qwen3 0.6B", ModelCatalog.byId("qwen3-0.6b")?.name)
        assertNull(ModelCatalog.byId("nope"))
    }
}
