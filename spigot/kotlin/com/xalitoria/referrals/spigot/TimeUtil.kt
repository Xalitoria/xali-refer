package com.xalitoria.referrals.spigot

import com.xalitoria.referrals.spigot.config.MessageConfig
import kotlin.collections.iterator

private val units = mapOf(
    "day" to 86400,
    "hour" to 3600,
    "minute" to 60,
    "second" to 1
)

internal fun String.parseSeconds(): Long {
    var seconds = 0L
    val matches = Regex("""(\d+)([dhms])""").findAll(this.lowercase())
    if (!matches.any()) throw IllegalArgumentException("No valid time units found in: '$this'")

    matches.forEach {
        val (time, unitChar) = it.destructured
        val unit = units.entries.firstOrNull { it.key.first() == unitChar.first() }?.value
            ?: throw IllegalArgumentException("Invalid unit in time format: '$this' - Unknown unit: '${unitChar.first()}'")
        seconds += unit * time.toLong()
    }
    return seconds
}

@Throws(IllegalArgumentException::class)
internal fun Long.formattedTime(): String {
    val timeSection = MessageConfig.config.getConfigurationSection("playtime")
        ?: throw IllegalArgumentException("Missing 'playtime' section found in messages.yml")
    var format = timeSection.getString("format")
        ?: throw IllegalArgumentException("Missing 'playtime.format' section found in messages.yml")

    var timeLeft = this
    for ((unit, time) in units) {
        val t = (timeLeft / time).toInt()
        val unitPath = when (t) {
            0 -> continue
            1 -> unit
            else -> unit + "s"
        }
        timeLeft %= time
        var unitFormat = timeSection.getString(unitPath)?.replace("{}", t.toString())
            ?: throw IllegalArgumentException("Invalid unit in time format: '$this', '$unit'")

        format = format.replace("{${unit.first()}}", unitFormat)
    }
    return format
}