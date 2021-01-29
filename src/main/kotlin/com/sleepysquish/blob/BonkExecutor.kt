package com.sleepysquish.blob

import kotlinx.coroutines.*
import org.bukkit.*
import org.bukkit.command.*
import org.bukkit.entity.Player
import java.lang.Runnable

object BonkExecutor : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) return false;
        if (args.size >= 1) {
            val target = Bukkit.getServer().getPlayer(args[0])
            if(target == null) {
                sender.sendMessage("Player ${args[0]} must be online.")
                return true
            }

            GlobalScope.launch {
                val balance = Persistent.get_money(sender.uniqueId.toString())
                val paid = balance > 25.0
                if(paid) {
                    Persistent.add_money(sender.uniqueId.toString(), -25.0)
                    Persistent.async_pool_add_money(25.0)
                }

                Bukkit.getScheduler().runTask(SleepyBlob.instance, Runnable Response@ {
                    val origin = Bukkit.getServer().getPlayer(sender.uniqueId)
                    val target = Bukkit.getServer().getPlayer(args[0])
                    if(origin != null && target != null) {
                        if(!paid) {
                            origin.sendMessage("Not enough ${Utility.currency}.")
                            return@Response
                        }

                        origin.playSound(sender.location, Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, SoundCategory.VOICE, 1.0f, 0.0f)
                        origin.sendMessage("${ChatColor.LIGHT_PURPLE}Bonked ${args[0]}!!")
                        target.playSound(target.location, Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, SoundCategory.VOICE, 1.0f, 0.0f)
                        target.sendMessage("${ChatColor.LIGHT_PURPLE}BONK!")
                    }
                })
            }
            return true;
        }
        sender.sendMessage("/bonk to bonk someone. 25 Orerus.")
        return true
    }

}
