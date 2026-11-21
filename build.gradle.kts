// AGP 9 has built-in Kotlin; this classpath entry pins KGP to 2.4.21
// (LiteRT-LM in Phase 3 needs Kotlin >= 2.3).
buildscript {
    dependencies { classpath(libs.kotlin.gradle.plugin) }
}
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
}
