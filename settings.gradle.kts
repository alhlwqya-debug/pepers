import org.gradle.api.initialization.resolve.RepositoriesMode
import java.util.Locale

// Keep the Gradle/Android build toolchain on an ASCII/English locale.
// This prevents old bundletool versions from generating/expecting Arabic-Indic
// DEX indices such as "classes٢.dex" while D8 correctly produces "classes2.dex".
Locale.setDefault(Locale.US)
Locale.setDefault(Locale.Category.FORMAT, Locale.US)
Locale.setDefault(Locale.Category.DISPLAY, Locale.US)

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
    }
}

rootProject.name = "Pepers"
include(":app")
