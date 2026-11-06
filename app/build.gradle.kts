plugins { id("com.android.application") }
android { namespace = "com.example.yardtopo"; compileSdk = 35
    defaultConfig { applicationId = "com.example.yardtopo"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "0.2" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation("com.google.ar:core:1.48.0") }
