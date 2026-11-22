# R8 rules for the release build. Libraries (Room, Hilt, Ktor, Coil, WorkManager, ML Kit, kotlinx.serialization)
# ship their own consumer rules; these cover what this app adds on top.

-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, RuntimeVisibleAnnotations

# kotlinx.serialization: navigation keys, backup/feed DTOs and the import-queue JSON are looked up by class.
-keepclassmembers class com.exc33ded.library.** { *** Companion; }
-keepclasseswithmembers class com.exc33ded.library.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.exc33ded.library.**$$serializer { *; }

# Navigation 3 saves/restores back-stack keys polymorphically by class name.
-keep class com.exc33ded.library.ui.nav.** { *; }

# Debug-only receivers are declared in the debug manifest; nothing to keep in release.

# Optional logging backends referenced by Ktor.
-dontwarn org.slf4j.**
-dontwarn javax.annotation.**
