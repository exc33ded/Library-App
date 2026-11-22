package com.exc33ded.library.profile

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarStoreTest {
    @Test fun landscapeCropsTheSides() = assertEquals(Triple(100, 0, 200), centerSquare(400, 200))
    @Test fun portraitCropsTopAndBottom() = assertEquals(Triple(0, 150, 200), centerSquare(200, 500))
    @Test fun squareIsUntouched() = assertEquals(Triple(0, 0, 300), centerSquare(300, 300))
}
