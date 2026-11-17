package com.tas5805m.ctrl.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore("presets")

class PresetRepository(private val context: Context) {
    private val KEY = stringPreferencesKey("presets_json")

    val presets: Flow<List<Preset>> = context.dataStore.data.map { prefs ->
        val raw = prefs[KEY] ?: "[]"
        parse(raw)
    }

    suspend fun add(p: Preset) {
        context.dataStore.edit { prefs ->
            val list = parse(prefs[KEY] ?: "[]").toMutableList()
            list.add(0, p)
            prefs[KEY] = serialize(list)
        }
    }

    suspend fun remove(index: Int) {
        context.dataStore.edit { prefs ->
            val list = parse(prefs[KEY] ?: "[]").toMutableList()
            if (index in list.indices) list.removeAt(index)
            prefs[KEY] = serialize(list)
        }
    }

    private fun serialize(list: List<Preset>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(JSONObject().apply {
                put("name", p.name)
                put("mode", p.mode)
                put("xover", p.xover)
                put("master", p.master)
                put("sat", p.sat)
                put("sub", p.sub)
                put("ctr", p.ctr)
                put("gain", p.gain)
                put("eqOn", p.eqOn)
                put("ll", p.ll.toDouble()); put("lr", p.lr.toDouble())
                put("rl", p.rl.toDouble()); put("rr", p.rr.toDouble())
                put("eqLeft", JSONArray(p.eqLeft))
                put("eqRight", JSONArray(p.eqRight))
                put("created", p.created)
            })
        }
        return arr.toString()
    }

    private fun parse(raw: String): List<Preset> {
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Preset(
                    name = o.optString("name"),
                    mode = o.optInt("mode"),
                    xover = o.optInt("xover", 100),
                    master = o.optInt("master", 80),
                    sat = o.optInt("sat", 100),
                    sub = o.optInt("sub", 100),
                    ctr = o.optInt("ctr", 100),
                    gain = o.optInt("gain", 18),
                    eqOn = o.optBoolean("eqOn", true),
                    ll = o.optDouble("ll", 1.0).toFloat(),
                    lr = o.optDouble("lr", 0.0).toFloat(),
                    rl = o.optDouble("rl", 0.0).toFloat(),
                    rr = o.optDouble("rr", 1.0).toFloat(),
                    eqLeft = (0 until 15).map { o.optJSONArray("eqLeft")?.optDouble(it, 0.0)?.toFloat() ?: 0f },
                    eqRight = (0 until 15).map { o.optJSONArray("eqRight")?.optDouble(it, 0.0)?.toFloat() ?: 0f },
                    created = o.optString("created")
                )
            }
        } catch (_: Exception) { emptyList() }
    }
}
