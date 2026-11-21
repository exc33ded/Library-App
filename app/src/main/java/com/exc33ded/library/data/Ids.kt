package com.exc33ded.library.data

import java.util.UUID

object Ids {
    fun new(): String = UUID.randomUUID().toString()

    /** Same (a, b) -> same id on every device, so offline link edits don't duplicate after sync. */
    fun pair(a: String, b: String): String = UUID.nameUUIDFromBytes("$a|$b".toByteArray()).toString()
}
