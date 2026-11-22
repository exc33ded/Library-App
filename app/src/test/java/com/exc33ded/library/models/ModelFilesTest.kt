package com.exc33ded.library.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ModelFilesTest {
    @get:Rule val tmp = TemporaryFolder()

    private val spec = ModelSpec("t", "T", "d", ModelKind.TEXT, "r/x", "f.litertlm", 5L, "0".repeat(64))

    @Test fun sha256OfKnownBytes() {
        val f = File(tmp.root, "abc.bin").apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256Hex(f))
    }

    @Test fun installedOnlyWhenSizeMatches() {
        assertFalse(isInstalled(tmp.root, spec))
        finalFile(tmp.root, spec).writeText("1234")        // 4 bytes, expected 5
        assertFalse(isInstalled(tmp.root, spec))
        finalFile(tmp.root, spec).writeText("12345")
        assertTrue(isInstalled(tmp.root, spec))
    }

    @Test fun partFileHasPartSuffix() = assertEquals("f.litertlm.part", partFile(tmp.root, spec).name)

    @Test fun dirSizeSumsNestedFilesAndHandlesMissingDir() {
        File(tmp.root, "a").apply { mkdirs() }.let { File(it, "x").writeText("12") }
        File(tmp.root, "y").writeText("345")
        assertEquals(5L, dirSize(tmp.root))
        assertEquals(0L, dirSize(File(tmp.root, "missing")))
    }

    @Test fun formatBytes() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("12 KB", formatBytes(12_000))
        assertEquals("343 MB", formatBytes(343_490_560))
        assertEquals("2.6 GB", formatBytes(2_588_147_712))
    }
}
