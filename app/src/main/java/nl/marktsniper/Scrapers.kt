package nl.marktsniper

import org.json.JSONObject
import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Scrapers {
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"
    private val ADEVINTA = mapOf("marktplaats" to "https://www.marktplaats.nl", "2dehands" to "https://www.2dehands.be")
    private const val VINTED = "https://www.vinted.nl"
    private var vintedSession = false

    init {
        // Vinted geeft alleen resultaten met een sessie-cookie
        if (CookieHandler.getDefault() == null) CookieHandler.setDefault(CookieManager())
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("User-Agent", UA)
        c.setRequestProperty("Accept-Language", "nl-NL,nl;q=0.9")
        c.setRequestProperty("Accept", "application/json, text/html")
        return try {
            val code = c.responseCode
            code to (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        } finally { c.disconnect() }
    }

    private fun adevinta(platform: String, w: Watch): List<Hit> {
        val base = ADEVINTA.getValue(platform)
        var url = "$base/lrp/api/search?query=${enc(w.query)}&limit=50&offset=0&sortBy=SORT_INDEX&sortOrder=DECREASING" +
            "&attributeRanges[]=PriceCents:${w.minPrice * 100}:${w.maxPrice * 100}"
        if (w.categoryId != null) url += "&l1CategoryId=${w.categoryId}"
        val (code, body) = get(url)
        if (code != 200) error("HTTP $code")
        val arr = JSONObject(body).optJSONArray("listings") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val l = arr.getJSONObject(i)
            val img = l.optJSONArray("imageUrls")?.optString(0)?.takeIf { it.isNotEmpty() }
            Hit(
                id = "$platform:${l.getString("itemId")}", watchId = w.id, platform = platform,
                title = l.optString("title"),
                price = (l.optJSONObject("priceInfo")?.optInt("priceCents") ?: 0) / 100.0,
                url = base + l.optString("vipUrl"),
                image = img?.let { if (it.startsWith("//")) "https:$it" else it },
                location = l.optJSONObject("location")?.optString("cityName").orEmpty(),
                description = l.optString("description").take(300),
            )
        }
    }

    private fun vinted(w: Watch): List<Hit> {
        if (!vintedSession) { get(VINTED); vintedSession = true }
        val (code, body) = get("$VINTED/api/v2/catalog/items?search_text=${enc(w.query)}&order=newest_first" +
            "&per_page=50&price_from=${w.minPrice}&price_to=${w.maxPrice}")
        if (code == 401 || code == 403) { vintedSession = false; error("geblokkeerd (HTTP $code)") }
        if (code != 200) error("HTTP $code")
        val arr = JSONObject(body).optJSONArray("items") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val it = arr.getJSONObject(i)
            val p = it.opt("price")
            val price = if (p is JSONObject) p.optString("amount").toDoubleOrNull() ?: 0.0 else it.optString("price").toDoubleOrNull() ?: 0.0
            Hit(
                id = "vinted:${it.get("id")}", watchId = w.id, platform = "vinted", title = it.optString("title"),
                price = price, url = it.optString("url").ifEmpty { "$VINTED/items/${it.get("id")}" },
                image = it.optJSONObject("photo")?.optString("url"), location = "",
                description = it.optString("brand_title"),
            )
        }
    }

    /** Zoekt op alle gekozen platforms; fouten per platform komen terug als tekst. */
    fun search(w: Watch): Pair<List<Hit>, String?> {
        val hits = mutableListOf<Hit>()
        val errors = mutableListOf<String>()
        for (p in w.platforms) {
            runCatching { if (p == "vinted") vinted(w) else adevinta(p, w) }
                .onSuccess { hits += it }
                .onFailure { errors += "$p: ${it.message}" }
        }
        return hits.filter { Matcher.matches(w, it) } to errors.joinToString("\n").ifEmpty { null }
    }
}
