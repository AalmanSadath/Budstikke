// MMS PDU encoding and decoding, copied from AOSP. See README.md before changing anything here.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.aalman.budstikke.mms"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
