package dev.wiptv.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** The id of the Favourites group, which sits above your provider's groups. */
const val FAVOURITES_ID = "★favourites"

/**
 * Your favourite channels, from any group, in the order you added them. Kept on the TV with
 * each channel's details, so Favourites shows instantly without loading every group.
 */
class FavouritePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("favourites", Context.MODE_PRIVATE)

    fun load(): List<Channel> = runCatching {
        val array = JSONArray(prefs.getString("channels", "[]"))
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Channel(
                id = item.getLong("id"),
                number = item.optInt("number", i + 1),
                name = item.optString("name"),
                logo = item.optString("logo").takeIf { it.isNotBlank() },
                groupId = item.optString("groupId"),
                guideId = item.optString("guideId").takeIf { it.isNotBlank() },
                catchUp = item.optBoolean("catchUp"),
            )
        }
    }.getOrDefault(emptyList())

    fun save(channels: List<Channel>) {
        val array = JSONArray()
        for (c in channels) {
            array.put(
                JSONObject()
                    .put("id", c.id)
                    .put("number", c.number)
                    .put("name", c.name)
                    .put("logo", c.logo ?: "")
                    .put("groupId", c.groupId)
                    .put("guideId", c.guideId ?: "")
                    .put("catchUp", c.catchUp),
            )
        }
        prefs.edit().putString("channels", array.toString()).apply()
    }

    companion object {
        /** Adds the channel if it isn't a favourite yet, otherwise removes it. */
        fun toggle(favourites: List<Channel>, channel: Channel): List<Channel> =
            if (favourites.any { it.id == channel.id }) favourites.filterNot { it.id == channel.id }
            else favourites + channel
    }
}
