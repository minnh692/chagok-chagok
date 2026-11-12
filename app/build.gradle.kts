import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.ksp)
}

ksp {
  // Exported schema is committed so tests can check that no coordinate or time column ever appears.
  arg("room.schemaLocation", "$projectDir/schemas")
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

  // Release signing comes from outside the repo: ~/.chagok/keystore.properties (storeFile is relative to that
  // folder). Without it the release build is left unsigned.
  val signingProps = File(System.getProperty("user.home"), ".chagok/keystore.properties")
  val releaseSigning = if (signingProps.isFile) {
    val props = Properties().apply { signingProps.inputStream().use { load(it) } }
    signingConfigs.create("release") {
      storeFile = File(signingProps.parentFile, props.getProperty("storeFile"))
      storePassword = props.getProperty("storePassword")
      keyAlias = props.getProperty("keyAlias")
      keyPassword = props.getProperty("keyPassword")
    }
  } else {
    null
  }

  buildTypes {
    release {
      signingConfig = releaseSigning
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
  implementation(libs.pleos.vehicle)
  implementation(libs.pleos.navihelper)

  // Local storage
  implementation(libs.androidx.room.runtime)
  ksp(libs.androidx.room.compiler)

  testImplementation(libs.junit)
}
