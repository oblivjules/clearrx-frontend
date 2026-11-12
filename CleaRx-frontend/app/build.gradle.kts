import java.net.URI

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

val releaseBackendUrl = "https://clearx-refactored.onrender.com/"
val debugBackendUrl = providers.gradleProperty("clearx.debugBackendUrl")
    .orElse("http://192.168.1.3:8000/").get().trim().let { if (it.endsWith("/")) it else "$it/" }
val debugBackendUri = URI(debugBackendUrl)
require(debugBackendUri.scheme in setOf("http", "https") &&
    debugBackendUri.host?.matches(Regex("[A-Za-z0-9.-]+")) == true &&
    debugBackendUri.userInfo == null && debugBackendUri.query == null && debugBackendUri.fragment == null) {
    "clearx.debugBackendUrl must be an HTTP(S) URL with a hostname and no credentials, query or fragment"
}
fun buildConfigString(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val debugNetworkResources = layout.buildDirectory.dir("generated/clearx/debug/res")
val generateDebugNetworkPolicy by tasks.registering {
    inputs.property("backendHost", debugBackendUri.host)
    outputs.dir(debugNetworkResources)
    doLast {
        val output = debugNetworkResources.get().file("xml/network_security_config.xml").asFile
        output.parentFile.mkdirs()
        output.writeText("""
            <?xml version="1.0" encoding="utf-8"?>
            <network-security-config>
                <base-config cleartextTrafficPermitted="false">
                    <trust-anchors><certificates src="system" /></trust-anchors>
                </base-config>
                <domain-config cleartextTrafficPermitted="true">
                    <domain>${debugBackendUri.host}</domain>
                </domain-config>
            </network-security-config>
        """.trimIndent())
    }
}

android {
    namespace = "com.app.clearx"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.app.clearx"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets.getByName("debug").res.srcDir(debugNetworkResources)

    buildTypes {
        debug {
            buildConfigField("String", "BACKEND_BASE_URL", buildConfigString(debugBackendUrl))
        }
        release {
            buildConfigField("String", "BACKEND_BASE_URL", buildConfigString(releaseBackendUrl))
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.recyclerview)

    // CameraX
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)

    // Retrofit + Gson
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // OkHttp + logging
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    //OpenCv
    implementation("org.opencv:opencv:4.9.0")

    implementation("androidx.camera:camera-extensions:1.4.0")
}
tasks.matching { it.name == "preDebugBuild" }.configureEach { dependsOn(generateDebugNetworkPolicy) }
