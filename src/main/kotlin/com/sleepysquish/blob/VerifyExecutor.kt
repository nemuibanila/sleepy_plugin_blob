package com.sleepysquish.blob
import com.mongodb.client.model.Filters.eq
import kotlinx.coroutines.*
import org.bukkit.*
import org.bukkit.command.*
import org.bukkit.entity.Player
import org.bukkit.event.*
import org.bukkit.event.player.PlayerJoinEvent
import java.lang.Runnable
import java.util.*

object VerifyExecutor : CommandExecutor, Listener {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player) {
            GlobalScope.launch {
                val player = Persistent.get_player(sender.uniqueId.toString())
                if (player.containsKey("discord")) return@launch

                val from_discord_token = Persistent.tokens.findOneAndDelete(eq("username", sender.name))
                if (from_discord_token == null) {
                    sender.sendMessage("Your verification token ran out. Please \$verify again.")
                    return@launch
                }

                player.append("discord", from_discord_token["discord"])
                player.append("discord_id", from_discord_token["discord_id"])
                Persistent.players.replaceOne(eq("uuid", sender.uniqueId.toString()), player)
                sender.sendMessage("${ChatColor.GREEN} You are sucessfully verified")
            }
        }
        return true
    }

    suspend fun is_player_verified(uniqueId: UUID): Boolean {
        val player_token = Persistent.players.find(eq("uuid", uniqueId.toString())).first()
        if (player_token != null) {
            if(player_token.contains("discord")){
                return true
            }
        }
        return false
    }

    @EventHandler
    fun onLogin(e: PlayerJoinEvent) {
        // FAILSAFE!!!
        if ( !Persistent.safe ) {
            e.player.kickPlayer("eee")
            // nooo
        }
        GlobalScope.launch {
            val token = Persistent.tokens.find(eq("username", e.player.name)).first()
            val allow = is_player_verified(e.player.uniqueId)
            if (!allow) {
                if(token!=null) {
                    SleepyBlob.instance.server.scheduler.runTaskLater(SleepyBlob.instance, Runnable {
                        val allow_2 = runBlocking {
                            return@runBlocking is_player_verified(e.player.uniqueId)
                        }
                        if(!allow_2) {
                            e.player.kickPlayer("Took too long to verify.")
                        }
                    }, 1200)
                } else {
                    Bukkit.getScheduler().runTask(SleepyBlob.instance, Runnable {
                        e.player.kickPlayer("You are not whitelisted on this server.")
                    })
                }
            }
        }

    }
}
