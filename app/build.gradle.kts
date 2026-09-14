plugins {
    // AGP 9 trae Kotlin integrado: no hace falta aplicar org.jetbrains.kotlin.android.
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

android {
    namespace = "app.widgetdiccionario"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.widgetdiccionario"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // La base de datos precargada no debe comprimirse dentro del APK.
        noCompress += "db"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
}
