pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://api.xposed.info/")
        maven("https://jitpack.io")
    }
}

rootProject.name = "EdgeX"
include(":app")
include(":premium-api")

// Premium implementation modules are proprietary and not included in this repo.
// When the directory is present locally, it is included automatically
// so ./gradlew :premium:assembleRelease works without any manual changes.
if (file("premium").exists()) {
    include(":premium")
}
