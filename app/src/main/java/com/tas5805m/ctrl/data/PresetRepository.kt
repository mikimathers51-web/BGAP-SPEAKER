package com.tas5805m.ctrl.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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

    private fun serialize(list: List<Preset>): String = PresetJson.toJson(list)

    private fun parse(raw: String): List<Preset> = PresetJson.fromJson(raw)

    /** Semua preset pengguna sebagai JSON (untuk dibagikan / dicadangkan). */
    suspend fun exportJson(): String = serialize(presets.first())

    /** Gabungkan preset dari JSON ke depan daftar. Mengembalikan jumlah preset yang diimpor (0 = JSON tidak valid). */
    suspend fun importJson(raw: String): Int {
        val incoming = parse(raw.trim()).filter { it.name.isNotBlank() }.map { it.copy(builtin = false) }
        if (incoming.isEmpty()) return 0
        context.dataStore.edit { prefs ->
            val list = parse(prefs[KEY] ?: "[]").toMutableList()
            list.addAll(0, incoming)
            prefs[KEY] = serialize(list)
        }
        return incoming.size
    }
}
