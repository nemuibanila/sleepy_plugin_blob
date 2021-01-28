import com.sleepysquish.blob.Persistent

fun main(args: Array<String>) {
    for (index in Persistent.players.listIndexes()) {
        println(index)
    }
}
