package com.exc33ded.library.metadata

import org.junit.Assert.assertEquals
import org.junit.Test

class CoverSizeTest {
    @Test fun landscapeIsScaledDown() = assertEquals(1200 to 900, coverTargetSize(4096, 3072))
    @Test fun smallImageIsNotUpscaled() = assertEquals(800 to 600, coverTargetSize(800, 600))
    @Test fun portraitIsScaledDown() = assertEquals(900 to 1200, coverTargetSize(3000, 4000))
}
