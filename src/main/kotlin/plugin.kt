
import com.comphenix.protocol.*
import com.comphenix.protocol.wrappers.WrappedChatComponent
import com.google.gson.*
import com.mongodb.client.*
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.*
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.flags.*
import com.sk89q.worldguard.protection.regions.*
import org.bson.Document
import org.bukkit.*
import org.bukkit.block.data.BlockData
import org.bukkit.command.*
import org.bukkit.entity.Player
import org.bukkit.event.*
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.*
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.*

const val ADMIN_PERM = "sleepyblob.admin"
object mongo {
    val client: MongoClient
    val db: MongoDatabase
    val players: MongoCollection<Document>
    val globals: MongoCollection<Document>
    val materials: MongoCollection<Document>
    val history: MongoCollection<Document>
    init {
        client = MongoClients.create()
        db = client.getDatabase("slp_data")
        players = db.getCollection("players")
        globals = db.getCollection("globals")
        materials = db.getCollection("materials")
        history = db.getCollection("history")
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
        val query = materials.find(eq("material", m))
        if (query.any()) {
            return
        }

        var material_info = Document("material", m)
        material_info.append("value", worth)
        material_info.append("mined", 0L)
        materials.insertOne(material_info)
    }

    fun mine_resource(m: String): Double {
        val query = materials.find(eq("material", m))
        if (query.any()) {
            var material = query.first()
            val worth = material["value"] as Double
            val mined = (material["mined"] as Long).toDouble()
            materials.updateOne(eq("material", m), inc("mined", 1))
            return (worth * 1000.0)/(1000.0 + worth*mined)
        }

        return 0.0
    }

}

object history {


    fun make_mining_snapshot() {
    // mongo.globals.find
    // Create a diff with last history
    // TODO

    }
}

class SleepyBlob : JavaPlugin(), Listener {

    override fun onEnable() {
        server.pluginManager.registerEvents(this, this)
        server.pluginManager.registerEvents(ClaimExecutor, this)
        getCommand("pay")!!.setExecutor(PayExecutor)
        getCommand("money")!!.setExecutor(MoneyExecutor)
        getCommand("claim")!!.setExecutor(ClaimExecutor)
        history.make_mining_snapshot()
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
            // println(e.block.blockData.asString)
            val reward: Double = mongo.mine_resource(e.block.blockData.material.createBlockData().asString)
            mongo.add_money(e.player.uniqueId.toString(), reward)

            // get current reward DONE
            // -- need datastore DONE
            // update amount in store DONE

            // give out reward DONE
        }
    }

    @EventHandler
    fun onPlayerMove(e: PlayerMoveEvent) {
        //println(e.player.inventory.itemInMainHand)
        //if (e.player.inventory.itemInMainHand.type == Material.LEATHER) {
        //
        //          e.player.sendBlockChange(e.player.location.subtract(0.0, 2.0, 0.0), Material.GOLD_BLOCK.createBlockData())
        //    }
        //
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

    fun start_claiming(): String {
        return "${ChatColor.AQUA}Please mark the corners of your claim using a gold hoe."
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

class CommandQueueElement(identifier:String) {
    val identifier: String = identifier
    var value: Any = Any()

    override fun equals(other: Any?): Boolean {
        if(other is CommandQueueElement) {
            return this.identifier == other.identifier && this.value.equals(other.value)
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    override fun toString(): String {
        return "{identifier: ${identifier}, value: ${value}}"
    }
}

object ClaimExecutor : CommandExecutor, Listener {
    var players_: HashMap<UUID, Queue<CommandQueueElement>> = HashMap()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        // setup command chain -> let player set corners with right clicking
        // -> playerinteractevent
        if (args.size > 0) {
            // this is some other subcommand
            return false
        }

        if (sender is Player) {
            var variable_queue = LinkedList<CommandQueueElement>()
            players_.put(sender.uniqueId, variable_queue)
            sender.sendMessage(Slp.start_claiming())
        }

        return true
    }

    @EventHandler
    fun onPlayerInteract(e: PlayerInteractEvent) {
        println(e)
        println(players_)
        if (e.hasItem() && e.item!!.type == Material.GOLDEN_HOE && players_.containsKey(e.player.uniqueId) && e.hasBlock()) {
            e.isCancelled = true

            // we are in claim mode..
            var queue = players_[e.player.uniqueId]!!
            reset_map_painting(e.player)

            val element = CommandQueueElement("coordinate")
            element.value = e.clickedBlock!!.location
            queue.add(element)

            val coords = queue.filter { it.identifier == "coordinate" }
            if (coords.size >= 2) {
                val a = coords[0].value as Location
                val b = coords[1].value as Location
                queue.removeAll(coords)
                if (a.world != b.world) {
                    e.player.sendMessage("you're pretty cheeky, huh? trying to claim across dimensions")
                    return
                }
                val style = BlockStyle(Material.GOLD_BLOCK.createBlockData(), 3)

                queue.add(draw_clientside_rect(a, b, e.player, style))

                val sk_world = BukkitAdapter.adapt(a.world)
                val container = WorldGuard.getInstance().platform.regionContainer
                val regions = container.get(sk_world)!!

                // check if overlap with regions of others
                val prospect = ProtectedCuboidRegion("${e.player.displayName}_${UUID.randomUUID()}",
                    BukkitAdapter.asBlockVector(a),
                    BukkitAdapter.asBlockVector(b))

                val overlaps = regions.getApplicableRegions(prospect)
                for (region in overlaps) {
                    if (region.owners.contains(e.player.uniqueId)) {
                        e.player.sendMessage("${ChatColor.RED}This claim overlaps your old claims. This will not reflect in cost.")
                        continue
                    } else {
                        e.player.sendMessage("${ChatColor.RED}Your selection overlaps with someone elses claim. /showclaims")
                        return
                    }
                }

                var player_region = if(regions.hasRegion(e.player.uniqueId.toString())) {
                    regions.getRegion(e.player.uniqueId.toString())
                } else {
                    GlobalProtectedRegion(e.player.uniqueId.toString())
                }
                player_region!!.setFlag(Flags.FIRE_SPREAD, StateFlag.State.DENY)
                player_region!!.setFlag(Flags.WATER_FLOW, StateFlag.State.ALLOW)
                prospect.parent = player_region


                val protocol = ProtocolLibrary.getProtocolManager()
                val json_str = JsonChat()
                    .textln("hello world")
                    .add_color(ChatColor.DARK_AQUA)

                    .textln("click here to die")
                    .add_color(ChatColor.DARK_PURPLE)
                    .add_command("/kill").done()

                println(json_str)
                val comp = WrappedChatComponent.fromJson(json_str)

                val packet = protocol.createPacket(PacketType.Play.Server.CHAT)
                packet.chatComponents.write(0, comp)


                protocol.sendServerPacket(e.player, packet)
                println(queue)

                // draw rectangle using packets.. DONE
                // check overlap DONE
                // print info about claim size, cost, balance
                // confirm/cancel



            }



        }
    }

    val gson = Gson()

    class JsonChat {
        val array = JsonArray()
        init {
        }

        fun text(text: String): JsonChat {
            val obj = JsonObject()
            obj.addProperty("text", text)
            array.add(obj)
            return this
        }

        fun textln(text: String): JsonChat {
            return text(text + '\n')
        }

        fun add_command(command: String): JsonChat {
            val obj = JsonObject()
            obj.addProperty("action", "run_command")
            obj.addProperty("value", command)
            array.last().asJsonObject.add("clickEvent",obj)
            return this
        }

        fun add_color(chat_color: ChatColor): JsonChat {
            array.last().asJsonObject.addProperty("color", chat_color.name.toLowerCase())
            return this
        }

        fun done(): String {
            return array.toString()
        }
    }




    // claim command
    // plot out claim with playerinteract selection
    // check if there is a overlap
    // tell them how much it will cost
    // let them click [confirm]

    // deleteclaim
    // delete the claim youre standing in

    // showclaims
    // scan for claims
    // show around

    data class BlockStyle(val b: BlockData, val stipple: Int)

    // returns a command queue element to reset painting
    private fun draw_clientside_rect(
        a: Location,
        b: Location,
        player: Player,
        style: BlockStyle
    ): CommandQueueElement {
        val block_revert_queue = LinkedList<Location>() as Queue<Location>
        val block_data = style.b
        block_revert_queue.add(a)
        block_revert_queue.add(b)
        player.sendBlockChange(a, block_data)
        player.sendBlockChange(b, block_data)
        for (x in min(a.blockX, b.blockX)..max(a.blockX, b.blockX)) {
            if (x % style.stipple != 0)
                continue

            var top_tmp = a.clone()
            top_tmp.x = x.toDouble()
            top_tmp.y = a.world!!.getHighestBlockAt(top_tmp.x.toInt(), top_tmp.z.toInt()).y.toDouble()
            block_revert_queue.add(top_tmp)
            player.sendBlockChange(top_tmp, block_data)

            var bot_tmp = b.clone()
            bot_tmp.x = x.toDouble()
            bot_tmp.y = a.world!!.getHighestBlockAt(bot_tmp.x.toInt(), bot_tmp.z.toInt()).y.toDouble()
            block_revert_queue.add(bot_tmp)
            player.sendBlockChange(bot_tmp, block_data)
        }

        for (z in min(a.blockZ, b.blockZ)..max(a.blockZ, b.blockZ)) {
            if (z % style.stipple != 0)
                continue

            var top_tmp = a.clone()
            top_tmp.z = z.toDouble()
            top_tmp.y = a.world!!.getHighestBlockAt(top_tmp.x.toInt(), top_tmp.z.toInt()).y.toDouble()
            block_revert_queue.add(top_tmp)
            player.sendBlockChange(top_tmp, block_data)

            var bot_tmp = b.clone()
            bot_tmp.z = z.toDouble()
            bot_tmp.y = a.world!!.getHighestBlockAt(bot_tmp.x.toInt(), bot_tmp.z.toInt()).y.toDouble()
            block_revert_queue.add(bot_tmp)
            player.sendBlockChange(bot_tmp, block_data)
        }

        val ele = CommandQueueElement("paint_queue")
        ele.value = block_revert_queue
        return ele
    }

    fun reset_map_painting(pl: Player) {
        println("poofy")
        if(players_.containsKey(pl.uniqueId)) {
            println("fuwafuwa")
            val paints = players_[pl.uniqueId]!!.filter { it.identifier == "paint_queue" }
            for (paint in paints) {
                println("eeee")
                val queue = paint.value as Queue<Location>
                while(queue.isNotEmpty()) {
                    val elem = queue.poll()
                    println("fluffy")
                    pl.sendBlockChange(elem, elem.block.blockData)
                }
            }
            players_[pl.uniqueId]!!.removeAll(paints)
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
