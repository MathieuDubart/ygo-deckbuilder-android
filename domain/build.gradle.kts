// Module JVM pur : aucune dépendance Android, donc des tests qui tournent en une seconde
// et une logique qu'on peut relire à côté de ses jumelles TypeScript et Swift.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    testImplementation(libs.kotlin.test)
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
