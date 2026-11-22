package com.exc33ded.library.profile

const val NAME_MAX = 40
const val BIO_MAX = 140
const val GOAL_MAX = 1000

data class Profile(
    val name: String = "",
    val bio: String = "",
    val yearlyGoal: Int? = null,
    val avatarPath: String? = null,
)

fun clampName(s: String): String = s.trim().take(NAME_MAX)
fun clampBio(s: String): String = s.trim().take(BIO_MAX)

/** null (no goal) unless the value is a positive number; capped at [GOAL_MAX]. */
fun clampGoal(n: Int?): Int? = n?.takeIf { it >= 1 }?.coerceAtMost(GOAL_MAX)

/** Goal text field -> goal: blank, non-numeric or non-positive clears the goal. */
fun parseGoal(text: String): Int? = clampGoal(text.trim().toIntOrNull())

/** Up to two initials for the avatar placeholder; "?" for a blank name. */
fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return if (parts.isEmpty()) "?" else parts.take(2).joinToString("") { it.first().uppercase() }
}
