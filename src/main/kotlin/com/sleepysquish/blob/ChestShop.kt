package com.sleepysquish.blob
import org.bukkit.*
import org.bukkit.block.*
import org.bukkit.block.data.Directional
import org.bukkit.entity.Player
import org.bukkit.event.*
import org.bukkit.event.block.*
import org.bukkit.event.inventory.*
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.*
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.HashMap

object ChestShop : Listener {

    data class ShopInfo(val player : Player, val shop_owner_uuid : String, val cost : Double, var shop_limit : Double?, val block: BlockInfo, val buy_sell : BuySell) {
    }
    data class BlockInfo(val sign: Block, val container: Block)

    var players_in_shops = HashMap<UUID, ShopInfo>()

    @EventHandler
    fun onPlayerInteract(e: PlayerInteractEvent) {
        if(e.action != Action.RIGHT_CLICK_BLOCK) return

        val block = e.clickedBlock
        if (block != null && block.state is Sign && block.blockData is Directional) {
            if(is_restricted(e.player.uniqueId)) return

            val sign = block.state as Sign

            val direction = block.blockData as Directional
            val behind = block.getRelative(direction.facing.oppositeFace)

            if (behind.state is Chest) {
                val line_1 = sign.getLine(0)
                var buy_sell = BuySell.SELL
                if(sign.getLine(0).startsWith("sell:", true)) {
                    buy_sell = BuySell.SELL
                } else if (sign.getLine(0).startsWith("buy:", true)) {
                    buy_sell = BuySell.BUY
                } else {
                    return
                }

                val cost_str = line_1.split(' ')
                if (cost_str.size != 2) return

                val limit = sign.getLine(1).toDoubleOrNull()
                val container = behind.state as Container
                val uuid = sign_decode_uuid(sign.getLine(2), sign.getLine(3))
                val cost = cost_str[1].toDouble()
                if (cost < 0) {
                    return
                }

                val player = e.player
                val shop_info = ShopInfo(player,uuid.toString() ,cost, limit,
                    BlockInfo(block, behind), buy_sell)

                e.isCancelled = false;

                if (!players_in_shops.any { it.value.block.sign.location == shop_info.block.sign.location }) {
                    acquire_block(shop_info)
                    val view = e.player.openInventory(container.snapshotInventory)
                } else {
                    e.player.sendMessage("${ChatColor.RED}Someone is using this shop. Please wait.")
                }

                e.isCancelled = true
            }
        }
    }

    fun verify_sign_shop(lines: Array<String>): Boolean {
        try {
            val double_value = lines[0].split(' ')[1].toDoubleOrNull()
            if (double_value == null) return false
            val valid =  double_value >= 0.0
            return valid
        }
        catch (e: Exception) {
            return false;
        }
        return false;
    }

    fun potential_shop(line1: String): Boolean {
        return line1.startsWith("sell:", true) || line1.startsWith("buy:", true)
    }

    val decoder = Base64.getDecoder()
    fun sign_decode_uuid(line1: String, line2: String): UUID {
        val a_dec = decoder.decode(line1)
        val b_dec = decoder.decode(line2)
        val a_restore = ByteBuffer.wrap(a_dec).getLong(0)
        val b_restore = ByteBuffer.wrap(b_dec).getLong(0)
        return UUID(b_restore, a_restore)
    }

    val encoder = Base64.getEncoder()
    fun sign_encode_uuid(uuid: UUID): Array<String?> {
        val a = uuid.leastSignificantBits.toBigInteger().toByteArray()
        val b = uuid.mostSignificantBits.toBigInteger().toByteArray()
        val returns = Array<String?>(2) { null }
        returns[0] = encoder.encodeToString(a)
        returns[1] = encoder.encodeToString(b)
        return returns
    }


    enum class BuySell {
        BUY, // Store buys
        SELL // Store sells
    }


    @EventHandler
    fun onInventoryOpen(e: InventoryOpenEvent) {
        if (players_in_shops.containsKey(e.player.uniqueId)) {
            e.isCancelled = false
        }
    }


    @EventHandler
    fun onSignPlace(e: SignChangeEvent) {
        // check if chest exists
        val direction = e.block.blockData as Directional
        val behind = e.block.getRelative(direction.facing.oppositeFace)
        if (behind.state is Chest) else return


        if(potential_shop(e.lines[0])) {
            if(verify_sign_shop(e.lines)) {
                val uuid_str = sign_encode_uuid(e.player.uniqueId)
                e.setLine(2, uuid_str[0])
                e.setLine(3, uuid_str[1])
                e.player.sendMessage("${ChatColor.AQUA}Shop created successfully.")
                e.player.sendMessage("${ChatColor.AQUA}Each transaction will cost an additional ${Math.round(Utility.transaction_fee *100)}% in fees.")
                if(e.lines[0].startsWith("buy", true)) {
                    e.player.sendMessage("${ChatColor.AQUA}Add at least 1 of each type of item you want to buy into the chest.")
                } else if(e.lines[0].startsWith("sell", true)){
                    e.player.sendMessage("${ChatColor.AQUA}Add the items you want to sell into the chest.")
                }
            } else {
                e.block.breakNaturally()
            }
        }
    }

    @EventHandler
    fun onInventoryMoveItem(e: InventoryClickEvent) {

        if(e.clickedInventory == null) return

        if (e.currentItem != null && e.whoClicked is Player) {
            val shop_info = players_in_shops[e.whoClicked.uniqueId]
            if(shop_info == null) return
            e.isCancelled = true
            if(shop_info.buy_sell == BuySell.SELL && e.clickedInventory == e.whoClicked.inventory) {
                return
            }

            if(shop_info.buy_sell == BuySell.BUY && e.clickedInventory != e.whoClicked.inventory) {
                return
            }


            var buyer_uuid = shop_info.player.uniqueId.toString()
            var seller_uuid = shop_info.shop_owner_uuid
            var buyer_inventory = shop_info.player.inventory as Inventory
            var seller_inventory = (shop_info.block.container.state as Chest).inventory as Inventory
            var ghost_inventory = e.clickedInventory

            var action = "bought"
            var direction = "from"
            if (shop_info.buy_sell == BuySell.BUY) {
                buyer_uuid = shop_info.shop_owner_uuid
                seller_uuid = shop_info.player.uniqueId.toString()
                buyer_inventory = (shop_info.block.container.state as Chest).inventory
                seller_inventory = shop_info.player.inventory
                action = "sold"
                direction = "to"

                if (!inventory_contains_type(buyer_inventory, e.currentItem)) return
            }

            // get money first..
            val buyer_money = Persistent.get_money(buyer_uuid)

            // get amount bought
            val clicked_item = seller_inventory.getItem(e.slot)
            if(clicked_item == null) {
                return
            }

            var amount = 1
            if (e.isLeftClick) {
                amount = Math.min(8, clicked_item.amount)
            }
            if (e.isShiftClick) {
                amount = clicked_item.amount
            }

            val actor = shop_info.player

            // check if has enough money
            val total_base_cost = amount*shop_info.cost
            if (total_base_cost < 0) {
                e.whoClicked.sendMessage("negative cost shop. no.")
                return
            }

            val total_expected_cost = Utility.amount_plus_fee(amount * shop_info.cost)
            if (Utility.amount_plus_fee(buyer_money) < total_expected_cost) {
                if (shop_info.buy_sell == BuySell.SELL) {
                    e.whoClicked.sendMessage("${ChatColor.YELLOW}Not enough ${Utility.currency}. Cost: ${total_expected_cost} (${total_base_cost} +${
                        Utility.amount_fee(
                            total_base_cost
                        )
                    }) ${Utility.currency}")
                } else {
                    e.whoClicked.sendMessage("${ChatColor.GREEN}The shop owner can not afford this :)")
                }
                return
            }

            // for selling - check if enough limit is left
            if (shop_info.shop_limit != null && total_expected_cost > shop_info.shop_limit!!) {
                e.whoClicked.sendMessage("${ChatColor.RED}Shop limit exceeded. The shop does not want to buy any more items.")
                return
            }

            // add to buyers inventory
            val buyer_get_item = ItemStack(clicked_item)
            amount = Math.min(amount, clicked_item.amount)
            buyer_get_item.amount = amount
            val could_not_add = buyer_inventory.addItem(buyer_get_item)
            if (shop_info.buy_sell == BuySell.BUY) {
            }

            if(could_not_add.any()) {
                val couldnt_item = could_not_add[0]
                amount -= couldnt_item!!.amount
                e.whoClicked.sendMessage("${ChatColor.YELLOW}Inventory is too full. Only ${action} ${amount}.")
            }

            // remove from sellers inventory
            if (amount < clicked_item.amount) {
                clicked_item.amount -= amount
                seller_inventory.setItem(e.slot, clicked_item)
                if (shop_info.buy_sell == BuySell.SELL) {
                    ghost_inventory!!.setItem(e.slot, e.currentItem!!)
                }
            } else {
                seller_inventory.setItem(e.slot, null)
                if (shop_info.buy_sell == BuySell.SELL) {
                    ghost_inventory!!.setItem(e.slot, null)
                }
            }

            actor.updateInventory()
            val total_cost = amount*shop_info.cost
            // for selling update limit with total_cost
            if (shop_info.buy_sell == BuySell.BUY && shop_info.shop_limit != null) {
                shop_info.shop_limit = shop_info.shop_limit!! - Utility.amount_plus_fee(total_cost)
                val new_limit_text = "%.4f".format(shop_info.shop_limit)
                val sign = shop_info.block.sign.state as Sign

                sign.setLine(1, new_limit_text)
                sign.update(true, false)
            }

            val buy_sell = shop_info.buy_sell
            Bukkit.getScheduler().runTaskAsynchronously(SleepyBlob.instance, Runnable {
                Persistent.add_money(seller_uuid, total_cost)
                Persistent.pool_add_money(Utility.amount_fee(total_cost))
                Persistent.add_money(buyer_uuid, -Utility.amount_plus_fee(total_cost))
                val amount_shown = if(buy_sell == BuySell.BUY) total_cost else Utility.amount_plus_fee(total_cost)
                val user = Persistent.get_player(shop_info.shop_owner_uuid)["username"] as String
                actor.sendMessage("${ChatColor.AQUA}Shop: ${action} ${amount}x ${clicked_item.type} for ${amount_shown} ${direction} ${user}")
            })

        }
    }

    fun inventory_contains_type(inv: Inventory, itemStack: ItemStack?): Boolean {
        if(itemStack == null) return false
        for(inventory_stack in inv) {
            if (inventory_stack != null && inventory_stack.isSimilar(itemStack)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    fun onInventoryClose(e: InventoryCloseEvent) {
        if (is_restricted(e.player.uniqueId)) {
            dequire_block(e.player.uniqueId)
        }
    }

    fun dequire_block(player: UUID) {
        players_in_shops.remove(player)
    }

    fun acquire_block(info: ShopInfo) {
        players_in_shops[info.player.uniqueId] = info
    }

    fun is_restricted(player: UUID): Boolean {
        return players_in_shops.containsKey(player)
    }


}
