import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import net.minecrell.pluginyml.bukkit.BukkitPluginDescription.Command

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.shadow)
    alias(libs.plugins.pluginyml)
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "com.xalitoria"
version = "0.0.1"

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
        name = "spigotmc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://repo.extendedclip.com/releases/") {
        name = "placeholderapi"
    }
    maven("https://jitpack.io") {
        name = "jitpack"
    }
}

dependencies {
    compileOnly(libs.spigot)
    compileOnly(libs.cmiapi)
    compileOnly(libs.luckperms)
    compileOnly(libs.placeholderapi)
    implementation(libs.kotlin)
    implementation(libs.kotlin.coroutines)
    implementation(libs.textcolor)
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21")
    }
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
    //jvmToolchain(8)
}

bukkit {
    name = rootProject.name
    version = rootProject.version.toString()
    author = rootProject.property("author").toString()
    description = rootProject.property("spigot.description").toString()
    main = "com.xalitoria.referrals.spigot.XaliRefer"
    apiVersion = "1.21"
    depend = listOf("LuckPerms", "PlaceholderAPI")
    commands.apply {
        Command("xalireferral").apply {
            aliases = listOf("xreferral", "xrefer", "xref")
            permission = "xalirefer.info"
            usage = "/<command> <subCommand>"
        }.let { add(it) }
    }
    website = "https://xalitoria.com"

}

tasks {
    jar { enabled = false }
    val shadowJar = named<ShadowJar>("shadowJar") {
        archiveClassifier = "paper"
    }
    build {
        dependsOn("generateBukkitPluginDescription")
        dependsOn(shadowJar)
    }
    processResources {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}
