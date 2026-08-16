pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "LumenCrashLocal"
            url = uri("${rootDir}/local-maven")
        }
    }
}

rootProject.name = "CDict"
include(":app")
