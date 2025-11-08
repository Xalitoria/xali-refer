import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

@OptIn(ExperimentalKotlinGradlePluginApi::class)
plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.shadow)
}

group = "com.xalitoria"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin)
    //implementation(kotlin("stdlib-jdk8"))
}

kotlin {
    sourceSets {
        main {
            kotlin.srcDirs("kotlin")
            resources.srcDirs("resources")
        }
    }
    jvmToolchain(21)
}

val targetDir = File(layout.buildDirectory.get().asFile, "target")

tasks {
    val renameAndMoveTask = register("renameAndMoveShadowJars") {
        group = "shadow"
        description = "Renames and moves shadowJar outputs for each module."

        doLast {
            targetDir.mkdirs()

            subprojects.forEach { subproject ->
                val shadowJarTask = subproject.tasks.findByName("shadowJar")
                if (shadowJarTask != null) {
                    val originalJar = shadowJarTask.outputs.files.singleFile
                    val newName = "${rootProject.name}-${subproject.name}-${subproject.version}.jar"
                    val targetFile = file("$targetDir/$newName")

                    if (subproject.name == "core") return@forEach
                    println("Moving ${originalJar.name} from ${subproject.name} to $targetFile")
                    originalJar.copyTo(targetFile, overwrite = true)
                } else {
                    println("shadowJar task not found in ${subproject.name}, skipping.")
                }
            }
            println("All shadowJars have been renamed and moved to $targetDir")
        }
    }

    // Make sure this task runs after all shadowJar tasks
    afterEvaluate {
        subprojects.forEach { subproject ->
            subproject.tasks.findByName("shadowJar")?.let { shadowJarTask ->
                renameAndMoveTask.configure {
                    dependsOn(shadowJarTask)
                }
            }
        }
    }
    build {
        //dependsOn("bungee:shadowJar")
        dependsOn("spigot:shadowJar")
        dependsOn("velocity:shadowJar")
        finalizedBy("renameAndMoveShadowJars")
    }
    clean {
        delete(targetDir)
    }
}