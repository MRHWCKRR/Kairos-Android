plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.services)
    alias(libs.plugins.compose.compiler)
}

val localPropertiesFile = rootProject.file("local.properties")
val relaySecret = if (localPropertiesFile.exists()) {
    val lines = localPropertiesFile.readLines()
    lines.firstOrNull { it.startsWith("KAIROS_RELAY_SECRET=") }
        ?.substringAfter("KAIROS_RELAY_SECRET=")
        ?.trim()
        ?.removeSurrounding("\"")
        ?: ""
} else {
    ""
}

android {
    namespace = "com.kairos.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kairos.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "KAIROS_RELAY_SECRET", "\"$relaySecret\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.play.services.auth)
    implementation(libs.generativeai)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.common)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.play.services)
    implementation("io.coil-kt:coil-compose:2.6.0")

    debugImplementation(libs.compose.ui.tooling)

    // keep whatever testImplementation/androidTestImplementation lines
    // Android Studio already generated here (junit, espresso, etc.)
}
