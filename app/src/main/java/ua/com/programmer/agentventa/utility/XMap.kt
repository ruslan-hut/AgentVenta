package ua.com.programmer.agentventa.utility

import android.content.ContentValues
import android.util.Log
import org.json.JSONObject

class XMap {

    private var values: ContentValues = ContentValues()
    private var hasValues = false

    private fun loadValuesFromJson(json: JSONObject) {
        val columns = json.names()
        if (columns != null) {
            for (column in 0 until columns.length()) {
                val key = columns[column].toString()
                when (val value = json.get(key)) {
                    is Boolean -> values.put(key, value)
                    is Double -> values.put(key, value)
                    is Int -> values.put(key, value)
                    is Long -> values.put(key, value)
                    else -> values.put(key, value.toString())
                }
            }
        }
        hasValues = values.size() > 0
    }

    constructor(text: String) {
        if (text.isNotEmpty()) {
            try {
                val json = JSONObject(text)
                loadValuesFromJson(json)
            }catch (e: Exception) {
                Log.e("XMap", "String constructor failed: $e")
            }
        }
    }

    constructor(map: Map<*, *>?) {
        if (map != null) {
            for (item in map) {
                val key = item.key.toString()
                if (key.isNotEmpty()) {
                    when (item.value) {
                        is Boolean -> values.put(key, item.value as Boolean)
                        is Double -> values.put(key, item.value as Double)
                        is Int -> values.put(key, item.value as Int)
                        is Long -> values.put(key, item.value as Long)
                        is Map<*, *> -> values.put(key, JSONObject(item.value as Map<*, *>).toString())
                        else -> values.put(key, item.value.toString())
                    }
                }
            }
        }
        hasValues = values.size() > 0
    }

    fun getString(key: String): String {
        var value = ""
        if (values.containsKey(key)) {
            value = values.getAsString(key) ?: ""
        }
        return value
    }

    fun getBoolean(key: String): Boolean {
        var value = false
        if (getString(key).isNotEmpty()) {
            value = values.getAsBoolean(key)
        }
        return value
    }

    fun getDouble(key: String): Double {
        var value = 0.0
        if (getString(key).isNotEmpty()) {
            value = values.getAsDouble(key)
        }
        return value
    }

    fun getInt(key: String): Int {
        var value = 0
        if (getString(key).isNotEmpty()) {
            value = values.getAsInteger(key)
        }
        return value
    }

    fun getLong(key: String): Long {
        var value: Long = 0
        if (getString(key).isNotEmpty()) {
            value = values.getAsLong(key)
        }
        return value
    }

    fun toJson(): String {
        val json = JSONObject()
        for (key in values.keySet()) {
            json.put(key, values.get(key))
        }
        return json.toString()
    }

    fun isEmpty(): Boolean {
        return !hasValues
    }

    fun setDatabaseId(databaseId: String) {
        values.put("databaseId", databaseId)
    }

    fun setTimestamp(timestamp: Long) {
        values.put("timestamp", timestamp)
    }

    fun getDatabaseId(): String {
        return getString("databaseId")
    }

    fun getTimestamp(): Long {
        return getLong("timestamp")
    }

    fun getValueId(): String {
        return getString("value_id")
    }

}