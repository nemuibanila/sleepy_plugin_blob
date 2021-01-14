import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.world.World
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.flags.*
import com.sk89q.worldguard.protection.managers.RegionManager
import com.sk89q.worldguard.protection.regions.*
import org.bukkit.*
import org.bukkit.command.*
import org.bukkit.entity.Player
import org.bukkit.event.*
import org.bukkit.event.player.PlayerInteractEvent
import java.util.*
import java.util.logging.Level
import kotlin.collections.HashMap
import kotlin.math.*

object ClaimExecutor : CommandExecutor, Listener {
    var players_: HashMap<UUID, Queue<CommandQueueElement>> = HashMap()

    fun player_global_region(player: UUID, regions: RegionManager): ProtectedRegion {
        val global_region_exists = regions.hasRegion(player.toString())
        var player_region = if(global_region_exists) {
            regions.getRegion(player.toString())
        } else {
            GlobalProtectedRegion(player.toString())
        }

        player_region!!.owners.addPlayer(player)
        player_region!!.setFlag(Flags.FIRE_SPREAD, StateFlag.State.DENY)
        player_region!!.setFlag(Flags.WATER_FLOW, StateFlag.State.ALLOW)
        if(!global_region_exists) {
            regions.addRegion(player_region)
        }
        return player_region
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        // setup command chain -> let player set corners with right clicking
        // -> playerinteractevent
        if (args.size > 0) {
            if ("cancel".startsWith(args[0])) {
                BlockPacketCleanups.reset_map_painting(sender as Player)
                players_.remove(sender.uniqueId)
                sender.sendMessage("${ChatColor.RED}Claiming cancelled.")
            }

            if("friend".startsWith(args[0]) && sender is Player) {
                val container = WorldGuard.getInstance().platform.regionContainer
                val regions = container.get(BukkitAdapter.adapt(sender.location.world))!!
                val global_region = player_global_region(sender.uniqueId, regions)

                if (args.size >= 2) {
                    val friend_uuid_str = mongo.username_to_uuid(args[1])

                    if (friend_uuid_str == null) {
                        sender.sendMessage("Could not find ${args[1]}.")
                        return true
                    } else {
                        val friend_uuid = UUID.fromString(friend_uuid_str)
                        if(global_region.members.contains(friend_uuid)) {
                            global_region.members.removePlayer(friend_uuid)
                            sender.sendMessage("Revoked access for ${args[1]}.")
                            return true
                        }

                        global_region.members.addPlayer(friend_uuid)
                        sender.sendMessage("Gave ${args[1]} access to all your claims.")
                        val fren = sender.server.getPlayer(friend_uuid)
                        if(fren != null) {
                            fren.sendMessage("${sender.displayName} gave you access to their claims-")
                        }
                        return true
                    }
                } else {
                    sender.sendMessage("These people have access to your claims:")
                    for(member in global_region.members.players) {
                        sender.sendMessage(member)
                    }
                    sender.sendMessage(" --------------------------------------")
                    return true
                }
            }

            if(("list".startsWith(args[0]) || "delete".startsWith(args[0])) && sender is Player) {
                val player_location = BukkitAdapter.adapt(sender.location)
                val container = WorldGuard.getInstance().platform.regionContainer
                val regions = container.get(BukkitAdapter.adapt(sender.location.world))
                if (regions == null) {
                    SleepyBlob.instance.logger.log(Level.SEVERE, "Regions not loaded");
                    return false;
                }

                val block_location =
                    BlockVector3.at(player_location.blockX, player_location.blockY, player_location.blockZ)
                val regions_in_area = regions.getApplicableRegions(block_location)
                val player_regions = regions_in_area.filter { it.owners.contains(sender.uniqueId) && it.isPhysicalArea }
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
                }
                if(args.size >= 2) {
                    // with id
                    val to_delete = player_regions.find { it.id == args[1] }
                    if (to_delete == null) {
                        sender.sendMessage("you dont own ${args[1]}, cant delete")
                        return true
                    }
                    if (args.size > 2 && args[2].equals("confirm")) {
                        // actually delete
                        regions.removeRegion(to_delete.id)
                        sender.sendMessage("Claim ${to_delete.id} deleted.")
                        return true;
                    }
                    prompt.textln("You will NOT get a refund for your claim.")
                        .add_color(ChatColor.DARK_RED)
                        .textln("Delete claim ${to_delete.id} ")
                        .add_color(ChatColor.AQUA)
                        .text("[[DELETE]]")
                        .add_color(ChatColor.DARK_RED)
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
                    BlockPacketCleanups.reset_map_painting(sender)
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
            BlockPacketCleanups.reset_map_painting(e.player)

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

                val manhattan_corner_distance = abs(a.x - b.x) +1 + abs(a.z - b.z) +1

                if(manhattan_corner_distance > 250.0) {
                    e.player.sendMessage("thats a pretty big claim. (width+height>250) aborting..")
                    players_.remove(queue)
                    return
                }

                if(manhattan_corner_distance < 8) {
                    e.player.sendMessage("that claim area is too small! width+height has to be >7")
                }

                val style = BlockStyle(Material.GOLD_BLOCK.createBlockData(), 3)

                draw_clientside_rect(a, b, e.player, style)

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
                val prospect = ProtectedCuboidRegion(
                    "${e.player.displayName}_${counter}",
                    BukkitAdapter.asBlockVector(a),
                    BukkitAdapter.asBlockVector(b)
                )

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

                val player_region = player_global_region(e.player.uniqueId, regions)
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

                val avg_distance = calculate_avg_distance(amount.toDouble(), cost,
                    Slp.base_claim_cost,
                    Slp.halving_distance
                )
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
        return Math.pow(0.5, distance / halving_distance) * 0.8 * base_cost + 0.2 * base_cost
    }

    fun calculate_avg_distance(amount: Double, cost: Double, base_cost: Double, halving_distance: Double): Double {
        return log(((cost-(0.2*amount*base_cost)) / (0.8*base_cost)) / amount, 0.5) *halving_distance
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





}
