// Les versions vivent dans gradle/libs.versions.toml ; `apply false` les déclare sans les
// appliquer à la racine, chaque module prenant celles dont il a besoin.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
}
