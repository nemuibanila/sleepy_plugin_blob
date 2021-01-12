import org.bukkit.command.*
import org.bukkit.entity.Player

object MoneyExecutor : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if(sender is Player) {
            sender.sendMessage(Slp.balance_str(sender))
        }
        return true
    }
}
