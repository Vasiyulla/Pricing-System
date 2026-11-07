import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Properties

plugins {

  alias(libs.plugins.android.application)

  alias(libs.plugins.kotlin.android)

  alias(libs.plugins.kotlin.compose)

  alias(libs.plugins.google.devtools.ksp)

  alias(libs.plugins.google.services)

}

val envFile = rootProject.file(".env")
val envProperties = Properties().apply {
    if (envFile.exists()) {
        envFile.bufferedReader().use { reader -> load(reader) }
    }
}

fun getEnv(key: String, defaultVal: String = ""): String {
    return System.getenv(key) ?: envProperties.getProperty(key) ?: defaultVal
}



android {

  namespace = "com.example"

  compileSdk = 36



  defaultConfig {

    applicationId = "com.aistudio.pricebridge.qvxkzp"

    minSdk = 26

    targetSdk = 36

    versionCode = 1

    versionName = "1.0"



    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    val webClientId = getEnv("GOOGLE_WEB_CLIENT_ID", getEnv("GOOGLE_CLIENT_ID", ""))
    val backendBaseUrl = getEnv("BACKEND_BASE_URL", "https://pricing-system-4nhl.onrender.com/")

    buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$webClientId\"")
    buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
  }



  signingConfigs {

    create("release") {
 
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"

      storeFile = file(keystorePath)

      storePassword = System.getenv("STORE_PASSWORD")

      keyAlias = "upload"

      keyPassword = System.getenv("KEY_PASSWORD")

    }

    val customDebugKeystore = file("${rootDir}/debug.keystore")

    if (customDebugKeystore.exists()) {

      create("debugConfig") {

        storeFile = customDebugKeystore

        storePassword = "android"

        keyAlias = "androiddebugkey"

        keyPassword = "android"

      }

    }

  }



  buildTypes {

    release {

      isCrunchPngs = false

      isMinifyEnabled = false

      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

      signingConfig = signingConfigs.getByName("release")

    }

    debug {

      signingConfig = signingConfigs.findByName("debugConfig") ?: signingConfigs.getByName("debug")

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

  dependenciesInfo {

    includeInApk = false

    includeInBundle = true

  }

  kotlinOptions {

    jvmTarget = "11"

  }

}







googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }



// Some unused dependencies are commented out below instead of being removed.

// This makes it easy to add them back in the future if needed.

dependencies {

  implementation(platform(libs.androidx.compose.bom))

  implementation(platform(libs.firebase.bom))

  // implementation(libs.accompanist.permissions)

  implementation(libs.androidx.activity.compose)

  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.mlkit.text.recognition)
  implementation(libs.mlkit.barcode.scanning)

  implementation(libs.androidx.compose.material.icons.core)

  implementation(libs.androidx.compose.material.icons.extended)

  implementation(libs.androidx.compose.material3)

  implementation(libs.androidx.compose.ui)

  implementation(libs.androidx.compose.ui.graphics)

  implementation(libs.androidx.compose.ui.tooling.preview)

  implementation(libs.androidx.core.ktx)

  // implementation(libs.androidx.datastore.preferences)

  implementation(libs.androidx.lifecycle.runtime.compose)

  implementation(libs.androidx.lifecycle.runtime.ktx)

  implementation(libs.androidx.lifecycle.viewmodel.compose)

  implementation(libs.androidx.navigation.compose)

  implementation(libs.androidx.room.ktx)

  implementation(libs.androidx.room.runtime)

  implementation(libs.androidx.work.runtime.ktx)

  implementation(libs.coil.compose)

  implementation(libs.converter.moshi)

  implementation(libs.core.ktx)

  // Firebase Auth and Google Sign-In via Credential Manager:
  implementation(libs.firebase.auth)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)

  implementation(libs.kotlinx.coroutines.android)

  implementation(libs.kotlinx.coroutines.core)

  implementation(libs.logging.interceptor)

  implementation(libs.moshi.kotlin)

  implementation(libs.okhttp)

  implementation(libs.play.services.location)

  implementation(libs.retrofit)

  testImplementation(libs.androidx.compose.ui.test.junit4)

  testImplementation(libs.androidx.core)

  testImplementation(libs.androidx.junit)

  testImplementation(libs.junit)

  testImplementation(libs.kotlinx.coroutines.test)

  testImplementation(libs.robolectric)

  androidTestImplementation(platform(libs.androidx.compose.bom))

  androidTestImplementation(libs.androidx.compose.ui.test.junit4)

  androidTestImplementation(libs.androidx.espresso.core)

  androidTestImplementation(libs.androidx.junit)

  androidTestImplementation(libs.androidx.runner)

  debugImplementation(libs.androidx.compose.ui.test.manifest)

  debugImplementation(libs.androidx.compose.ui.tooling)

  "ksp"(libs.androidx.room.compiler)

  "ksp"(libs.moshi.kotlin.codegen)

}

val generateGoogleServices = tasks.register("generateGoogleServicesFromEnv") {
    doLast {
        val projId = getEnv("FIREBASE_PROJECT_ID", "pricebridge-demo")
        val apiKey = getEnv("FIREBASE_API_KEY", "AIzaSyPriceBridgeDeveloperKeyMock001")
        val appId = getEnv("FIREBASE_APP_ID", "1:100000000001:android:pricebridgeclient001")
        val webClient = getEnv("GOOGLE_WEB_CLIENT_ID", "100000000001-mockclientid001.apps.googleusercontent.com")

        val targetFile = file("${projectDir}/google-services.json")
        val jsonContent = """
{
  "project_info": {
    "project_number": "100000000001",
    "project_id": "$projId",
    "storage_bucket": "$projId.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "$appId",
        "android_client_info": {
          "package_name": "com.aistudio.pricebridge.qvxkzp"
        }
      },
      "oauth_client": [
        {
          "client_id": "$webClient",
          "client_type": 3
        }
      ],
      "api_key": [
        {
          "current_key": "$apiKey"
        }
      ],
      "services": {}
    }
  ],
  "configuration_version": "1"
}
""".trimIndent()
        targetFile.writeText(jsonContent)
    }
}

tasks.named("preBuild") {
    dependsOn(generateGoogleServices)
}

