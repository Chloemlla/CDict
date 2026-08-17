import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

fun propertyOrEnvironment(name: String): String? =
    providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

// Build identity injected by CI for the 关于 page (Commit Hash / Build Time).
// Local and test builds fall back to N/A / 0, which the UI treats as a dev build.
val cdictCommitHash: String = propertyOrEnvironment("CDICT_COMMIT_HASH") ?: "N/A"
val cdictBuildTimeSeconds: Long =
    propertyOrEnvironment("CDICT_BUILD_TIME")?.toLongOrNull()?.takeIf { it > 0 } ?: 0L

// CI materializes the lumen-crash SDK via scripts/fetch-lumen-crash-sdk.py and writes the
// resolved version to lumen-crash.resolved.version; gradle.properties is a local fallback.
val lumenCrashSdkVersion: String =
    rootProject.file("lumen-crash.resolved.version").takeIf { it.isFile }?.readText()
        ?.trim()?.takeIf { it.isNotEmpty() }
        ?: propertyOrEnvironment("lumenCrashVersion")
        ?: error(
            "lumen-crash SDK version is unresolved: run scripts/fetch-lumen-crash-sdk.py " +
                "or set lumenCrashVersion in gradle.properties"
        )

val releaseKeystoreFile = propertyOrEnvironment("KEYSTORE_FILE")
val releaseKeystorePassword = propertyOrEnvironment("KEYSTORE_PASSWORD")
val releaseKeyAlias = propertyOrEnvironment("KEY_ALIAS")
val releaseKeyPassword = propertyOrEnvironment("KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseKeystoreFile,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.chloemlla.cdict"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.chloemlla.cdict"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"
        buildConfigField("String", "COMMIT_HASH", "\"${cdictCommitHash.replace("\"", "\\\"")}\"")
        buildConfigField("long", "BUILD_TIME", "${cdictBuildTimeSeconds}L")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        resourceConfigurations += listOf("zh", "en")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystoreFile))
                storePassword = requireNotNull(releaseKeystorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            // Distinct applicationId so a debug build can be sideloaded alongside
            // the installed official app without conflicting package names.
            applicationIdSuffix = ".debug"
            // Sign the debug build with the release keystore in CI so the emitted
            // debug APK carries a trusted, reproducible signature (not the random
            // default debug key). Locally (no secrets) it falls back to the debug key.
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // AAB cannot coexist with ABI splits + resource shrink in one buildType:
        // AGP writes one shrunk-resources file per split, and buildReleasePreBundle
        // requires a single one (IllegalStateException). Produce the AAB from a
        // dedicated buildType with shrink off; Play performs resource shrinking
        // per device at serve time.
        create("releaseAab") {
            isMinifyEnabled = true
            isShrinkResources = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    lint {
        abortOnError = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

// Room exports the schema to a shared project dir per database. `release` and
// `releaseAab` both run KSP and would concurrently read/write that file, racing
// ("Expected end of object, but had EOF"). Force the AAB variant's KSP to run
// strictly after release's so the file is complete before it is read. Room
// schema export is not a declared task output, so Gradle cannot know they
// conflict and would otherwise parallelize them.
tasks.configureEach {
    if (name == "kspReleaseAabKotlin") {
        mustRunAfter("kspReleaseKotlin")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    ksp("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.10")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("org.brotli:dec:0.1.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.chloemlla.lumen:lumen-crash:$lumenCrashSdkVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.json:json:20260814")
    testImplementation("androidx.test:core-ktx:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
