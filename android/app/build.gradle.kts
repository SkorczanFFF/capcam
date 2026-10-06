import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Release signing comes from a properties file outside the repo, named by the Gradle property
// `capcam.signing` (e.g. in GRADLE_USER_HOME/gradle.properties). Without it, release builds are unsigned.
val releaseSigning: Properties? = providers.gradleProperty("capcam.signing").orNull?.let { path ->
    Properties().apply { file(path).inputStream().use(::load) }
}

android {
    namespace = "io.github.skorczanfff.capcam"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.skorczanfff.capcam"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.1"
        manifestPlaceholders["appLabel"] = "CapCam"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            // Lets a debug build sit next to the released app on the same phone.
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "CapCam debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
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
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
}
