pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { maven { url = uri("../tools/google-home-sdk-1.11.0"); content { includeModule("com.google.android.gms", "play-services-home"); includeModule("com.google.android.gms", "play-services-home-types") } }; google(); mavenCentral() } }
rootProject.name = "MaMaison"
include(":app")

