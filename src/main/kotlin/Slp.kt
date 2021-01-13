
import com.mongodb.client.model.Filters.eq
import org.bson.Document
import org.bukkit.ChatColor
import org.bukkit.entity.Player

object Slp {
    val settings: Document
    val recent_settings_version = 3
    var settings_version = 2
    var transaction_fee = 0.2
    var base_claim_cost = 7.0
    var halving_distance = 1000.0
    var pool_percent_worth = 0.003

    init {
        settings = mongo.get_settings() ?: Document("name", "settings")
        for (kv in settings) {
            set_ab(kv.key, kv.value)
        }

        // migrate from balance 2 to balance 3
        if (settings_version != recent_settings_version) {
            for (player in mongo.players.find()) {
                val uuid = player["uuid"] as String
                //mongo.set_money(uuid, mongo.get_money(uuid)*10)
            }
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

    fun balance_str(p: Player): String {
        return "You currently have ${"%.2f".format(mongo.get_money(p.uniqueId.toString()))} ${currency}s"
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
        mongo.globals.deleteOne(eq("name", "settings"))
        mongo.globals.insertOne(settings)
    }

}
