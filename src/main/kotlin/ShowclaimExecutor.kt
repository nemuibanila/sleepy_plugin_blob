import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.regions.*
import org.bukkit.command.*
import org.bukkit.entity.Player
import java.util.*

object ShowclaimExecutor : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender is Player) {
            val player_location = BukkitAdapter.adapt(sender.location)
            val container = WorldGuard.getInstance().platform.regionContainer
            val regions = container.get(BukkitAdapter.adapt(sender.location.world))
            if (regions == null) return false;

            if (args.size > 0) {
                println(args[0])
                val show_region = regions.getRegion(args[0])
                if (show_region != null) {
                    draw_region(show_region, sender)
                } else {
                    sender.sendMessage("Region ${args[0]} does not exist.")
                }
            }

            // just /showclaims
            if (BlockPacketCleanups.has_stuff(sender)) {
                BlockPacketCleanups.reset_map_painting(sender)
                sender.sendMessage("Toggled claim visibility")
                return true
            }

            val a = BlockVector3.at(sender.location.x - 25, 0.0, sender.location.z - 25)
            val b = BlockVector3.at(sender.location.x + 25, 0.0, sender.location.z + 25)
            val query_region = ProtectedCuboidRegion(UUID.randomUUID().toString(), a, b)

            for (r in regions.getApplicableRegions(query_region).filter { it.isPhysicalArea && it.type == RegionType.CUBOID }) {
                draw_region(r, sender)
            }

            sender.sendMessage("type /showclaims again to hide")
            return true
        } else {
            return false
        }
    }
}
