import java.util.Properties

plugins {
    alias(libs.plugins.android.application)

    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)

}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val propsFile = project.rootProject.file("local.properties")
            if (propsFile.exists()) {
                val properties = Properties().apply {
                    propsFile.inputStream().use { load(it) }
                }
                properties.getProperty("STORE_FILE_PATH")?.let { path ->
                    storeFile = file(path)
                    storePassword = properties.getProperty("STORE_PASSWORD")
                    keyAlias = properties.getProperty("KEY_ALIAS")
                    keyPassword = properties.getProperty("KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        debug {
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
        }
        release {
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile?.exists() == true) {
                signingConfig = releaseSigning
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    api(project(":common"))
    api(project(":xposed"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Keep Xposed APIs available to avoid stripping code in library modules.
    compileOnly(libs.xposed.api)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.ui)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.accompanist.drawablepainter)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.compose.html.text)
    implementation(libs.mavericks.compose)
}
