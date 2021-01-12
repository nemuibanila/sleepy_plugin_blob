import com.google.gson.*
import org.bukkit.ChatColor

class JsonChat {
    companion object {
        val gson = Gson()
    }

    val array = JsonArray()
    init {
    }

    fun text(text: String): JsonChat {
        val obj = JsonObject()
        obj.addProperty("text", text)
        array.add(obj)
        return this
    }

    fun textln(text: String): JsonChat {
        return text(text + '\n')
    }

    fun add_command(command: String): JsonChat {
        val obj = JsonObject()
        obj.addProperty("action", "run_command")
        obj.addProperty("value", command)
        array.last().asJsonObject.add("clickEvent",obj)
        return this
    }

    fun add_color(chat_color: ChatColor): JsonChat {
        array.last().asJsonObject.addProperty("color", chat_color.name.toLowerCase())
        return this
    }

    fun done(): String {
        return array.toString()
    }
}
