package com.creation.city.clicker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ScriptStore {

    private fun dir(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(null), "scripts")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun saveScript(ctx: Context, script: Script) {
        val f = File(dir(ctx), script.name + ".json")
        val arr = JSONArray()
        for (p in script.points) {
            val o = JSONObject().apply {
                put("type", p.type)
                put("x", p.x)
                put("y", p.y)
                put("ex", p.ex)
                put("ey", p.ey)
                put("delayBeforeMs", p.delayBeforeMs)
                put("swipeMs", p.swipeMs)
            }
            arr.put(o)
        }
        val root = JSONObject().apply {
            put("name", script.name)
            put("createdAt", script.createdAt)
            put("points", arr)
        }
        f.writeText(root.toString())
    }

    fun loadScript(ctx: Context, name: String): Script? {
        val f = File(dir(ctx), name + ".json")
        if (!f.exists()) return null
        val root = JSONObject(f.readText())
        val arr = root.getJSONArray("points")
        val pts = mutableListOf<TapAction>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            pts.add(
                TapAction(
                    type = o.optString("type", "tap"),
                    x = o.optDouble("x").toFloat(),
                    y = o.optDouble("y").toFloat(),
                    ex = o.optDouble("ex").toFloat(),
                    ey = o.optDouble("ey").toFloat(),
                    delayBeforeMs = o.optLong("delayBeforeMs"),
                    swipeMs = o.optLong("swipeMs", 300)
                )
            )
        }
        return Script(root.optString("name", name), pts, root.optLong("createdAt"))
    }

    fun listScripts(ctx: Context): List<String> {
        return dir(ctx).listFiles { f -> f.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.sorted() ?: emptyList()
    }

    fun deleteScript(ctx: Context, name: String) {
        File(dir(ctx), name + ".json").delete()
    }
}
