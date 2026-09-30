plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystorePath: String? = System.getenv("GOODLIFE_KEYSTORE")

android {
    namespace = "com.goodlife.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.goodlife.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.7"

        // Dépôt GitHub utilisé pour vérifier les nouvelles versions (releases publiques)
        buildConfigField("String", "UPDATE_REPO", "\"GRENECHE-Matheo/GoodLife\"")
        // Contact public (RGPD art. 13, fiche Play Store, signalement des contenus IA)
        buildConfigField("String", "CONTACT_EMAIL", "\"matheo.greneche0@gmail.com\"")
    }

    // Deux distributions du même code :
    // - github : APK publié sur GitHub, avec mise à jour intégrée vérifiée ;
    // - play   : Google Play, sans mise à jour intégrée (interdite par le règlement Play).
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "true")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "false")
        }
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("GOODLIFE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GOODLIFE_KEY_ALIAS")
                keyPassword = System.getenv("GOODLIFE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            // Version de test installable à côté de la version officielle (autre signature)
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    val camerax = "1.3.4"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // Verrouillage par empreinte / visage / code du téléphone
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.3")

    // Lecture des codes-barres 100 % sur le téléphone (modèle intégré, sans Google Play)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Sleep API (détection du sommeil, même méthode que Google Fit)
    implementation("com.google.android.gms:play-services-location:21.3.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Pas : Health Connect (lecture seule) et relevé périodique du capteur
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    // Déjà apportée par Health Connect ; déclarée pour que CameraX voie ListenableFuture à la compilation
    implementation("com.google.guava:guava:31.1-android")

    // Amis : QR code (génération hors ligne, et scanner de Google sans permission caméra)
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")
}
