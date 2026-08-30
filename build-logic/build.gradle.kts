plugins {
    `kotlin-dsl`
}

gradlePlugin {
    plugins {
        register("assetManifest") {
            id = "com.aliothmoon.maameow.asset-manifest"
            implementationClass = "com.aliothmoon.maameow.buildlogic.AssetManifestPlugin"
        }
        register("i18nVerify") {
            id = "com.aliothmoon.maameow.i18n-verify"
            implementationClass = "com.aliothmoon.maameow.buildlogic.I18nVerifyPlugin"
        }
    }
}
