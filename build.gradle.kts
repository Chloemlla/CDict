buildscript {
    dependencies {
        // Force a KGP newer than AGP 9.x's bundled floor (2.2.10) so the
        // Compose compiler plugin (2.4.10) matches the Kotlin compiler under
        // AGP's built-in Kotlin.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
    }
}

plugins {
    id("com.android.application") version "9.3.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    id("com.google.devtools.ksp") version "2.3.11" apply false
}
