package nl.marktsniper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Properties

val ALL_PLATFORMS = listOf("marktplaats", "2dehands", "vinted")

data class Watch(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val query: String,
    val include: String = "",
    val exclude: String = "",
    val minPrice: Int = 0,
    val maxPrice: Int = 250,
    val platforms: List<String> = ALL_PLATFORMS,
    val categoryId: Int? = null,
    val active: Boolean = true,
    // false zolang de eerste zoekronde nog niet klaar is: bestaande advertenties geven dan geen melding
    val primed: Boolean = false,
    val lastCheck: Long = 0,
    val lastError: String? = null,
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("query", query); put("include", include)
        put("exclude", exclude); put("minPrice", minPrice); put("maxPrice", maxPrice)
        put("platforms", JSONArray(platforms)); put("categoryId", categoryId ?: JSONObject.NULL)
        put("active", active); put("primed", primed); put("lastCheck", lastCheck)
        put("lastError", lastError ?: JSONObject.NULL)
    }

    companion object {
        fun from(o: JSONObject) = Watch(
            o.getLong("id"), o.getString("name"), o.getString("query"), o.optString("include"),
            o.optString("exclude"), o.optInt("minPrice"), o.optInt("maxPrice", 250),
            o.getJSONArray("platforms").let { a -> List(a.length()) { a.getString(it) } },
            if (o.isNull("categoryId")) null else o.getInt("categoryId"),
            o.optBoolean("active", true), o.optBoolean("primed"), o.optLong("lastCheck"),
            if (o.isNull("lastError")) null else o.optString("lastError"),
        )
    }
}

data class Hit(
    val id: String,
    val watchId: Long,
    val platform: String,
    val title: String,
    val price: Double,
    val url: String,
    val image: String?,
    val location: String,
    val description: String = "",
    val foundAt: Long = System.currentTimeMillis(),
    val notified: Boolean = false,
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("watchId", watchId); put("platform", platform); put("title", title)
        put("price", price); put("url", url); put("image", image ?: JSONObject.NULL)
        put("location", location); put("foundAt", foundAt); put("notified", notified)
    }

    companion object {
        fun from(o: JSONObject) = Hit(
            o.getString("id"), o.getLong("watchId"), o.getString("platform"), o.getString("title"),
            o.getDouble("price"), o.getString("url"), if (o.isNull("image")) null else o.getString("image"),
            o.optString("location"), "", o.getLong("foundAt"), o.optBoolean("notified"),
        )
    }
}

object Matcher {
    private fun terms(s: String) = s.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    fun matches(w: Watch, h: Hit): Boolean {
        if (h.price <= 0 || h.price < w.minPrice || h.price > w.maxPrice) return false
        val title = h.title.lowercase()
        val text = "$title ${h.description.lowercase()}"
        val inc = terms(w.include)
        // los woord: "iphone 14" mag niet matchen op "iphone 141"
        if (inc.isNotEmpty() && inc.none { Regex("\\b" + Regex.escape(it) + "\\b").containsMatchIn(title) }) return false
        return terms(w.exclude).none { Regex("\\b" + Regex.escape(it)).containsMatchIn(text) }
    }
}

/** Eenvoudige opslag in JSON-bestanden; gedeeld door UI en achtergronddienst. */
object Store {
    private lateinit var dir: File
    private val _watches = MutableStateFlow<List<Watch>>(emptyList())
    private val _hits = MutableStateFlow<List<Hit>>(emptyList())
    val watches: StateFlow<List<Watch>> = _watches
    val hits: StateFlow<List<Hit>> = _hits
    private val _interval = MutableStateFlow(2)
    val intervalMinutes: StateFlow<Int> = _interval
    private val _running = MutableStateFlow(true)
    val running: StateFlow<Boolean> = _running

    private val props = Properties()

    @Synchronized
    fun init(dataDir: File) {
        if (::dir.isInitialized) return
        dir = dataDir.apply { mkdirs() }
        _watches.value = read("watches.json").map { Watch.from(it) }
        _hits.value = read("hits.json").map { Hit.from(it) }
        File(dir, "settings.properties").takeIf { it.exists() }?.reader()?.use(props::load)
        _interval.value = props.getProperty("interval", "2").toInt()
        _running.value = props.getProperty("running", "true").toBoolean()
    }

    @Synchronized
    private fun saveProp(key: String, value: Any) {
        props.setProperty(key, value.toString())
        File(dir, "settings.properties").writer().use { props.store(it, null) }
    }

    private fun read(name: String): List<JSONObject> {
        val f = File(dir, name)
        if (!f.exists()) return emptyList()
        return runCatching { JSONArray(f.readText()).let { a -> List(a.length()) { a.getJSONObject(it) } } }
            .getOrDefault(emptyList())
    }

    private fun write(name: String, items: List<JSONObject>) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(JSONArray(items).toString())
        tmp.renameTo(File(dir, name))
    }

    @Synchronized
    fun saveWatch(w: Watch, filtersChanged: Boolean = true) {
        val nw = if (filtersChanged) w.copy(primed = false, lastError = null) else w
        _watches.value = listOf(nw) + _watches.value.filter { it.id != w.id }
        if (filtersChanged) _hits.value = _hits.value.filter { it.watchId != w.id }
        persist()
    }

    @Synchronized
    fun deleteWatch(id: Long) {
        _watches.value = _watches.value.filter { it.id != id }
        _hits.value = _hits.value.filter { it.watchId != id }
        persist()
    }

    /** Voegt nieuwe vondsten toe en geeft de werkelijk nieuwe terug. */
    @Synchronized
    fun addHits(w: Watch, found: List<Hit>, error: String?): List<Hit> {
        val current = _watches.value.find { it.id == w.id } ?: return emptyList()
        val known = _hits.value.filter { it.watchId == w.id }.map { it.id }.toHashSet()
        val fresh = found.filter { it.id !in known }.distinctBy { it.id }
            .map { it.copy(watchId = w.id, notified = current.primed) }
        _hits.value = (fresh + _hits.value).take(1500)
        _watches.value = _watches.value.map {
            if (it.id == w.id) it.copy(primed = true, lastCheck = System.currentTimeMillis(), lastError = error) else it
        }
        persist()
        return if (current.primed) fresh else emptyList()
    }

    fun setInterval(minutes: Int) {
        _interval.value = minutes
        saveProp("interval", minutes)
    }

    fun setRunning(on: Boolean) {
        _running.value = on
        saveProp("running", on)
    }

    private fun persist() {
        write("watches.json", _watches.value.map { it.toJson() })
        write("hits.json", _hits.value.map { it.toJson() })
    }
}

data class Category(val id: Int?, val name: String)

val CATEGORIES = listOf(
    Category(null, "Alle categorieën"), Category(1, "Antiek en Kunst"), Category(31, "Audio, Tv en Foto"),
    Category(91, "Auto's"), Category(2600, "Auto-onderdelen"), Category(48, "Auto diversen"),
    Category(201, "Boeken"), Category(289, "Caravans en Kamperen"), Category(1744, "Cd's en Dvd's"),
    Category(322, "Computers en Software"), Category(1098, "Diensten en Vakmensen"),
    Category(395, "Dieren en Toebehoren"), Category(239, "Doe-het-zelf en Verbouw"),
    Category(445, "Fietsen en Brommers"), Category(1099, "Hobby en Vrije tijd"),
    Category(504, "Huis en Inrichting"), Category(565, "Kinderen en Baby's"),
    Category(621, "Kleding | Dames"), Category(1776, "Kleding | Heren"), Category(678, "Motoren"),
    Category(728, "Muziek en Instrumenten"), Category(1784, "Postzegels en Munten"),
    Category(1826, "Sieraden en Tassen"), Category(356, "Spelcomputers, Games"),
    Category(784, "Sport en Fitness"), Category(820, "Telecommunicatie"),
    Category(1984, "Tickets en Kaartjes"), Category(1847, "Tuin en Terras"), Category(895, "Verzamelen"),
    Category(976, "Watersport en Boten"), Category(537, "Witgoed en Apparatuur"),
    Category(1085, "Zakelijke goederen"), Category(428, "Diversen"),
)

val PRESETS = listOf(
    Watch(0, "iPhone 13 / 14 / 15", "iphone", "iphone 13, iphone 14, iphone 15",
        "hoesje, hoes, case, kapot, defect, onderdelen, scherm, icloud, glas, oplader, doosje, gezocht", 100, 400, categoryId = 820),
    Watch(0, "Laptop onder €250", "laptop", "",
        "kapot, defect, onderdelen, tas, hoes, oplader, adapter, gezocht, toetsenbord", 50, 250, categoryId = 322),
    Watch(0, "PlayStation 5", "ps5", "ps5, playstation 5",
        "game, spel, controller, hoes, kapot, defect, gezocht, fifa", 150, 330, categoryId = 356),
    Watch(0, "Switch OLED", "switch oled", "oled", "game, spel, hoes, kapot, defect, gezocht, case", 80, 220, categoryId = 356),
    Watch(0, "AirPods Pro", "airpods pro", "airpods pro",
        "case, hoesje, los, linker, rechter, kapot, nep, replica, gezocht", 40, 130),
    Watch(0, "MacBook Air M1/M2", "macbook air", "m1, m2", "kapot, defect, onderdelen, hoes, oplader, gezocht", 250, 600, categoryId = 322),
)
