import com.mongodb.client.model.Filters
import org.bson.Document
import org.bukkit.ChatColor
import org.bukkit.entity.Player

object Slp {
    val settings: Document
    const val settings_version = 2
    var transaction_fee = 0.2
    var base_claim_cost = 7.0
    var halving_distance = 1000.0

    init {
        settings = mongo.get_settings() ?: Document("name", "settings")
        for (kv in settings) {
            when(kv.key) {
                "transaction_fee" -> transaction_fee = kv.value as Double
                "base_claim_cost" -> base_claim_cost = kv.value as Double
                "halving_distance" -> halving_distance = kv.value as Double
            }
        }
    }
    const val currency = "Oreru"

    fun balance_str(p: Player): String {
        return "You currently have ${"%.2f".format(mongo.get_money(p.uniqueId.toString()))} ${currency}s"
    }

    fun mformat(money: Double): String {
        return "%.2f".format(money)
    }

    fun start_claiming(): String {
        return "${ChatColor.AQUA}Please mark the corners of your claim using a gold hoe."
    }

    fun save_settings() {
        settings["settings_version"] = settings_version
        settings["transaction_fee"] = transaction_fee
        settings["base_claim_cost"] = base_claim_cost
        settings["halving_distance"] = halving_distance
        if(mongo.get_settings() != null) {
            mongo.globals.replaceOne(Filters.eq("name", "settings"), settings)
        } else {
            mongo.globals.insertOne(settings)
        }
    }

}
