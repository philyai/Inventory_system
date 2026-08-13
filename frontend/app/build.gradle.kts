plugins {
    alias(libs.plugins.android.application)
}

val productionBaseUrl = providers.gradleProperty("PRODUCTION_BASE_URL").orElse("").get()
val releaseStoreFile = providers.environmentVariable("RELEASE_STORE_FILE").orElse("").get()
val releaseStorePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orElse("").get()
val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orElse("").get()
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orElse("").get()

android {
    namespace = "com.inventorysystem"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.inventorysystem"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val configuredReleaseSigning = if (
        releaseStoreFile.isNotBlank() &&
        releaseStorePassword.isNotBlank() &&
        releaseKeyAlias.isNotBlank() &&
        releaseKeyPassword.isNotBlank()
    ) {
        signingConfigs.create("configuredRelease") {
            storeFile = file(releaseStoreFile)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    } else {
        null
    }

    buildTypes {
        debug {
            buildConfigField("String", "BACKEND_BASE_URL", "\"\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            configuredReleaseSigning?.let { signingConfig = it }
            buildConfigField(
                "String",
                "BACKEND_BASE_URL",
                "\"${productionBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\""
            )
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            optimization {
                enable = true
            }
        }
    }
    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.room:room-runtime:2.8.4")
    annotationProcessor("androidx.room:room-compiler:2.8.4")
    implementation("androidx.work:work-runtime:2.11.2")
}
