import com.sleepysquish.blob.mongo

fun main(args: Array<String>) {
    for (index in mongo.players.listIndexes()) {
        println(index)
    }
}
