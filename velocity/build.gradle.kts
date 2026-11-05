import mc.xalitoria.gradle.sibling

plugins {
    alias(libs.plugins.shadow)
}

// Declared with api, so the proxy downloads the Kotlin runtime when the plugin loads (Velocity has none) and the
// plugin gets a loader class; without a library the plugin would have to be a Velocity plugin class itself.
dependencies {
    implementation(sibling("common"))
    api("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")
    compileOnly(libs.velocity.api)
}

tasks {
    jar { enabled = false }
    build {
        dependsOn(shadowJar)
    }
}
