package com.sleepysquish.blob

import org.bukkit.command.*
import org.bukkit.entity.Player

object MoneyExecutor : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if(sender is Player) {
            sender.sendMessage(Utility.balance_str(sender))
        }
        return true
    }
}
