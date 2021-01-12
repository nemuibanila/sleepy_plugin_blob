import org.bukkit.entity.Player
import java.util.*
import kotlin.collections.HashMap

object BlockPacketCleanups {
    var paint_cleanups : HashMap<UUID, Queue<Runnable>> = HashMap()
    fun add_cleanup(uuid: UUID, r: Runnable) {
        if (!paint_cleanups.containsKey(uuid)) {
            paint_cleanups[uuid] = LinkedList<Runnable>()
        }

        paint_cleanups[uuid]!!.add(r)
    }

    fun reset_map_painting(pl: Player) {
        if (paint_cleanups.containsKey(pl.uniqueId)) {
            val r_queue = paint_cleanups.remove(pl.uniqueId)!!
            for (r in r_queue) {
                r.run()
            }
        }
        paint_cleanups.remove(pl.uniqueId)
    }

    fun has_stuff(pl: Player): Boolean {
        return paint_cleanups.containsKey(pl.uniqueId)
    }
}
