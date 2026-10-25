plugins {
    id("com.android.application")
}
android {
    namespace = "com.iqonli.ezcd"
    compileSdk = providers.gradleProperty("ezcdCompileSdk").getOrElse("35").toInt()
    defaultConfig {
        applicationId = "com.iqonli.ezcd"
        minSdk = 26
        targetSdk = 34
        versionCode = 18
        versionName = "1.0.17"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    // AGP built-in Kotlin inherits JVM target 17 from compileOptions above.
    bundle { language { enableSplit = false } }
}
dependencies {
    // No AndroidX/Compose/Material/other app runtime libraries. Kotlin stdlib is implicit.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
