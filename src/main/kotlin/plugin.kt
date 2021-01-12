
import com.comphenix.protocol.*
import com.comphenix.protocol.wrappers.WrappedChatComponent
import com.mongodb.client.*
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.*
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.world.World
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
import java.lang.Math.pow
import java.util.*
import java.util.logging.Level
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

    companion object {
        lateinit var instance: SleepyBlob
    }

    override fun onEnable() {
        instance = this
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
    const val settings_version = 2
    var transaction_fee = 0.2
    var base_claim_cost = 7.0
    var halving_distance = 1000.0

    init {
        settings = mongo.get_settings()?: Document("name", "settings")
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
            if ("cancel".startsWith(args[0])) {
                reset_map_painting(sender as Player)
                players_.remove(sender.uniqueId)
                sender.sendMessage("${ChatColor.RED}Claiming cancelled.")
            }

            if("delete".startsWith(args[0]) && sender is Player) {
                val player_location = BukkitAdapter.adapt(sender.location)
                val container = WorldGuard.getInstance().platform.regionContainer
                val regions = container.get(BukkitAdapter.adapt(sender.location.world))
                if (regions == null) {
                    SleepyBlob.instance.logger.log(Level.SEVERE, "Regions not loaded");
                    return false;
                }

                val block_location = BlockVector3.at(player_location.blockX, player_location.blockY, player_location.blockZ)
                val regions_in_area = regions.getApplicableRegions(block_location)
                val player_regions = regions_in_area.filter { it.owners.contains(sender.uniqueId) }
                val prompt = JsonChat()


                if (args.size < 2) {
                    // no id
                    prompt.textln("-- your regions here --")
                        .add_color(ChatColor.DARK_AQUA)

                    for (claim in player_regions) {
                        prompt.text("- ${claim.id} ")
                        prompt.add_color(ChatColor.GREEN)
                        prompt.text("[[Show]] ")
                        prompt.add_color(ChatColor.DARK_GREEN)
                        prompt.add_command("/showclaims ${claim.id}")
                        prompt.text("[[Delete]]")
                        prompt.add_command("/claim delete ${claim.id}")
                        prompt.add_color(ChatColor.DARK_RED)
                        prompt.textln("")
                    }
                    send_chat_packet(prompt.done(), sender)
                    return true
                } else {
                    // with id
                    val to_delete = player_regions.find { it.id == args[1] }
                    if (to_delete == null) {
                        sender.sendMessage("you dont own ${args[1]}, cant delete")
                        return true
                    }
                    if (args.size > 2 && args[2].equals("confirm")) {
                        // actually delete
                        regions.removeRegion(to_delete.id)
                        return true;
                    }
                    prompt.textln("You will NOT get a refund for your claim.")
                        .add_color(ChatColor.DARK_RED)
                        .text("Delete claim ${to_delete.id} :")
                        .text("[[DELETE]]")
                        .add_command("/claim delete ${to_delete.id} confirm")
                    send_chat_packet(prompt.done(), sender)
                    return true;
                }
            }

            if("confirm".startsWith(args[0]) && sender is Player) {
                val queue = players_.get(sender.uniqueId)
                if (queue != null) {
                    val confirm_element = queue.find { it.identifier == "confirm" }
                    if (confirm_element == null) {
                        sender.sendMessage("Either you took too long, or you have not yet marked your claim.")
                        return false
                    }

                    val claim_cont = confirm_element!!.value as _ClaimContainer
                    if (mongo.get_money(sender.uniqueId.toString()) >= claim_cont.cost) {
                        val container = WorldGuard.getInstance().platform.regionContainer
                        val regions = container.get(claim_cont.world)!!
                        regions.addRegion(claim_cont.region)
                        sender.sendMessage("Claim created!")
                        if (claim_cont.cost < 0) {
                            sender.server.logger.log(Level.SEVERE, "excuse me what? negative cost")
                            return false
                        }

                        mongo.add_money(sender.uniqueId.toString(), -claim_cont.cost)
                        mongo.pool_add_money(claim_cont.cost)
                    } else {
                        sender.sendMessage("You don't have enough ${Slp.currency}s")
                    }
                    reset_map_painting(sender)
                    queue.clear()
                    players_.remove(sender.uniqueId)
                }
            }

            // this is some other subcommand
            return false
        }

        if (sender is Player) {
            players_.remove(sender.uniqueId)

            var variable_queue = LinkedList<CommandQueueElement>()
            players_.put(sender.uniqueId, variable_queue)
            sender.server.scheduler.runTaskLater(SleepyBlob.instance, Runnable {
                players_.remove(sender.uniqueId)
                if (sender.isOnline) {
                    sender.sendMessage("The /claim you started has timed out.")
                }
            }, 6000)
            sender.sendMessage(Slp.start_claiming())
        }

        return true
    }

    @EventHandler
    fun onPlayerInteract(e: PlayerInteractEvent) {
        if (e.hasItem() && e.item!!.type == Material.GOLDEN_HOE && players_.containsKey(e.player.uniqueId) && e.hasBlock()) {
            e.isCancelled = true

            // we are in claim mode..
            var queue = players_[e.player.uniqueId]!!
            reset_map_painting(e.player)

            val element = CommandQueueElement("coordinate")
            element.value = e.clickedBlock!!.location
            queue.add(element)
            e.player.sendBlockChange(e.clickedBlock!!.location, Material.DIAMOND_BLOCK.createBlockData())

            val coords = queue.filter { it.identifier == "coordinate" }
            if (coords.size >= 2) {
                queue.removeAll { it.identifier == "confirm" }

                val a = coords[0].value as Location
                val b = coords[1].value as Location
                a.y = 255.0
                b.y = 0.0

                queue.removeAll(coords)
                if (a.world != b.world) {
                    e.player.sendMessage("you're pretty cheeky, huh? trying to claim across dimensions")
                    players_.remove(queue)
                    return
                }

                if(a.world!!.environment != org.bukkit.World.Environment.NORMAL) {
                    e.player.sendMessage("Sorry, you can only claim in the overworld.. for now..")
                    players_.remove(queue)
                    return
                }

                val manhattan_corner_distance = abs(a.x-b.x)+1 + abs(a.z-b.z)+1

                if(manhattan_corner_distance > 250.0) {
                    e.player.sendMessage("thats a pretty big claim. (width+height>250) aborting..")
                    players_.remove(queue)
                    return
                }

                if(manhattan_corner_distance < 8) {
                    e.player.sendMessage("that claim area is too small! width+height has to be >7")
                }

                val style = BlockStyle(Material.GOLD_BLOCK.createBlockData(), 3)

                queue.add(draw_clientside_rect(a, b, e.player, style))

                val sk_world = BukkitAdapter.adapt(a.world)
                val container = WorldGuard.getInstance().platform.regionContainer
                val regions = container.get(sk_world)
                if (regions == null) {
                    SleepyBlob.instance.logger.log(Level.SEVERE, "Regions for world $sk_world not loaded.")
                    return;
                }
                // check if overlap with regions of others
                var counter = 1
                while (regions.hasRegion("${e.player.displayName}_${counter}")) {
                    counter += 1
                }
                val prospect = ProtectedCuboidRegion("${e.player.displayName}_${counter}",
                    BukkitAdapter.asBlockVector(a),
                    BukkitAdapter.asBlockVector(b))

                val overlaps = regions.getApplicableRegions(prospect)
                for (region in overlaps) {
                    if (region.owners.contains(e.player.uniqueId)) {
                        e.player.sendMessage("${ChatColor.RED}This claim overlaps your old claims. This will not reflect in cost.")
                        continue
                    } else {
                        e.player.sendMessage("${ChatColor.RED}Your selection overlaps with someone elses claim. /showclaims [does not work yet]")
                        return
                    }
                }

                val global_region_exists = regions.hasRegion(e.player.uniqueId.toString())
                var player_region = if(global_region_exists) {
                    regions.getRegion(e.player.uniqueId.toString())
                } else {
                    GlobalProtectedRegion(e.player.uniqueId.toString())
                }

                player_region!!.owners.addPlayer(e.player.uniqueId)
                player_region!!.setFlag(Flags.FIRE_SPREAD, StateFlag.State.DENY)
                player_region!!.setFlag(Flags.WATER_FLOW, StateFlag.State.ALLOW)
                if(!global_region_exists) {
                    regions.addRegion(player_region)
                }
                prospect.parent = player_region
                prospect.owners.addPlayer(e.player.uniqueId)

                val min_point = prospect.minimumPoint
                val max_point = prospect.maximumPoint
                val spawn = a.world!!.spawnLocation
                var cost = 0.0
                var amount = 0
                for (x in min_point.x..max_point.x) {
                    for(z in min_point.z..max_point.z) {
                        val cpoint = Location(a.world, x.toDouble(), a.y, z.toDouble())
                        cost += calculate_block_cost(cpoint, spawn, Slp.base_claim_cost, Slp.halving_distance)
                        amount += 1
                    }
                }

                val avg_distance = calculate_avg_distance(amount.toDouble(), cost, Slp.base_claim_cost, Slp.halving_distance)
                val avg_cost = cost/amount
                val xsize = max_point.x - min_point.x + 1
                val zsize = max_point.z - min_point.z + 1


                val json_str = JsonChat()
                    .textln("--- Claim info ---")
                    .add_color(ChatColor.DARK_AQUA)
                    .textln("Size $xsize x $zsize")
                    .textln("${Slp.mformat(avg_cost)} per Block (~${Slp.mformat(avg_distance)} Blocks from Spawn)")
                    .textln("=> TOTAL: ${Slp.mformat(cost)} for ${amount} Blocks")
                    .textln(Slp.balance_str(e.player))
                    .add_color(ChatColor.GREEN)
                    .text("[[YEA]]")
                    .add_color(ChatColor.DARK_GREEN)
                    .add_command("/claim confirm")
                    .text("[[Cancel..]]")
                    .add_color(ChatColor.DARK_RED)
                    .add_command("/claim cancel")
                    .text(" or just mark new corner points.")
                    .add_color(ChatColor.WHITE)
                    .done()

                val player = e.player
                send_chat_packet(json_str, player)

                val confirm_element = CommandQueueElement("confirm")
                confirm_element.value = _ClaimContainer(prospect, cost, sk_world)
                queue.add(confirm_element)

                // draw rectangle using packets.. DONE
                // check overlap DONE
                // print info about claim size, cost, balance DONE
                // confirm/cancel DONE
            }
        }


    }

    data class _ClaimContainer(val region: ProtectedRegion, val cost: Double, val world: World)

    fun calculate_block_cost(block: Location, spawn: Location, base_cost: Double, halving_distance: Double): Double {
        val distance = block.distance(spawn)
        return pow(0.5, distance/halving_distance) * base_cost
    }

    fun calculate_avg_distance(amount: Double, cost: Double, base_cost: Double, halving_distance: Double): Double {
        return log((cost/base_cost)/amount, 0.5)*halving_distance
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
        if(players_.containsKey(pl.uniqueId)) {
            val paints = players_[pl.uniqueId]!!.filter { it.identifier == "paint_queue" }
            for (paint in paints) {
                val queue = paint.value as Queue<Location>
                while(queue.isNotEmpty()) {
                    val elem = queue.poll()
                    pl.sendBlockChange(elem, elem.block.blockData)
                }
            }
            players_[pl.uniqueId]!!.removeAll(paints)
        }
    }



}

fun send_chat_packet(
    json_str: String,
    player: Player
) {
    val protocol = ProtocolLibrary.getProtocolManager()
    val comp = WrappedChatComponent.fromJson(json_str)
    val packet = protocol.createPacket(PacketType.Play.Server.CHAT)
    packet.chatComponents.write(0, comp)
    protocol.sendServerPacket(player, packet)
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
