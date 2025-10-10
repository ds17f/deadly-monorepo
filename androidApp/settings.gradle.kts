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

// V2 modules
include(":v2:app")
include(":v2:core:database")
include(":v2:core:design")
include(":v2:core:domain")
include(":v2:core:media")
include(":v2:core:model")
include(":v2:core:network")
include(":v2:core:network:archive")
include(":v2:core:theme-api")
include(":v2:core:theme")
include(":v2:core:api:search")
include(":v2:core:search")
include(":v2:core:api:playlist")
include(":v2:core:playlist")
include(":v2:core:api:miniplayer")
include(":v2:core:miniplayer")
include(":v2:core:api:player")
include(":v2:core:player")
include(":v2:core:api:library")
include(":v2:core:library")
include(":v2:core:api:home")
include(":v2:core:home")
include(":v2:core:api:collections")
include(":v2:core:collections")
include(":v2:core:api:recent")
include(":v2:core:recent")
include(":v2:feature:splash")
include(":v2:feature:home")
include(":v2:feature:search")
include(":v2:feature:playlist")
include(":v2:feature:player")
include(":v2:feature:miniplayer")
include(":v2:feature:library")
include(":v2:feature:collections")
include(":v2:feature:settings")
