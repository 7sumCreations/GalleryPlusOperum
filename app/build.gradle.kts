plugins {
    id("com.android.application")
}

val galleryApplicationId: String = providers.gradleProperty("GALLERY_APPLICATION_ID").get()

android {
    // Keep the AOSP Java package as the resource namespace so R.* references
    // in the untouched AOSP sources keep resolving. applicationId is what
    // actually has to be unique on the device.
    namespace = "com.android.gallery3d"
    compileSdk = 36

    defaultConfig {
        applicationId = galleryApplicationId
        minSdk = 29
        targetSdk = 33
        versionCode = 40030
        versionName = "1.1.40030"
        ndk.abiFilters += listOf("arm64-v8a")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Point at the AOSP layout instead of copying files, so merges from the
    // GrapheneOS branch stay clean.
    sourceSets {
        getByName("main") {
            manifest.srcFile("../AndroidManifest.xml")
            java.srcDirs("../src", "../src_pd", "../gallerycommon/src")
            res.srcDirs("../res")
        }
        getByName("test") {
            java.srcDirs("src/test/java")
        }
        getByName("androidTest") {
            java.srcDirs("src/androidTest/java")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("../CMakeLists.txt")
            version = "3.22.1"
        }
    }

    useLibrary("org.apache.http.legacy")

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "../proguard.flags")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        abortOnError = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // Mirrors static_libs in Android.bp
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.legacy:legacy-support-core-ui:1.0.0")
    implementation("androidx.legacy:legacy-support-v13:1.0.0")
    implementation("androidx.core:core:1.17.0")
    // external/xmp_toolkit
    implementation("com.adobe.xmp:xmpcore:5.1.2")
    // external/mp4parser
    implementation("com.googlecode.mp4parser:isoparser:1.0-RC-15")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
