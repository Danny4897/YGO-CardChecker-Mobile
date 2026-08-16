plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
android {
    namespace = "com.ygochecker.android"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.ygochecker.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 16
        versionName = "0.3.4"
        // Shown once after upgrade (DuelWhatsNewDialog). Escape quotes for BuildConfig.
        buildConfigField(
            "String",
            "WHATS_NEW",
            "\"${propertyOrDefault(
                "WHATS_NEW",
                "• Fix popup aggiornamento (feed GitHub raw + fallback multi-CDN)\\n• jsDelivr @main era bloccato su 0.3.1",
            )}\"",
        )
        // Set in android/local.properties (or CI secrets). Empty → opens provider app + manual confirm.
        buildConfigField("String", "DISCORD_CLIENT_ID", "\"${propertyOrEmpty("DISCORD_CLIENT_ID")}\"")
        buildConfigField("String", "DISCORD_CLIENT_SECRET", "\"${propertyOrEmpty("DISCORD_CLIENT_SECRET")}\"")
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"${propertyOrEmpty("GOOGLE_CLIENT_ID")}\"")
        // FOSS in-app updates — jsDelivr (GitHub raw CDN can stay stale for ~5 minutes).
        buildConfigField(
            "String",
            "UPDATE_FEED_URL",
            "\"${propertyOrDefault(
                "UPDATE_FEED_URL",
                "https://raw.githubusercontent.com/Danny4897/YGO-CardChecker-Mobile/main/android/distribution/update.json",
            )}\"",
        )
        // Social micro-API (SQLite backend). Empty → social UI shows offline hint.
        buildConfigField(
            "String",
            "SOCIAL_API_URL",
            "\"${propertyOrDefault("SOCIAL_API_URL", "")}\"",
        )
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    // FOSS distribution: release APK is NOT debuggable (Play Protect flags debug builds).
    // Same debug keystore keeps in-place upgrades from earlier GitHub debug APKs.
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}
dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:designsystem"))
    implementation(project(":data:cards"))
    implementation(project(":data:deck"))
    implementation(project(":feature:search"))
    implementation(project(":feature:decklist"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:overlay"))
    implementation(project(":feature:flow"))
    implementation(project(":feature:profile"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.androidx.datastore.preferences)
    implementation("androidx.browser:browser:1.8.0")
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}

fun Project.propertyOrEmpty(name: String): String = propertyOrDefault(name, "")

fun Project.propertyOrDefault(name: String, default: String): String {
    val local = rootProject.file("local.properties")
    if (local.exists()) {
        local.readLines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("$name=")) {
                return trimmed.substringAfter("=").trim().replace("\"", "\\\"")
            }
        }
    }
    val fromGradle = (findProperty(name) as? String)?.replace("\"", "\\\"")
    return fromGradle?.takeIf { it.isNotEmpty() } ?: default.replace("\"", "\\\"")
}
