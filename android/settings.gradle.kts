pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "YGOCheckerAndroid"
include(":app", ":core:model", ":core:domain", ":core:common", ":core:designsystem")
include(":data:cards", ":data:deck", ":data:social")
include(":feature:search", ":feature:decklist", ":feature:settings", ":feature:overlay", ":feature:flow", ":feature:profile", ":feature:home", ":feature:scan")
