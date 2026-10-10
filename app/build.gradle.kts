plugins { id("com.android.application") }

android {
    namespace = "cc.oneko.tv"
    compileSdk = 34
    defaultConfig {
        applicationId = "cc.oneko.tv"
        minSdk = 16          // Android 4.1+
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug") // lets you sideload the release APK
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    lint { checkReleaseBuilds = false }
}
