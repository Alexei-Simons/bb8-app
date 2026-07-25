package com.bb8.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class MacroRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadAll(): List<SavedMacro> {
        val raw = prefs.getString(KEY_MACROS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    parseMacro(array.getJSONObject(i))?.let(::add)
                }
            }
        }.getOrElse { emptyList() }
    }

    fun save(macro: SavedMacro) {
        val current = loadAll().filterNot { it.id == macro.id }
        persist(current + macro)
    }

    fun delete(id: String) {
        persist(loadAll().filterNot { it.id == id })
    }

    fun newId(): String = UUID.randomUUID().toString()

    private fun persist(macros: List<SavedMacro>) {
        val array = JSONArray()
        macros.forEach { array.put(encodeMacro(it)) }
        prefs.edit().putString(KEY_MACROS, array.toString()).apply()
    }

    private fun encodeMacro(macro: SavedMacro): JSONObject {
        val steps = JSONArray()
        macro.steps.forEach { step ->
            steps.put(
                JSONObject()
                    .put("type", step.type)
                    .put("speed", step.speed)
                    .put("heading", step.heading)
                    .put("r", step.r)
                    .put("g", step.g)
                    .put("b", step.b)
                    .put("delayMs", step.delayMs),
            )
        }
        return JSONObject()
            .put("id", macro.id)
            .put("name", macro.name)
            .put("createdAtMs", macro.createdAtMs)
            .put("steps", steps)
    }

    private fun parseMacro(obj: JSONObject): SavedMacro? {
        val stepsArray = obj.optJSONArray("steps") ?: return null
        val steps = buildList {
            for (i in 0 until stepsArray.length()) {
                val step = stepsArray.getJSONObject(i)
                add(
                    MacroStepDto(
                        type = step.getString("type"),
                        speed = step.optInt("speed"),
                        heading = step.optInt("heading"),
                        r = step.optInt("r"),
                        g = step.optInt("g"),
                        b = step.optInt("b"),
                        delayMs = step.optInt("delayMs"),
                    ),
                )
            }
        }
        return SavedMacro(
            id = obj.getString("id"),
            name = obj.getString("name"),
            steps = steps,
            createdAtMs = obj.optLong("createdAtMs"),
        )
    }

    companion object {
        private const val PREFS_NAME = "bb8_macros"
        private const val KEY_MACROS = "saved_macros"
    }
}
