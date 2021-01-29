package com.sleepysquish.blob
import com.mongodb.client.*
import com.mongodb.client.model.*
import com.mongodb.reactivestreams.client.*
import kotlinx.coroutines.*
import org.bson.Document
import org.bukkit.*
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.*

object Persistent {
    val client: CoroutineClient
    val db: CoroutineDatabase
    val players: CoroutineCollection<Document>
    val globals: CoroutineCollection<Document>
    val materials: CoroutineCollection<Document>
    val history: CoroutineCollection<Document>
    val tokens: CoroutineCollection<Document>

    var safe: Boolean
    init {
        try {
            client = MongoClients.create().coroutine
            db = client.getDatabase("slp_data")
            players = db.getCollection<Document>("players")
            globals = db.getCollection<Document>("globals")
            materials = db.getCollection<Document>("materials")
            history = db.getCollection<Document>("com.sleepysquish.blob.history")
            tokens = db.getCollection<Document>("tokens")
            runBlocking {
                get_pool() // initialize pool
            }
            ensure_basic_resources()
            safe = true
        } catch(e:Exception) {
            println("blub blub")
            e.printStackTrace()
            Bukkit.getServer().shutdown()
            safe = false
            throw RuntimeException("MongoDB could not be initialized. Shutting down..")
        }
    }

    fun force_init() {}

    suspend fun store_player(uuid: String) {
        var doc = Document("uuid", uuid)
        players.insertOne(doc)
    }

    suspend fun get_player(uuid: String): Document {
        val pl = players.find(Filters.eq("uuid", uuid))
        if (pl == null) {
            store_player(uuid)
        }
        return players.findOne(Filters.eq("uuid", uuid))!!
    }

    suspend fun get_money(uuid: String): Double {
        val pl = get_player(uuid)
        return if (pl.containsKey("money")) {
            pl["money"] as Double
        } else {
            0.0
        }
    }

    fun set_money(uuid: String, money: Double) {
        GlobalScope.launch {
            players.updateOne(Filters.eq("uuid", uuid), Updates.set("money", money))
        }
    }
    fun add_money(uuid: String, money: Double) {
        GlobalScope.launch {
            players.updateOne(Filters.eq("uuid", uuid), Updates.inc("money", money))
        }
    }

    fun update_uuid_with_name(uuid: String, username: String) {
        GlobalScope.launch {
            players.updateOne(Filters.eq("uuid", uuid), Updates.set("username", username))
        }
    }

    fun async_update_player_version(uuid: String) {
        // update fields with default values
        add_money(uuid, 0.0)
    }

    fun block_username_to_uuid(username: String): String? {
        val query = players.find(Filters.eq("username", username))
        var uuid: String? = null
        runBlocking {
            val pl = query.first()
            if (pl!=null) {
                uuid = pl["uuid"].toString()
            }
        }
        return uuid
    }

    fun block_get_settings(): Document? {
        val query = globals.find(Filters.eq("name", "settings"))
        var value: Document? = null
        runBlocking {
            value = query.first()
        }
        return value
    }


    /*
    pool:
    - amount: Double
     */
    suspend fun get_pool(): Document {
        val query = globals.find(Filters.eq("name", "pool"))
        if (query.first() != null) return query.first()!!
        else {
            var pool = Document("name", "pool").append("amount", 0.0)
            globals.insertOne(pool)
            return pool
        }
    }

    fun async_save_pool(pool: Document) {
        GlobalScope.launch {
            globals.replaceOne(Filters.eq("name", "pool"), pool)
        }
    }

    fun async_pool_add_money(amount: Double) {
        GlobalScope.launch {
            globals.updateOne(Filters.eq("name", "pool"), Updates.inc("amount", amount))
        }
    }

    /* resource pool
    - material --- index
    - amount_mined
    - exp
     */
    fun ensure_basic_resources() {
        ensure_basic_resource(Material.COAL_ORE.createBlockData().asString, 10.0)
        ensure_basic_resource("minecraft:nether_gold_ore", 15.0)
        ensure_basic_resource(Material.DIAMOND_ORE.createBlockData().asString, 50.0)
        ensure_basic_resource(Material.EMERALD_ORE.createBlockData().asString, 75.0)
        ensure_basic_resource(Material.LAPIS_ORE.createBlockData().asString, 35.0)
        ensure_basic_resource(Material.NETHER_QUARTZ_ORE.createBlockData().asString, 10.0)
        ensure_basic_resource(Material.REDSTONE_ORE.createBlockData().asString, 25.0)
    }

    fun ensure_basic_resource(m: String, worth: Double) {
        GlobalScope.launch {
            val query = materials.find(Filters.eq("material", m))
            if (query.first() != null) {
                materials.updateOne(Filters.eq("material", m), Updates.set("value", worth))
            } else {
                var material_info = Document("material", m)
                material_info.append("value", worth)
                material_info.append("mined", 0L)
                materials.insertOne(material_info)
            }
        }

    }

    suspend fun async_mine_resource(m: String):Deferred<Double> = GlobalScope.async {
        val query = materials.find(Filters.eq("material", m))
        var material = query.first()
            if (material != null) {
                val worth = material["value"] as Double
                val mined = (material["mined"] as Long).toDouble()
                var pool_amount = (get_pool()["amount"] as Double) * (Utility.pool_percent_worth * worth * 0.01)
                async_pool_add_money(-pool_amount)
                materials.updateOne(Filters.eq("material", m), Updates.inc("mined", 1))
                return@async ((worth * 1000.0)/(1000.0 + (worth/10.0)*mined)) + pool_amount
            }
        return@async 0.0
        }
}
