package com.xalitoria.referrals.spigot.config

import com.xalitoria.referrals.spigot.XaliRefer.Companion.instance
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStreamReader
import java.util.logging.*

open class Config(val name: String) {

    internal val file = File(instance.dataFolder, "$name.yml")
    internal lateinit var config: YamlConfiguration

    fun load() {
        try {
            if (!instance.dataFolder.exists()) instance.dataFolder.mkdirs()
            if (!file.exists()) createDefaultConfig()
            config = YamlConfiguration.loadConfiguration(file)
            loadDefaults()
        } catch (ex: Exception) {
            instance.logger.log(Level.SEVERE, "Failed to load $name.yml:", ex)
            config = YamlConfiguration() // Fallback to empty config
        }
    }

    fun save() {
        try {
            file.parentFile?.let { parent -> if (!parent.exists()) parent.mkdirs() }
            config.save(file)
        } catch (ex: Exception) {
            instance.logger.log(Level.SEVERE, "Failed to save $name.yml", ex)
        }
    }

    private fun createDefaultConfig() {
        try {
            instance.saveResource("$name.yml", false)
        } catch (_: Exception) {
            file.createNewFile()
            instance.logger.info("Created new $name.yml file")
        }
    }

    private fun loadDefaults() {
        instance.getResource("$name.yml")?.use { stream ->
            val defaults = YamlConfiguration.loadConfiguration(InputStreamReader(stream))
            config.setDefaults(defaults)
            save()
        }
    }
}