plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "com.chagokchagok.app"
  // compileSdk follows what the AndroidX libraries require; targetSdk matches the Pleos Connect image (API 34).
  compileSdk = 36
  defaultConfig {
    applicationId = "com.chagokchagok.app"
    minSdk = 28
    targetSdk = 34
    versionCode = 1
    versionName = "0.1.0"
  }

  // Car APIs (CarUxRestrictionsManager, CarPropertyManager) are provided by the device at runtime.
  useLibrary("android.car")

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  debugImplementation(libs.androidx.compose.ui.tooling)

  // Pleos Connect SDK
  implementation(libs.pleos.navihelper)
  implementation(libs.pleos.vehicle)
}
