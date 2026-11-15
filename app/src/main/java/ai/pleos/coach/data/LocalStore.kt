package ai.pleos.coach.data

import android.content.Context
import org.json.JSONObject

/** One atomic preferences entry; schema failures are preserved for diagnosis. */
class LocalStore(context: Context) {
    private val prefs = context.getSharedPreferences("donghaeng_local", Context.MODE_PRIVATE)
    var warning: String? = null
        private set

    fun load(): JSONObject? {
        val raw = prefs.getString("state", null) ?: return null
        return runCatching { JSONObject(raw).also { require(it.getInt("version") == 1); StateCodec.decode(it) } }
            .getOrElse {
                prefs.edit().putString("unreadable_backup", raw).apply()
                warning = "저장된 기록을 읽지 못했어요. 원본은 보관했습니다."
                null
            }
    }
    fun save(state: JSONObject) { prefs.edit().putString("state", state.toString()).apply() }
    fun saveRoute(json: String) { prefs.edit().putString("live_route", json).apply() }
    fun loadRoute(): String? = prefs.getString("live_route", null)
}
