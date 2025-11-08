package com.xalitoria.referrals.spigot.api

import com.xalitoria.referrals.spigot.XaliRefer.Companion.instance
import com.xalitoria.referrals.spigot.XaliRefer.Companion.luckPerms
import net.luckperms.api.model.user.User
import net.luckperms.api.node.NodeType
import net.luckperms.api.node.types.MetaNode.builder
import java.util.UUID
import java.util.logging.*
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalAtomicApi::class)
data class PlaytimeData(val playerId: UUID, val referUser: User) {
    init { sync() }

    @OptIn(ExperimentalUuidApi::class)
    private val metaKey get() = "refer-time:" + referUser.uniqueId.toKotlinUuid().toHexString()

    val playtimeMillis = AtomicLong(0)

    fun save() = try {
        referUser.data().clear(NodeType.META.predicate { it.metaKey == metaKey })
        referUser.nodes.add(builder(metaKey, playtimeMillis.load().toString()).build())
    } catch (ex: Exception) {
        instance.logger.log(Level.SEVERE, "Error when saving playtime for ${referUser.username}.", ex)
    }

    fun sync(): Long {
        luckPerms.userManager.loadUser(referUser.uniqueId)
        return luckPerms.userManager
            .loadUser(playerId).get().cachedData.metaData
            .getMetaValue(metaKey)?.toLongOrNull() ?: 0L
    }
}