plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    val ciVersionCode = (project.findProperty("appVersionCode") as? String)
      ?.toIntOrNull()?.takeIf { it > 0 } ?: 33
    val ciVersionName = (project.findProperty("appVersionName") as? String)
      ?.takeIf { it.isNotBlank() } ?: "1.0.33"
    val ciRepoSlug = (project.findProperty("githubRepoSlug") as? String)
      ?.takeIf { it.contains('/') }
      ?: System.getenv("GITHUB_REPOSITORY")?.takeIf { it.contains('/') }
      ?: "Abujar287/AG-Video-Player"

    applicationId = "com.aistudio.veloplay.mvxkpq"
    minSdk = 24
    targetSdk = 36
    versionCode = ciVersionCode
    versionName = ciVersionName
    buildConfigField("String", "GITHUB_REPO_SLUG", "\"$ciRepoSlug\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.core.ktx)
}
