package com.sleepysquish.blob
import com.mongodb.client.model.Filters.eq
import kotlinx.coroutines.runBlocking
import org.bson.Document
import org.bukkit.ChatColor
import org.bukkit.entity.Player

object Utility {
    private val settings: Document
    private val recent_settings_version = 3
    private var settings_version = 2
    var transaction_fee = 0.2
    var base_claim_cost = 7.0
    var halving_distance = 1000.0
    var pool_percent_worth = 0.003

    init {
        settings = Persistent.block_get_settings() ?: Document("name", "settings")
        for (kv in settings) {
            set_ab(kv.key, kv.value)
        }

        // migrate from balance 2 to balance 3
        if (settings_version != recent_settings_version) {
            //for (player in Persistent.players.find()) {
                //val uuid = player["uuid"] as String
                //com.sleepysquish.blob.mongo.set_money(uuid, com.sleepysquish.blob.mongo.get_money(uuid)*10)
            //}
            settings_version = recent_settings_version
        }
    }
    const val currency = "Oreru"

    fun set_ab(key: String, value: Any) {
        when(key) {
            "settings_version" -> settings_version = value as Int
            "transaction_fee" -> transaction_fee = value as Double
            "base_claim_cost" -> base_claim_cost = value as Double
            "halving_distance" -> halving_distance = value as Double
            "pool_percent_worth" -> pool_percent_worth = value as Double
        }
    }

    fun amount_fee(d: Double): Double{
        return d* transaction_fee
    }

    fun amount_plus_fee(d: Double):Double{
        return d + amount_fee(d)
    }

    suspend fun balance_str(p: Player): String {
        return "You currently have ${"%.2f".format(Persistent.get_money(p.uniqueId.toString()))} ${currency}s"
    }

    fun mformat(money: Double): String {
        return "%.2f".format(money)
    }

    fun smolformat(money: Double): String {
        return "%.3f".format(money)
    }

    fun start_claiming(): String {
        return "${ChatColor.AQUA}Please mark the corners of your claim using a gold hoe."
    }

    fun save_settings() {
        settings["settings_version"] = recent_settings_version
        settings["transaction_fee"] = transaction_fee
        settings["base_claim_cost"] = base_claim_cost
        settings["halving_distance"] = halving_distance
        settings["pool_percent_worth"] = pool_percent_worth
        runBlocking {
            Persistent.globals.deleteOne(eq("name", "settings"))
            Persistent.globals.insertOne(settings)
        }
    }

}
