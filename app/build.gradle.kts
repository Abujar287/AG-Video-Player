import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

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
    val ciVersionCode = (project.findProperty("appVersionCode") as? String)?.toIntOrNull() ?: 1
    val ciVersionName = (project.findProperty("appVersionName") as? String)
      ?.takeIf { it.isNotBlank() } ?: "1.0.0"
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

    ndk {
      abiFilters += listOf("arm64-v8a", "armeabi-v7a")
    }
  }

  signingConfigs {
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      isShrinkResources = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val keystoreFile = file(keystorePath)
      signingConfig = if (keystoreFile.exists() && !System.getenv("STORE_PASSWORD").isNullOrBlank()) {
        signingConfigs.create("releaseCustom") {
          storeFile = keystoreFile
          storePassword = System.getenv("STORE_PASSWORD")
          keyAlias = "upload"
          keyPassword = System.getenv("KEY_PASSWORD")
        }
      } else {
        signingConfigs.getByName("debugConfig")
      }
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
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
  testOptions { unitTests { isIncludeAndroidResources = true } }
  packaging {
    resources {
      excludes += listOf(
        "META-INF/DEPENDENCIES",
        "META-INF/LICENSE",
        "META-INF/LICENSE.txt",
        "META-INF/license.txt",
        "META-INF/NOTICE",
        "META-INF/NOTICE.txt",
        "META-INF/notice.txt",
        "META-INF/ASL2.0",
        "META-INF/*.kotlin_module"
      )
    }
  }
}

base {
  archivesName.set("AG Video Player")
}

tasks.register<Copy>("copyAgVideoPlayerApk") {
  from(layout.buildDirectory.dir("outputs/apk/release"))
  from(layout.buildDirectory.dir("outputs/apk/debug"))
  into(layout.buildDirectory.dir("export"))
  include("*.apk")
  rename { "AG Video Player.apk" }
  duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" || it.name == "assemble" }.configureEach {
  finalizedBy("copyAgVideoPlayerApk")
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  ignoreList.add("GITHUB_REPO_SLUG")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.session)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.androidx.core)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.compose.ui.test.junit4)

  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}
