plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.mikepenz.aboutlibraries.plugin")
}

val keystorePath: String? = System.getenv("GOODLIFE_KEYSTORE")

// Relais IA (version Google Play) : adresse du serveur et numéro du projet Google Cloud (Play Integrity).
// Ce ne sont pas des secrets : -PLIFOODY_RELAY_URL=… -PLIFOODY_CLOUD_PROJECT=… ou variables d'environnement du même nom.
fun prop(name: String): String = (findProperty(name) as String?) ?: System.getenv(name) ?: ""
val relayUrl = prop("LIFOODY_RELAY_URL").trimEnd('/')
val cloudProject = prop("LIFOODY_CLOUD_PROJECT").toLongOrNull() ?: 0L

android {
    namespace = "com.goodlife.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.goodlife.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 33
        versionName = "0.19.0"

        // Dépôt GitHub utilisé pour vérifier les nouvelles versions (releases publiques)
        buildConfigField("String", "UPDATE_REPO", "\"GRENECHE-Matheo/GoodLife\"")
        // Contact public (RGPD art. 13, fiche Play Store, signalement des contenus IA)
        buildConfigField("String", "CONTACT_EMAIL", "\"contact.fanixstudio@gmail.com\"")
        // Site, conditions d'abonnement et politique de confidentialité
        buildConfigField("String", "SITE_URL", "\"https://lifoody.pages.dev\"")
    }

    // L'app parle français et anglais : on ne garde que ces langues dans les textes des bibliothèques
    // (elles en contiennent des dizaines d'autres, jamais affichées par GoodLife).
    androidResources {
        localeFilters += setOf("fr", "en")
    }

    // Deux distributions du même code :
    // - github : APK publié sur GitHub, avec mise à jour intégrée vérifiée ;
    // - play   : Google Play, sans mise à jour intégrée (interdite par le règlement Play).
    flavorDimensions += "store"
    productFlavors {
        // GitHub : 100 % gratuit, IA avec la propre clé Gemini de la personne (jamais via le serveur Lifoody)
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "true")
            buildConfigField("boolean", "AI_RELAY", "false")
            buildConfigField("String", "RELAY_URL", "\"\"")
            buildConfigField("long", "CLOUD_PROJECT", "0L")
        }
        // Google Play : IA via le relais Lifoody (abonnement Premium + essais gratuits), sans clé à créer
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "false")
            buildConfigField("boolean", "AI_RELAY", "true")
            buildConfigField("String", "RELAY_URL", "\"$relayUrl\"")
            buildConfigField("long", "CLOUD_PROJECT", "${cloudProject}L")
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
            // Essais : relais lancé en local (« npm run dev » dans backend/), joint depuis l'émulateur
            buildConfigField("String", "RELAY_URL", "\"${relayUrl.ifEmpty { "http://10.0.2.2:8787" }}\"")
        }
        release {
            // Carte (code natif) : téléphones ARM 64/32 bits et Chromebooks x86_64 ; le x86 32 bits n'existe plus
            ndk { abiFilters += setOf("arm64-v8a", "armeabi-v7a", "x86_64") }
            // R8 : retire le code et les ressources jamais utilisés (rien ne change pour l'utilisateur)
            isMinifyEnabled = true
            isShrinkResources = true
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
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    val camerax = "1.4.2"
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

    // Carte : MapLibre (libre, sans clé) avec les fonds OpenFreeMap (données OpenStreetMap)
    implementation("org.maplibre.gl:android-sdk:13.6.1")

    // Lecture de la liste des licences générée par le plugin (affichée dans À propos)
    implementation("com.mikepenz:aboutlibraries-core:11.6.3")

    // Version Google Play seulement : abonnement Premium et vérification de l'app pour le relais IA
    "playImplementation"("com.android.billingclient:billing-ktx:9.1.0")
    "playImplementation"("com.google.android.play:integrity:1.6.0")
}
