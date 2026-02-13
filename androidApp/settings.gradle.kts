pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Deadly"

include(":app")

include(":core:database")
include(":core:design")
include(":core:domain")
include(":core:media")
include(":core:model")
include(":core:network")
include(":core:network:archive")
include(":core:api:search")
include(":core:search")
include(":core:api:playlist")
include(":core:playlist")
include(":core:api:miniplayer")
include(":core:miniplayer")
include(":core:api:player")
include(":core:player")
include(":core:api:library")
include(":core:library")
include(":core:api:home")
include(":core:home")
include(":core:api:collections")
include(":core:collections")
include(":core:api:recent")
include(":core:recent")
include(":feature:splash")
include(":feature:home")
include(":feature:search")
include(":feature:playlist")
include(":feature:player")
include(":feature:miniplayer")
include(":feature:library")
include(":feature:collections")
include(":feature:settings")
