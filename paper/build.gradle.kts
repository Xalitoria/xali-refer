import mc.xalitoria.gradle.sibling

plugins {
    kotlin("plugin.serialization")
    alias(libs.plugins.shadow)
}

dependencies {
    implementation(sibling("common"))

    // Libraries the server downloads when the plugin loads (see loader.json); nothing declared here is shaded.
    api(libs.xalicraft.util)
    api(libs.bundles.database)
    api(libs.bundles.exposed)

    // Folia's API is Paper's plus the regionised scheduler, so it covers both platforms.
    compileOnly(libs.folia.api)
    compileOnly(libs.placeholderapi)
    testImplementation(libs.xalicraft.util)
    testImplementation(libs.folia.api)
    testImplementation(libs.bundles.database)
    testImplementation(libs.bundles.exposed)
}

tasks.test {
    useJUnitPlatform()
}

tasks {
    jar { enabled = false }
    shadowJar {
        minimize()
    }
    build {
        dependsOn(shadowJar)
    }
}
