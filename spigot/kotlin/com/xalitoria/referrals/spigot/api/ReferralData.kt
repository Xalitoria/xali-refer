package com.xalitoria.referrals.spigot.api

import com.xalitoria.referrals.spigot.XaliRefer.Companion.instance
import com.xalitoria.referrals.spigot.XaliRefer.Companion.luckPerms
import net.luckperms.api.model.user.User
import net.luckperms.api.node.NodeType
import net.luckperms.api.node.types.MetaNode
import org.bukkit.OfflinePlayer
import java.util.logging.*
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalAtomicApi::class, ExperimentalUuidApi::class)
data class ReferralData(val referUser: User) {
    constructor(player: OfflinePlayer) : this(
        luckPerms.userManager.loadUser(player.uniqueId).get()
            ?: throw NullPointerException("User ${player.name} not found in LuckPerms")
    )

    private val _manualCountKey = MANUAL_COUNT_PREFIX + referUser.uniqueId.toKotlinUuid().toHexString()
    val bonusCount = AtomicInt(referUser.cachedData.metaData.getMetaValue(_manualCountKey)?.toIntOrNull() ?: 0)

    private val _referCountKey = REFER_COUNT_PREFIX + referUser.uniqueId.toKotlinUuid().toHexString()
    val referCount = AtomicInt(referUser.cachedData.metaData.getMetaValue(_referCountKey)?.toIntOrNull() ?: 0)

    val totalCount get() = bonusCount.load() + referCount.load()

    fun save() { try {
        if (!instance.isEnabled) return
        referUser.data().apply {
            clear(NodeType.META.predicate { it.metaKey == _manualCountKey || it.metaKey == _referCountKey })
            add(MetaNode.builder(_manualCountKey, bonusCount.load().toString()).build())
            add(MetaNode.builder(_referCountKey, referCount.load().toString()).build())
        }
        luckPerms.userManager.saveUser(referUser)
    } catch (ex: Exception) {
        instance.logger.log(Level.SEVERE, "Error when saving referral count for ${referUser.username}.", ex)
    }   }

    companion object {
        private const val MANUAL_COUNT_PREFIX = "manual-count:"
        private const val REFER_COUNT_PREFIX = "refer-count:"
    }
}