plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

/**
 * Version et signature viennent de la ligne de commande quand la CI les fournit, et des
 * valeurs ci-dessous sinon. Un build local reste donc possible sans rien configurer, et
 * c'est la CI qui fait autorité pour ce qui est publié.
 */
val ciVersionName = providers.gradleProperty("ygoVersionName").orNull
val ciVersionCode = providers.gradleProperty("ygoVersionCode").orNull?.toIntOrNull()

/**
 * Le keystore de sideload. Il n'est PAS dans le dépôt — celui-ci est public — mais déposé
 * par la CI depuis un secret. Absent, on ne signe pas : le build local produit alors un APK
 * de debug ordinaire, et c'est très bien.
 *
 * Une clé stable n'est pas un détail de confort : Android refuse une mise à jour dont la
 * signature a changé. Sans elle, chaque nouvelle version demanderait une désinstallation, et
 * donc la perte de la session et de l'adresse du serveur.
 */
val keystoreFile = providers.environmentVariable("SIGNING_KEYSTORE_PATH").orNull
    ?.trim()?.takeIf { it.isNotEmpty() }
    ?.let(::file)?.takeIf { it.exists() }

/**
 * Une variable d'environnement, ramenée à de l'ASCII imprimable.
 *
 * Le filtre n'est pas de la coquetterie, et `trim()` n'y suffisait pas. La JVM refuse un mot
 * de passe contenant le moindre caractère hors 0x20–0x7E et le dit très mal : « Password is
 * not ASCII », alors que le mot de passe l'est et qu'un simple retour à la ligne suffit à
 * déclencher ça. Or un secret GitHub garde tout ce que la copie a emporté avec la ligne —
 * `\n`, `\r`, une espace insécable, un BOM — et `trim()` ne connaît que les blancs.
 *
 * Aucun des trois réglages lus ici ne peut légitimement contenir autre chose que de l'ASCII
 * imprimable, donc filtrer ne perd rien.
 */
fun env(name: String): String? =
    providers.environmentVariable(name).orNull
        ?.filter { it.code in 0x20..0x7E }
        ?.takeIf { it.isNotEmpty() }

val storePass = env("SIGNING_KEYSTORE_PASSWORD")

android {
    namespace = "dev.ygo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.ygo.app"
        minSdk = 29
        targetSdk = 35
        versionCode = ciVersionCode ?: 1
        versionName = ciVersionName ?: "0.1.0"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("sideload") {
                // `providers.environmentVariable` et non `System.getenv` : le cache de
                // configuration est actif, et une lecture directe de l'environnement à la
                // configuration le ferait échouer.
                storeFile = keystoreFile
                storePassword = storePass
                keyAlias = env("SIGNING_KEY_ALIAS")
                // Le keystore est un PKCS12, format dans lequel keytool refuse un mot de
                // passe de clé différent de celui du magasin : les deux sont le même par
                // construction. Le secret dédié reste honoré s'il existe, mais il n'est plus
                // obligatoire — c'est une valeur de moins à recopier, donc une de moins à se
                // tromper, et c'est précisément celle-là qui cassait la signature.
                keyPassword = env("SIGNING_KEY_PASSWORD") ?: storePass
            }
        }
    }

    buildTypes {
        debug {
            // Celui-ci reste signé par la clé de debug de la machine : c'est le build de
            // développement, il s'installe à côté du vrai grâce au suffixe.
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.findByName("sideload")
            /*
             * Rétrécissement désactivé pour l'instant, volontairement. R8 casse par
             * réflexion — sérialisation, Ktor — et ça ne se voit qu'à l'exécution, jamais
             * au build : une CI verte ne prouverait rien. On l'allumera quand l'app aura
             * tourné sur un vrai téléphone, et on le vérifiera là.
             */
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin { jvmToolchain(17) }

    buildFeatures { compose = true }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
}
