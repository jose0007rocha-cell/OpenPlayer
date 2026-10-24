plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.josercha.openplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.josercha.openplayer"
        minSdk = 24
        targetSdk = 36
        versionCode = 20
        versionName = "2.9"
    }
    
    buildFeatures {
        dataBinding = true
        buildConfig = true
    }

    // Dois sabores: "github" (APK direto, exclusão sem janelas com acesso a todos os arquivos)
    // e "play" (segue as regras da Google Play: sem MANAGE_EXTERNAL_STORAGE).
    flavorDimensions += "dist"
    productFlavors {
        create("github") {
            dimension = "dist"
            buildConfigField("boolean", "FULL_STORAGE_ACCESS", "true")
        }
        create("play") {
            dimension = "dist"
            buildConfigField("boolean", "FULL_STORAGE_ACCESS", "false")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_PATH") ?: "Android.OpenPlayer.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    lint {
        // O lint não roda dentro do assembleRelease; rode "gradle lint" quando quiser auditar.
        checkReleaseBuilds = false
    }
} // Fecha o bloco android

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.3.1+1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")	
}