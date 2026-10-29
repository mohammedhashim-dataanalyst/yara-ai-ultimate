plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yara.ai"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yara.ai.ultimate"
        minSdk = 26
        targetSdk = 34
        versionCode = 100
        versionName = "1.0.0-ultimate"
    }

    // A fixed key, so every new build installs OVER the old one (your chats stay).
    signingConfigs {
        getByName("debug") {
            storeFile = file("yara-debug.keystore")
            storePassword = "yara123"
            keyAlias = "yara"
            keyPassword = "yara123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
