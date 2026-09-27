plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.vernova.santhali"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.vernova.santhali"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64", "x86")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        noCompress += listOf("onnx", "data", "tflite", "bin")
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.kotlinx.coroutines.android)

    // ONNX Runtime v1.20.0 — provides Java API + matching native libs
    // (libonnxruntime.so + libonnxruntime4j_jni.so must come from same version)
    implementation(libs.onnxruntime.android)

    // appcompat needed for theme resource linking
    implementation(libs.androidx.appcompat)

    // sherpa-onnx v1.13.8 local AAR — provides sherpa Kotlin API + sherpa JNI libs
    // Its bundled libonnxruntime.so is excluded via pickFirsts (onnxruntime-android wins)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    debugImplementation(libs.androidx.compose.ui.tooling)
}
