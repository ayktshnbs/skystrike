plugins {
    id("com.android.application")
}

android {
    namespace = "com.aykut.skystrike2"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.aykut.skystrike2"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

configurations.all {
    resolutionStrategy {
        // Force versions compatible with compileSdk 35 to resolve AAR metadata errors
        force("androidx.core:core:1.13.1")
        force("androidx.core:core-ktx:1.13.1")
        force("androidx.appcompat:appcompat:1.6.1")
        force("androidx.activity:activity:1.9.3")
        force("androidx.lifecycle:lifecycle-common:2.8.7")
        force("androidx.lifecycle:lifecycle-runtime:2.8.7")
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-games-v2:20.1.2")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.13.1")

    testImplementation("junit:junit:4.13.2")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    // This provides androidx.test.platform.app.InstrumentationRegistry
    androidTestImplementation("androidx.test:monitor:1.6.1")
}
