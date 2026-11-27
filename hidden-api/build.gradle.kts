plugins {
    alias(libs.plugins.android.library)
}
android{
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    namespace = "com.aliothmoon.hidden_api"

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        targetCompatibility = JavaVersion.VERSION_17
        sourceCompatibility = JavaVersion.VERSION_17
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
