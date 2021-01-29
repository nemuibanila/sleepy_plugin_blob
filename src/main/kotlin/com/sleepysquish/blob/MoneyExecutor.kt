package com.sleepysquish.blob

import kotlinx.coroutines.*
import org.bukkit.command.*
import org.bukkit.entity.Player

object MoneyExecutor : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if(sender is Player) {
            GlobalScope.launch {
                sender.sendMessage(Utility.balance_str(sender))
            }
        }
        return true
    }
}
