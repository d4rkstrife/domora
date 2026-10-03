plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
 namespace = "fr.domora.tv"
 compileSdk = 35
 defaultConfig { applicationId = "fr.domora.tv"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
 sourceSets.getByName("main").java.srcDir("../common/src/main/java")
 buildFeatures { compose = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
 implementation(platform("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.11.0"))
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
 implementation("com.wireguard.android:tunnel:1.0.20260102")
 coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
 implementation(platform("androidx.compose:compose-bom:2024.12.01"))
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.media3:media3-exoplayer:1.5.1")
 implementation("androidx.media3:media3-ui:1.5.1")
 implementation("androidx.media3:media3-datasource:1.5.1")
 implementation("com.google.zxing:core:3.5.3")
}
