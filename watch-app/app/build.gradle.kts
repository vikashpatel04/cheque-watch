import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Endpoint and key come from local.properties (git-ignored), never from source.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun localProp(name: String): String = localProps.getProperty(name, "").trim()

val watchEndpoint = localProp("watch.endpoint")
val watchKey = localProp("watch.key")
if (watchEndpoint.isEmpty() || watchKey.isEmpty()) {
    logger.warn("ChequeWatch: watch.endpoint / watch.key missing from local.properties; the app will show 'Not configured'.")
}

android {
    namespace = "com.chequetracker.watch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chequetracker.watch"
        minSdk = 30
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"

        buildConfigField("String", "WATCH_ENDPOINT", "\"$watchEndpoint\"")
        buildConfigField("String", "WATCH_KEY", "\"$watchKey\"")
    }

    buildTypes {
        release {
            // Shrunk + optimized: smaller APK and noticeably less CPU work than
            // a debug Compose build, which matters for battery. Signed with the
            // local debug key since this is sideloaded, never published.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        // Versions are pinned on purpose (newer Wear/AndroidX releases need a
        // newer compileSdk/AGP); Dependabot proposes upgrades instead.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    // Compose for Wear OS (Material 3)
    implementation("androidx.wear.compose:compose-material3:1.5.6")
    implementation("androidx.wear.compose:compose-foundation:1.5.6")
    implementation("androidx.wear.compose:compose-navigation:1.5.6")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    // Tiles + ProtoLayout
    implementation("androidx.wear.tiles:tiles:1.6.2")
    implementation("androidx.wear.protolayout:protolayout:1.4.2")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")

    // Networking, JSON, cache, background refresh
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.5")

    testImplementation("junit:junit:4.13.2")
}
