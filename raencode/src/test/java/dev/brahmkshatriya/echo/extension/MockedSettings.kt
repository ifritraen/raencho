package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.settings.Settings

class MockedSettings : Settings {
    private val booleans = mutableMapOf<String, Boolean?>()
    private val ints = mutableMapOf<String, Int?>()
    private val strings = mutableMapOf<String, String?>()
    private val stringSets = mutableMapOf<String, Set<String>?>()

    override fun getBoolean(key: String): Boolean? = booleans[key]
    override fun getInt(key: String): Int? = ints[key]
    override fun getString(key: String): String? = strings[key]
    override fun getStringSet(key: String): Set<String>? = stringSets[key]

    override fun putBoolean(key: String, value: Boolean?) { booleans[key] = value }
    override fun putInt(key: String, value: Int?) { ints[key] = value }
    override fun putString(key: String, value: String?) { strings[key] = value }
    override fun putStringSet(key: String, value: Set<String>?) { stringSets[key] = value }
}