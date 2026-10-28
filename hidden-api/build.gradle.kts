plugins {
    alias(libs.plugins.android.library)
}
android{
    compileSdk = 37

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
