import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// CI passes -PSUPABASE_*, local dev keeps them in local.properties (gitignored).
// Gradle does not load local.properties on its own, hence the explicit read.
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun credential(name: String): String =
    (project.findProperty(name) as String?) ?: localProps.getProperty(name).orEmpty()

android {
    namespace = "com.chatnook.app"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.chatnook.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Supabase credentials are injected from CI secrets / local gradle properties.
        // Placeholders keep the app buildable without secrets; Supabase.isConfigured
        // reports false and the UI degrades to local-only mode.
        val supabaseUrl = credential("SUPABASE_URL")
        val supabaseKey = credential("SUPABASE_ANON_KEY")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseKey\"")
    }

    signingConfigs {
        // Real upload key comes from CI secrets (CI decodes the .jks to /tmp) or from
        // local.properties for local release builds. Absent both, we fall back to debug
        // so the project still builds on a fresh clone.
        val storePath = credential("CHATNOOK_KEYSTORE_PATH")
        if (storePath.isNotEmpty()) {
            create("release") {
                storeFile = file(storePath)
                storePassword = credential("CHATNOOK_KEYSTORE_PASSWORD")
                keyAlias = credential("CHATNOOK_KEY_ALIAS")
                keyPassword = credential("CHATNOOK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.coil.compose)
    implementation(libs.coil.gif)

    // Supabase backend
    implementation(libs.supabase.core)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.storage)
    // Ktor needs an explicit engine on Android; supabase-kt only pulls ktor-client-core.
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}