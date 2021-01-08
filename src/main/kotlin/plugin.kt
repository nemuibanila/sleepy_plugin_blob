
import com.mongodb.client.*
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.*
import org.bson.Document
import org.bukkit.ChatColor
import org.bukkit.command.*
import org.bukkit.entity.Player
import org.bukkit.event.*
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import kotlin.math.round

const val ADMIN_PERM = "sleepyblob.admin"
object mongo {
    val client: MongoClient
    val db: MongoDatabase
    val players: MongoCollection<Document>
    init {
        client = MongoClients.create()
        db = client.getDatabase("slp_data")
        players = db.getCollection("players")
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
        return "aefe4c36-83d6-43bd-9a24-20522607cc2c";
    }
}

class SleepyBlob : JavaPlugin(), Listener {

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        getCommand("pay")!!.setExecutor(PayExecutor)
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
}

object PayExecutor : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val cs = sender.server.consoleSender

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
                sender.sendMessage("${ChatColor.RED} $target_str could not be found")
                return true
            }

            if (uuid == sender.uniqueId.toString()) {
                sender.sendMessage("${ChatColor.RED} You cannot send to yourself")
                return true
            }

            // get amount
            val amount_str = args[1]
            var amount = amount_str.toDoubleOrNull() ?: 0.0
            amount = round(amount*100)/100
            if (amount <= 0.0) {
                sender.sendMessage("${ChatColor.RED} $amount_str is not a valid amount")
                return true
            }
            
            // check transaction
            val balance = mongo.get_money(sender.uniqueId.toString())
            if (balance > amount) {
                mongo.add_money(sender.uniqueId.toString(), -amount)
                mongo.add_money(uuid, amount)
                sender.sendMessage("${ChatColor.GREEN} $amount_str sent to $target_str")
            } else {
                sender.sendMessage("Not enough. Your balance: $balance")
            }
            return true;
            // apply transaction fee

            // move transaction fee to pool
        }

        return false;
    }
}
