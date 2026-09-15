import java.util.Properties

plugins {
    // AGP 9 trae Kotlin integrado: no hace falta aplicar org.jetbrains.kotlin.android.
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

/** Datos de firma de release (keystore.properties, fuera de git). Sin él, la release sale sin firmar. */
val propiedadesFirma: Properties? = rootProject.file("keystore.properties")
    .takeIf { it.exists() }
    ?.let { archivo -> Properties().apply { archivo.inputStream().use(::load) } }

android {
    namespace = "app.widgetdiccionario"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.widgetdiccionario"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.3"
    }

    signingConfigs {
        if (propiedadesFirma != null) {
            create("release") {
                storeFile = file(propiedadesFirma.getProperty("storeFile"))
                storePassword = propiedadesFirma.getProperty("storePassword")
                keyAlias = propiedadesFirma.getProperty("keyAlias")
                keyPassword = propiedadesFirma.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (propiedadesFirma != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        // Robolectric necesita los recursos para inflar layouts en los tests de pantallas.
        unitTests.isIncludeAndroidResources = true
        // Robolectric accede a internos de la JVM que JDK 17+ oculta por defecto.
        unitTests.all {
            it.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        }
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
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
}
