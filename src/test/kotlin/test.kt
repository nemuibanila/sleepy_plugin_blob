import java.util.*

fun main(args: Array<String>) {
    val a = UUID.randomUUID()
    val b = UUID.randomUUID()
    val c = UUID.randomUUID()
    mongo.store_player(a.toString())
    mongo.store_player(b.toString())
    mongo.store_player(c.toString())
    assert(mongo.get_player(a.toString()) != null)
    assert(mongo.get_player(b.toString()) != null)
    assert(mongo.get_player(c.toString()) != null)

    mongo.add_money(a.toString(), 500.0)
    mongo.add_money(a.toString(), -220.0)
    mongo.add_money(a.toString(), 1.0)
    mongo.set_money(b.toString(), 120.0)


}
