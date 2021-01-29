package com.sleepysquish.blob

import kotlinx.coroutines.*
import org.bukkit.ChatColor
import org.bukkit.command.*
import org.bukkit.entity.Player
import kotlin.math.*

object PayExecutor : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player) {
            if(args.size < 2)
                return false

            // get target
            val target_str = args[0]
            val target_player = sender.server.getPlayer(target_str)

            val uuid = target_player?.uniqueId?.toString() ?: Persistent.block_username_to_uuid(target_str)
            if(target_player != null && uuid != null) {
                Persistent.update_uuid_with_name(uuid, target_player.name)
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
            amount = round(amount * 100) /100
            if (amount <= 0.0) {
                sender.sendMessage("${ChatColor.RED}$amount_str is not a valid amount")
                return true
            }

            var fee = Utility.transaction_fee * amount
            val fee_str = Utility.mformat(fee)

            // check transaction
            GlobalScope.launch {
                val balance = Persistent.get_money(sender.uniqueId.toString())
                if (balance > amount + fee) {
                    Persistent.add_money(sender.uniqueId.toString(), -(amount+fee))
                    Persistent.add_money(uuid, amount)
                    Persistent.async_pool_add_money(fee)
                    sender.sendMessage("${ChatColor.GREEN}$amount_str ${Utility.currency} sent to $target_str. Transaction fee: $fee_str ${Utility.currency}s")
                    if (target_player != null) {
                        target_player.sendMessage("You just got $amount_str ${Utility.currency} from ${sender.displayName}.")
                    }
                } else {
                    sender.sendMessage("${ChatColor.RED}Not enough.  ${Utility.balance_str(sender)} \n" +
                            "${ChatColor.AQUA}Transaction: $amount + $fee (${(Utility.transaction_fee *100).roundToInt()}%)")
                }
            }
            return true;
        }

        return false;
    }
}
