try {
  val processEnvClass = Class.forName("java.lang.ProcessEnvironment")
  val field = processEnvClass.getDeclaredField("theEnvironment")
  field.isAccessible = true
  val map = field.get(null) as MutableMap<String, String>
  map.remove("ANDROID_PREFS_ROOT")
  val ciField = processEnvClass.getDeclaredField("theCaseInsensitiveEnvironment")
  ciField.isAccessible = true
  val ciMap = ciField.get(null) as MutableMap<String, String>
  ciMap.remove("ANDROID_PREFS_ROOT")
} catch (_: Exception) {}

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

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "Price Bridge"

include(":app")
