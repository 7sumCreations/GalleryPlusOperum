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
        // versionCode only ever goes up (debug builds share it, and Android
        // refuses downgrades). 0.1.0 = Epic 1.
        versionCode = 40100
        versionName = "0.1.0"
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

    // Release signing comes from outside the repo: the keystore path as a
    // Gradle property and the password as an environment variable, both
    // supplied by scripts/release.sh. Without them the release APK is left
    // unsigned (uninstallable) rather than falling back to a debug key.
    val releaseKeystore = providers.gradleProperty("GALLERY_RELEASE_KEYSTORE").orNull
    val releasePassword = providers.environmentVariable("GALLERY_RELEASE_KEY_PASSWORD").orNull
    val releaseSigningReady = releaseKeystore != null && releasePassword != null
    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storeType = "pkcs12"
                storePassword = releasePassword
                keyAlias = "release"
                keyPassword = releasePassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            // Off for 0.1.0 so the release runs exactly the code that was
            // verified on device as a debug build. Re-enable only with its
            // own on-device test round (AOSP code relies on reflection).
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "../proguard.flags")
            signingConfig = if (releaseSigningReady) signingConfigs.getByName("release") else null
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
    implementation("com.google.android.material:material:1.12.0")
    // external/xmp_toolkit
    implementation("com.adobe.xmp:xmpcore:5.1.2")
    // external/mp4parser
    implementation("com.googlecode.mp4parser:isoparser:1.0-RC-15")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
