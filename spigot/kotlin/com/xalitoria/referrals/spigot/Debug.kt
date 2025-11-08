package com.xalitoria.referrals.spigot

import com.xalitoria.referrals.spigot.XaliRefer.Companion.instance

object Debug {
    private var function: String.() -> Unit = { /* no-op */ }

    var state = false
        private set

    internal fun toggle(): Boolean {
        state = !state
        function = if (state) { it: String -> instance.logger.info(it) } else ({ /* no-op */ })
        return state
    }

    internal operator fun invoke(obj: Any? = null) {
        val msg = when (obj) {
            is String? -> obj ?: "potato"
            else -> obj.toString()
        }
        function(msg)
    }
}