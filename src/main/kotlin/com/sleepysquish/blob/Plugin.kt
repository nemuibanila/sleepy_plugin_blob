package com.sleepysquish.blob
import com.comphenix.protocol.*
import com.comphenix.protocol.wrappers.WrappedChatComponent
import com.mongodb.client.model.Filters.eq
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.*
import org.bukkit.command.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.*
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import kotlin.math.*

const val ADMIN_PERM = "sleepyblob.admin"

class SleepyBlob : JavaPlugin(), Listener {

    companion object {
        lateinit var instance: SleepyBlob
    }

    override fun onEnable() {
        instance = this
        Persistent.force_init()
        if(!Persistent.safe) return

        server.pluginManager.registerEvents(this, this)
        server.pluginManager.registerEvents(ClaimExecutor, this)
        server.pluginManager.registerEvents(VerifyExecutor, this)
        server.pluginManager.registerEvents(ChestShop, this)
        getCommand("pay")!!.setExecutor(PayExecutor)
        getCommand("money")!!.setExecutor(MoneyExecutor)
        getCommand("claim")!!.setExecutor(ClaimExecutor)
        getCommand("showclaims")!!.setExecutor(ShowclaimExecutor)
        getCommand("verify")!!.setExecutor(VerifyExecutor)
        getCommand("bonk")!!.setExecutor(BonkExecutor)
        history.make_mining_snapshot()
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, Runnable {
            Utility.save_settings()
            this.logger.info("com.sleepysquish.blob.SleepyBlob settings saved.")
        }, 36000, 36000)
    }

    override fun onDisable() {
        if(Persistent.safe) {
            Utility.save_settings()
        }
        super.onDisable()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender.hasPermission(ADMIN_PERM) ) {

            // triplet command set value_name value
            if (args.size == 3) {
                if(args[0] == "set_double") {
                    try {
                        Utility.set_ab(args[1], args[2].toDouble())
                        sender.sendMessage("set ${args[1]} to ${args[2]}")
                    } catch(e:Exception) {
                        sender.sendMessage(e.localizedMessage)
                    }
                }
            }
        }

        return false
    }

    @EventHandler
    fun onPlayerJoin(e: PlayerJoinEvent) {
        val uuid_player = Persistent.players.find(eq("uuid", e.player.uniqueId.toString())).first()
        if (uuid_player == null) {
            // do special cool stuff
            Persistent.get_player(e.player.uniqueId.toString())
            Persistent.update_uuid_with_name(e.player.uniqueId.toString(), e.player.name)
        } else {
            Persistent.update_uuid_with_name(e.player.uniqueId.toString(), e.player.name)
            Persistent.async_update_player_version(e.player.uniqueId.toString())
        }
    }

    @EventHandler
    fun onBlockBreak(e: BlockBreakEvent) {
        if (e.player.isOnline && e.isDropItems) {
            // println(e.block.blockData.asString)
            if(e.player.inventory.itemInMainHand.type == Material.AIR ||
                e.player.inventory.itemInMainHand.containsEnchantment(Enchantment.SILK_TOUCH)) {
                return
            }

            val resource = e.block.blockData.material.createBlockData().asString
            Bukkit.getScheduler().runTaskAsynchronously(instance, Runnable {
                val reward: Double = Persistent.mine_resource(resource)
                if (reward > 0.0) {
                    Persistent.add_money(e.player.uniqueId.toString(), reward)
                    val money = Persistent.get_money(e.player.uniqueId.toString())
                    Bukkit.getScheduler().runTask(instance, Runnable {
                        e.player.sendActionBar(
                            TextComponent("${ChatColor.YELLOW}${Utility.mformat(money)} ${ChatColor.GREEN}(+${
                                Utility.smolformat(
                                    reward
                                )
                            })${ChatColor.YELLOW} Oreru"))
                    })
                }
            })

            // get current reward DONE
            // -- need datastore DONE
            // update amount in store DONE

            // give out reward DONE
        }
    }





}

fun draw_clientside_rect(
    a: Location,
    b: Location,
    player: Player,
    style: BlockStyle
) {
    val block_revert_queue = LinkedList<Location>() as Queue<Location>
    val block_data = style.b
    block_revert_queue.add(a)
    block_revert_queue.add(b)
    player.sendBlockChange(a, block_data)
    player.sendBlockChange(b, block_data)
    for (x in min(a.blockX, b.blockX)..max(a.blockX, b.blockX)) {
        if (x % style.stipple != 0)
            continue

        val top_tmp = a.clone()
        top_tmp.x = x.toDouble()
        top_tmp.y = a.world!!.getHighestBlockAt(top_tmp.x.toInt(), top_tmp.z.toInt()).y.toDouble()
        block_revert_queue.add(top_tmp)
        player.sendBlockChange(top_tmp, block_data)

        val bot_tmp = b.clone()
        bot_tmp.x = x.toDouble()
        bot_tmp.y = a.world!!.getHighestBlockAt(bot_tmp.x.toInt(), bot_tmp.z.toInt()).y.toDouble()
        block_revert_queue.add(bot_tmp)
        player.sendBlockChange(bot_tmp, block_data)
    }

    for (z in min(a.blockZ, b.blockZ)..max(a.blockZ, b.blockZ)) {
        if (z % style.stipple != 0)
            continue

        val top_tmp = a.clone()
        top_tmp.z = z.toDouble()
        top_tmp.y = a.world!!.getHighestBlockAt(top_tmp.x.toInt(), top_tmp.z.toInt()).y.toDouble()
        block_revert_queue.add(top_tmp)
        player.sendBlockChange(top_tmp, block_data)

        val bot_tmp = b.clone()
        bot_tmp.z = z.toDouble()
        bot_tmp.y = a.world!!.getHighestBlockAt(bot_tmp.x.toInt(), bot_tmp.z.toInt()).y.toDouble()
        block_revert_queue.add(bot_tmp)
        player.sendBlockChange(bot_tmp, block_data)
    }

    BlockPacketCleanups.add_cleanup(player.uniqueId, {
        while (block_revert_queue.isNotEmpty()) {
            val loc = block_revert_queue.remove()
            player.sendBlockChange(loc, loc.block.blockData)
        }
    })
}



fun draw_region(region: ProtectedRegion, player: Player) {
    val own = BlockStyle(Material.EMERALD_BLOCK.createBlockData(), 4)
    val others = BlockStyle(Material.IRON_BLOCK.createBlockData(), 4)

    val draw_with = if(region.owners.contains(player.uniqueId)) {
        own
    } else {
        others
    }

    val pl_location = player.location
    val min_p = region.minimumPoint
    val max_p = region.maximumPoint
    val _a = Location(pl_location.world, min_p.x.toDouble(), min_p.y.toDouble(), min_p.z.toDouble())
    val _b = Location(pl_location.world, max_p.x.toDouble(), max_p.y.toDouble(), max_p.z.toDouble())
    println(_a)
    println(_b)
    draw_clientside_rect(_a, _b, player, draw_with)
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
