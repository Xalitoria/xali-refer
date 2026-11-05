package mc.xalitoria.refer.paper.data.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mc.xalitoria.refer.paper.XaliRefer
import mc.xalitoria.refer.paper.XaliRefer.Companion.debug
import mc.xalitoria.refer.paper.data.db.tables.BonusTable
import mc.xalitoria.refer.paper.data.db.tables.IpTable
import mc.xalitoria.refer.paper.data.db.tables.PlayerTable
import mc.xalitoria.refer.paper.data.db.tables.ReferralTable
import mc.xalitoria.util.config.DatabaseConfig
import mc.xalitoria.util.config.databaseType
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils

/** HikariCP pool and Exposed [Database] for the plugin's tables (see [mc.xalitoria.refer.paper.data.db.tables]). */
object DatabaseManager {
    lateinit var dataSource: HikariDataSource
        private set
    lateinit var database: Database
        private set

    var tablePrefix = "xalirefer_"
        private set

    fun tableName(name: String): String = tablePrefix + name

    fun connect(plugin: XaliRefer, config: DatabaseConfig) {
        val hikariConfig = HikariConfig()
        val type = config.databaseType
        if (type.isRemote) {
            val mysql = config.mysql
            hikariConfig.jdbcUrl = mysql.jdbcUrl(type)
            hikariConfig.driverClassName = mysql.driverClass(type)
            hikariConfig.username = mysql.username
            hikariConfig.password = mysql.password
            hikariConfig.maximumPoolSize = mysql.poolSize.coerceAtLeast(1)
        } else {
            val file = plugin.dataFolder.resolve(config.sqlite.filename)
            file.parentFile.mkdirs()
            hikariConfig.jdbcUrl = "jdbc:sqlite:${file.absolutePath}"
            hikariConfig.driverClassName = "org.sqlite.JDBC"
            // Single-writer: SQLite does not support real concurrent writes.
            hikariConfig.maximumPoolSize = 1
            // SQLite leaves foreign keys (and their cascades) off unless asked, on every connection.
            hikariConfig.connectionInitSql = "PRAGMA foreign_keys = ON"
        }
        hikariConfig.poolName = "XaliRefer-DB"
        attach(HikariDataSource(hikariConfig), config)
    }

    /** Sets up the schema on [source]. The prefix is set first, as table objects read it once. */
    internal fun attach(source: HikariDataSource, config: DatabaseConfig) {
        dataSource = source
        tablePrefix = DatabaseConfig.checkPrefix(config.tablePrefix)
        database = Database.connect(source)
        debug("Attaching database: prefix='{0}'", tablePrefix)

        transaction(database) {
            MigrationUtils.statementsRequiredForDatabaseMigration(PlayerTable, ReferralTable, BonusTable, IpTable, withLogs = false)
                .onEach { debug("Schema statement: {0}", it) }
                .forEach { exec(it) }
        }
    }

    fun close() {
        if (::dataSource.isInitialized) dataSource.close()
    }

    /** Runs [statement] in a suspending transaction on [Dispatchers.IO]; nested calls join the outer one. */
    suspend fun <T> dbTransaction(statement: suspend JdbcTransaction.() -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction(db = database, statement = statement) }
}
