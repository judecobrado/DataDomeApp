plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    kotlin("android")
    kotlin("kapt")
    id("kotlin-parcelize")
}

android {
    namespace = "com.example.datadomeapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.datadomeapp"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures{
        viewBinding = true
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    packaging {
        resources {
            excludes += listOf(
                "META-INF/NOTICE.md",
                "META-INF/LICENSE.md",
                "META-INF/DEPENDENCIES",
                "META-INF/INDEX.LIST"
            )
        }
    }
}

dependencies {
    // >> CRITICAL FIX: Add this line for SwipeRefreshLayout <<
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
// You can use a newer version if available, but 1.1.0 is stable and common.
    implementation(libs.material)
// Which typically resolves to: implementation("com.google.android.material:material:1.x.x")

    implementation(platform("com.google.firebase:firebase-bom:34.3.0"))

    implementation("com.sun.mail:android-mail:1.6.7")
    implementation("com.sun.mail:android-activation:1.6.7")

    implementation("com.google.firebase:firebase-storage-ktx:20.2.1")
    implementation("com.squareup.picasso:picasso:2.8")
    implementation("com.google.firebase:firebase-appcheck-playintegrity:17.0.0")
    implementation("com.google.firebase:firebase-functions:21.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    implementation("com.github.bumptech.glide:glide:4.16.0")

    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation(libs.androidx.gridlayout)
    implementation(libs.androidx.camera.camera2.pipe)
    kapt("com.github.bumptech.glide:compiler:4.15.1")

    implementation("com.google.firebase:firebase-appcheck-debug:17.0.0")

    //eto na add
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    //eto na add

    implementation("com.google.android.gms:play-services-maps:19.2.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")


    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.firebase.auth)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}