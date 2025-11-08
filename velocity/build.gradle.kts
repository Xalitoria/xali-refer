import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.shadow)
    //id("xyz.jpenilla.run-velocity") version "2.3.1"
}

group = "com.xalitoria"
version = "0.0.1"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
}

dependencies {
    compileOnly(libs.velocity)
    compileOnly(libs.luckperms)
    //kapt("com.velocitypowered:velocity-api:3.4.0-SNAPSHOT")
    implementation(libs.kotlin)
}

val targetJavaVersion = 17
kotlin {
    sourceSets {
        main {
            kotlin.srcDirs("kotlin")
            resources.srcDirs("resources")
        }
    }
    jvmToolchain(targetJavaVersion)
}

tasks {
    jar { enabled = false }
    val shadowJar = named<ShadowJar>("shadowJar") {
        archiveClassifier = ""
        dependsOn(":shadowJar")
    }
    build {
        dependsOn(shadowJar)
    }
    processResources {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        val props = mapOf(
            "name" to rootProject.name,
            "id" to rootProject.name.lowercase(),
            "version" to rootProject.version.toString(),
            "description" to rootProject.property("velocity.description"),
            "authors" to rootProject.property("author").toString()
        )
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("velocity-plugin.json") {
            expand(props)
        }
    }
}
