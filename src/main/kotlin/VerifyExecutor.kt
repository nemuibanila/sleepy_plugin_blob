
import com.mongodb.client.model.Filters.eq
import org.bukkit.ChatColor
import org.bukkit.command.*
import org.bukkit.entity.Player
import org.bukkit.event.*
import org.bukkit.event.player.PlayerJoinEvent
import java.util.*

object VerifyExecutor : CommandExecutor, Listener {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player) {
            val player = mongo.get_player(sender.uniqueId.toString())
            if (player.containsKey("discord")) return true;

            val from_discord_token = mongo.tokens.findOneAndDelete(eq("username", sender.name))
            if (from_discord_token == null) {
                assert(false) // they shouldnt even be on the server
            }

            player.append("discord", from_discord_token["discord"])
            player.append("discord_id", from_discord_token["discord_id"])
            mongo.players.replaceOne(eq("uuid", sender.uniqueId.toString()), player)
            sender.sendMessage("${ChatColor.GREEN} You are sucessfully verified")
        }
        return true
    }

    fun is_player_verified(uniqueId: UUID): Boolean {
        val player_token = mongo.players.find(eq("uuid", uniqueId.toString()))
        if (player_token.any()) {
            if(player_token.first()!!.contains("discord")){
                return true
            }
        }
        return false
    }

    @EventHandler
    fun onLogin(e: PlayerJoinEvent) {
        // FAILSAFE!!!
        if ( mongo == null ) {
            e.player.kickPlayer("eee")
        }

        val token = mongo.tokens.find(eq("username", e.player.name))
        var allow = is_player_verified(e.player.uniqueId)
        if (!allow) {
            if(token.any()) {
                SleepyBlob.instance.server.scheduler.runTaskLater(SleepyBlob.instance, Runnable {
                    if(!is_player_verified(e.player.uniqueId)) {
                        e.player.kickPlayer("Took too long to verify.")
                    }
                }, 1200)
            } else {
                e.player.kickPlayer("You are not whitelisted on this server.")
            }
        }

    }
}
