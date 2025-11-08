package com.xalitoria.referrals.spigot.config

import com.xalitoria.referrals.spigot.parseSeconds

object MainConfig : Config("settings") {
    val ipSearch: String
        get() = config.getString("ip-search")
            ?: throw IllegalArgumentException("'ip-search' is not set in settings.yml - please set and /xalireferral admin reload")

    val afkThresholdSeconds: Long = 30
        get() = config.getString("afk-threshold")?.parseSeconds() ?: field

    val playtimeSeconds: Long = 7200 // 2 hours
        get() = config.getString("playtime-requirement")?.parseSeconds() ?: field

}