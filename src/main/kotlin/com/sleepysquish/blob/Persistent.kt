package com.sleepysquish.blob
import com.mongodb.client.*
import com.mongodb.client.model.*
import org.bson.Document
import org.bukkit.*

object Persistent {
    val client: MongoClient
    val db: MongoDatabase
    val players: MongoCollection<Document>
    val globals: MongoCollection<Document>
    val materials: MongoCollection<Document>
    val history: MongoCollection<Document>
    val tokens: MongoCollection<Document>

    var safe: Boolean
    init {
        try {
            client = MongoClients.create()
            db = client.getDatabase("slp_data")
            players = db.getCollection("players")
            globals = db.getCollection("globals")
            materials = db.getCollection("materials")
            history = db.getCollection("com.sleepysquish.blob.history")
            tokens = db.getCollection("tokens")
            get_pool() // initialize pool
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

    fun store_player(uuid: String) {
        var doc = Document("uuid", uuid)
        players.insertOne(doc)
    }

    fun get_player(uuid: String): Document {
        val pl = players.find(Filters.eq("uuid", uuid)).first()
        if (pl == null) {
            store_player(uuid)
        }
        return players.find(Filters.eq("uuid", uuid)).first()!!
    }

    fun get_money(uuid: String): Double {
        val pl = get_player(uuid)
        return if (pl.containsKey("money")) {
            pl["money"] as Double
        } else {
            0.0
        }
    }

    fun set_money(uuid: String, money: Double) {
        players.updateOne(Filters.eq("uuid", uuid), Updates.set("money", money))
    }

    fun add_money(uuid: String, money: Double) {
        players.updateOne(Filters.eq("uuid", uuid), Updates.inc("money", money))
    }

    fun update_uuid_with_name(uuid: String, username: String) {
        players.updateOne(Filters.eq("uuid", uuid), Updates.set("username", username))
    }

    fun async_update_player_version(uuid: String) {
        // update fields with default values
        add_money(uuid, 0.0)
    }

    fun block_username_to_uuid(username: String): String? {
        val query = players.find(Filters.eq("username", username))
        if (query.first() != null) return query.first()["uuid"].toString()
        else return null
    }

    fun get_settings(): Document? {
        val query = globals.find(Filters.eq("name", "settings"))
        return query.first()
    }


    /*
    pool:
    - amount: Double
     */
    fun get_pool(): Document {
        val query = globals.find(Filters.eq("name", "pool"))
        if (query.first() != null) return query.first()
        else {
            var pool = Document("name", "pool").append("amount", 0.0)
            globals.insertOne(pool)
            return pool
        }
    }

    fun save_pool(pool: Document) {
        globals.replaceOne(Filters.eq("name", "pool"), pool)
    }

    fun pool_add_money(amount: Double) {
        globals.updateOne(Filters.eq("name", "pool"), Updates.inc("amount", amount))
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
        val query = materials.find(Filters.eq("material", m))
        if (query.any()) {
            materials.updateOne(Filters.eq("material", m), Updates.set("value", worth))
            return
        }

        var material_info = Document("material", m)
        material_info.append("value", worth)
        material_info.append("mined", 0L)
        materials.insertOne(material_info)
    }

    fun mine_resource(m: String): Double {
        val query = materials.find(Filters.eq("material", m))
        if (query.any()) {
            var material = query.first()
            val worth = material["value"] as Double
            val mined = (material["mined"] as Long).toDouble()
            var pool_amount = (get_pool()["amount"] as Double) * (Utility.pool_percent_worth * worth * 0.01)
            pool_add_money(-pool_amount)
            materials.updateOne(Filters.eq("material", m), Updates.inc("mined", 1))
            return ((worth * 1000.0)/(1000.0 + (worth/10.0)*mined)) + pool_amount
        }
        return 0.0
    }

}
