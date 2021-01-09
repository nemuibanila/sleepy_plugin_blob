
import com.mongodb.client.*
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.*
import org.bson.Document
import org.bukkit.*
import org.bukkit.command.*
import org.bukkit.entity.Player
import org.bukkit.event.*
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import kotlin.math.*

const val ADMIN_PERM = "sleepyblob.admin"
object mongo {
    val client: MongoClient
    val db: MongoDatabase
    val players: MongoCollection<Document>
    val globals: MongoCollection<Document>
    init {
        client = MongoClients.create()
        db = client.getDatabase("slp_data")
        players = db.getCollection("players")
        globals = db.getCollection("globals")
        get_pool() // initialize pool
        ensure_basic_resources()
    }

    fun store_player(uuid: String) {
        var doc = Document("uuid", uuid)
        players.insertOne(doc)
    }

    fun get_player(uuid: String): Document {
        val pl = players.find(eq("uuid", uuid)).first()
        if (pl == null) {
            store_player(uuid)
        }
        return players.find(eq("uuid", uuid)).first()!!
    }

    fun get_money(uuid: String): Double {
        val pl = get_player(uuid)
        return if (pl.containsKey("money")) {
            pl["money"] as Double
        } else {
            0.0
        }
    }

    fun set_money(uuid: String, money: Double) {
        players.updateOne(eq("uuid", uuid), set("money", money))
    }

    fun add_money(uuid: String, money: Double) {
        players.updateOne(eq("uuid", uuid), inc("money", money))
    }

    fun update_uuid_with_name(uuid: String, username: String) {
        players.updateOne(eq("uuid", uuid), set("username", username))
    }

    fun update_player_version(uuid: String) {
        // update fields with default values
        add_money(uuid, 0.0)
    }

    fun username_to_uuid(username: String): String? {
        val query = players.find(eq("username", username))
        if (query.first() != null) return query.first()["uuid"].toString()
        else return null
    }

    fun get_settings(): Document? {
        val query = globals.find(eq("name", "settings"))
        return query.first()
    }


    /*
    pool:
    - amount: Double
     */
    fun get_pool(): Document {
        val query = globals.find(eq("name", "pool"))
        if (query.first() != null) return query.first()
        else {
            var pool = Document("name", "pool").append("amount", 0.0)
            globals.insertOne(pool)
            return pool
        }
    }

    fun save_pool(pool: Document) {
        globals.replaceOne(eq("name", "pool"), pool)
    }

    fun pool_add_money(amount: Double) {
        globals.updateOne(eq("name", "pool"), inc("amount", amount))
    }

    /* resource pool
    - material --- index
    - amount_mined
    - exp
     */
    fun ensure_basic_resources() {
        ensure_basic_resource(Material.COAL_ORE.createBlockData().asString, 1.0)
        ensure_basic_resource("minecraft:nether_gold_ore", 1.0)
        ensure_basic_resource(Material.DIAMOND_ORE.createBlockData().asString, 7.0)
        ensure_basic_resource(Material.EMERALD_ORE.createBlockData().asString, 7.0)
        ensure_basic_resource(Material.LAPIS_ORE.createBlockData().asString, 4.0)
        ensure_basic_resource(Material.NETHER_QUARTZ_ORE.createBlockData().asString, 2.0)
        ensure_basic_resource(Material.REDSTONE_ORE.createBlockData().asString, 3.0)
    }

    fun ensure_basic_resource(m: String, worth: Double) {
        val query = globals.find(eq("material", m))
        if (query.any()) {
            return
        }

        var material_info = Document("material", m)
        material_info.append("value", worth)
        material_info.append("mined", 0L)
        globals.insertOne(material_info)
    }

    fun mine_resource(m: String): Double {
        val query = globals.find(eq("material", m))
        if (query.any()) {
            var material = query.first()
            val worth = material["value"] as Double
            val mined = (material["mined"] as Long).toDouble()
            globals.updateOne(eq("material", m), inc("mined", 1))
            return (worth * 1000.0)/(1000.0 + worth*mined)
        }

        return 0.0
    }

}

class SleepyBlob : JavaPlugin(), Listener {

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        getCommand("pay")!!.setExecutor(PayExecutor)
        getCommand("money")!!.setExecutor(MoneyExecutor)
    }

    override fun onDisable() {
        Slp.save_settings()
        super.onDisable()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender.hasPermission(ADMIN_PERM) && sender is Player) {

        }

        return false
    }

    @EventHandler
    fun onPlayerJoin(e: PlayerJoinEvent) {
        val uuid_player = mongo.players.find(eq("uuid", e.player.uniqueId.toString())).first()
        if (uuid_player == null) {
            // do special cool stuff
            mongo.get_player(e.player.uniqueId.toString())
            mongo.update_uuid_with_name(e.player.uniqueId.toString(), e.player.name)
        } else {
            mongo.update_uuid_with_name(e.player.uniqueId.toString(), e.player.name)
            mongo.update_player_version(e.player.uniqueId.toString())
        }
    }

    @EventHandler
    fun onBlockBreak(e: BlockBreakEvent) {
        if (e.player.isOnline) {
            println(e.block.blockData.asString)
            val reward: Double = mongo.mine_resource(e.block.blockData.material.createBlockData().asString)
            mongo.add_money(e.player.uniqueId.toString(), reward)

            // get current reward
            // -- need datastore
            // update amount in store

            // give out reward
        }
    }
}

object Slp {
    val settings: Document
    const val settings_version = 1
    var transaction_fee = 0.2

    init {
        settings = mongo.get_settings()?: Document("name", "settings")
        for (kv in settings) {
            when(kv.key) {
                "transaction_fee" -> transaction_fee = kv.value as Double
            }
        }
    }
    const val currency = "Oreru"

    fun balance_str(p: Player): String {
        return "You currently have ${"%.2f".format(mongo.get_money(p.uniqueId.toString()))} ${currency}s"
    }

    fun save_settings() {
        settings["settings_version"] = settings_version
        settings["transaction_fee"] = transaction_fee
        if(mongo.get_settings() != null) {
            mongo.globals.replaceOne(eq("name", "settings"), settings)
        } else {
            mongo.globals.insertOne(settings)
        }

    }

}

object MoneyExecutor : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if(sender is Player) {
            sender.sendMessage(Slp.balance_str(sender))
        }
        return true
    }
}

object PayExecutor : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player) {
            if(args.size < 2)
                return false

            // get target
            val target_str = args[0]
            val target_player = sender.server.getPlayer(target_str)

            val uuid = target_player?.uniqueId?.toString() ?: mongo.username_to_uuid(target_str)
            if(target_player != null && uuid != null) {
                mongo.update_uuid_with_name(uuid, target_player.name)
            }

            if (uuid == null) {
                sender.sendMessage("${ChatColor.RED}$target_str could not be found")
                return true
            }

            if (uuid == sender.uniqueId.toString()) {
                sender.sendMessage("${ChatColor.RED}You cannot send to yourself")
                return true
            }

            // get amount
            val amount_str = args[1]
            var amount = amount_str.toDoubleOrNull() ?: 0.0
            amount = round(amount*100)/100
            if (amount <= 0.0) {
                sender.sendMessage("${ChatColor.RED}$amount_str is not a valid amount")
                return true
            }

            var fee = Slp.transaction_fee * amount
            val fee_str = ".2f".format(fee)
            
            // check transaction
            val balance = mongo.get_money(sender.uniqueId.toString())
            if (balance > amount + fee) {
                mongo.add_money(sender.uniqueId.toString(), -amount)
                mongo.add_money(uuid, amount)
                mongo.pool_add_money(fee)
                sender.sendMessage("${ChatColor.GREEN}$amount_str ${Slp.currency} sent to $target_str. Transaction fee: $fee_str ${Slp.currency}s")
            } else {
                sender.sendMessage("${ChatColor.RED}Not enough.  ${Slp.balance_str(sender)} \n" +
                        "${ChatColor.AQUA}Transaction: $amount + $fee (${(Slp.transaction_fee*100).roundToInt()}%)")
            }
            return true;
        }

        return false;
    }
}
