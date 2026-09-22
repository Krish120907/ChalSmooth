pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven(url = uri("https://maven.mappls.com/repository/mappls/"))
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = uri("https://maven.mappls.com/repository/mappls/"))
    }
}
rootProject.name = "RoadClassifier"
include(":app")