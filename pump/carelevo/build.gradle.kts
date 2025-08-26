plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    id("kotlin-android")
    id("android-module-dependencies")
    id("test-module-dependencies")
    id("jacoco-module-dependencies")
}

android {
    namespace = "info.nightscout.androidaps.plugins.pump.carelevo"
    defaultConfig {
        ksp {
            arg("room.incremental", "true")
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }
    buildFeatures {
        dataBinding = true
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:libraries"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":core:keys"))
    implementation(project(":core:validators"))

    api(libs.com.google.guava)

    //RxAndroidBle
    api(libs.io.reactivex.rxjava3.rxandroid)
    api(libs.com.polidea.rxandroidble3)
    api(libs.com.jakewharton.rx3.replaying.share)
    implementation(libs.androidx.appcompat.v170)
    implementation(libs.com.google.android.material)
    implementation(libs.activity)
    implementation(libs.androidx.constraintlayout.v214)

    ksp(libs.com.google.dagger.compiler)
    ksp(libs.com.google.dagger.android.processor)
}