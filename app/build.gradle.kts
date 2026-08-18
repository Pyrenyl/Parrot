plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val cfgReleaseStoreFile = providers.environmentVariable("RELEASE_STORE_FILE").orNull
val cfgReleaseStorePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").orNull
val cfgReleaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
val cfgReleaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull

android {
    namespace = "xyz.mufanc.parrot"
    compileSdk = 37

    defaultConfig {
        applicationId = "xyz.mufanc.parrot"
        minSdk = 27
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = cfgReleaseStoreFile?.let { file(it) }
            storePassword = cfgReleaseStorePassword
            keyAlias = cfgReleaseKeyAlias
            keyPassword = cfgReleaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            vcsInfo.include = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = true
        compose = true
        buildConfig = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.hidden.api.bypass)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    compileOnly(project(":hiddenapi"))
}
