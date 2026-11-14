plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.aalman.budstikke"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.aalman.budstikke"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"
    }

    // Release signing comes from the environment, which only the release workflow sets,
    // from repository secrets. Without all four the release build is left unsigned.
    val keystore = System.getenv("BUDSTIKKE_KEYSTORE")
    val keystorePassword = System.getenv("BUDSTIKKE_KEYSTORE_PASSWORD")
    val keyAlias = System.getenv("BUDSTIKKE_KEY_ALIAS")
    val keyPassword = System.getenv("BUDSTIKKE_KEY_PASSWORD")
    val canSign = listOf(keystore, keystorePassword, keyAlias, keyPassword).all { !it.isNullOrEmpty() }
    if (canSign) {
        signingConfigs {
            create("release") {
                storeFile = file(keystore!!)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }

    buildTypes {
        // Debug builds are a separate app, so one can sit beside the signed release, which a
        // different key would otherwise refuse to install over.
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            if (canSign) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.orgjson)
}
