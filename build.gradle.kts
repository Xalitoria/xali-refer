import mc.xalitoria.gradle.Load
import mc.xalitoria.gradle.RelativeLoad

plugins {
    kotlin("jvm")
    id("mc.xalitoria.conventions")
    id("mc.xalitoria.minecraft")
    alias(libs.plugins.shadow) apply false
}

subprojects {
    group = "mc.xalitoria.refer"
    version = "0.1.0"
}

tasks.named("build") {
    dependsOn(subprojects.map { "${it.path}:build" })
}

paper {
    name = "XaliRefer"
    main = "mc.xalitoria.refer.paper.XaliRefer"
    prefix = "XaliRefer"
    author = "audizian"
    website = "https://xalitoria.com"
    description = "Join with a player's referral address and earn points for them while you play."
    apiVersion = "26.2"
    load = Load.POSTWORLD
    foliaSupported = true
    relocateSources = true
    serverDependencies {
        register("PlaceholderAPI") { load = RelativeLoad.BEFORE; required = false }
    }
}

velocity {
    name = "XaliRefer"
    id = "xalirefer"
    main = "mc.xalitoria.refer.velocity.XaliRefer"
    author = "audizian"
    url = "https://xalitoria.com"
    description = "Tells the servers which address a player joined with, so they can credit the referral."
    relocateSources = true
}
