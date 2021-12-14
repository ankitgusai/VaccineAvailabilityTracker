package com.ankit.vaccineavailabilitytracker

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import java.lang.StringBuilder

class SpManager(context: Context) {
    private val sharedPreferenceName = "VaccineSlotApp"
    private var sharedPreference: SharedPreferences =
        context.getSharedPreferences(sharedPreferenceName, Activity.MODE_PRIVATE)

    fun clearAll() = sharedPreference.edit().clear().apply()

    var pinCode: String
        set(value) = sharedPreference.edit().putString("pinCode", value).apply()
        get() = sharedPreference.getString("pinCode", "") ?: ""

    var startTimeStamp: Long
        set(value) = sharedPreference.edit().putLong("startTimeStamp", value).apply()
        get() = sharedPreference.getLong("startTimeStamp", 0)

    var totalQueries: Int
        set(value) = sharedPreference.edit().putInt("totalQueries", value).apply()
        get() = sharedPreference.getInt("totalQueries", 0)

    var totalAvailableLocations: Int
        set(value) = sharedPreference.edit().putInt("totalAvailableLocations", value).apply()
        get() = sharedPreference.getInt("totalAvailableLocations", 0)

    var is18PlusSlotAvailable: Boolean
        set(value) = sharedPreference.edit().putBoolean("is18PlusSlotAvailable", value).apply()
        get() = sharedPreference.getBoolean("is18PlusSlotAvailable", false)

    var consolidatedString: String
        set(value) = sharedPreference.edit().putString("consolidatedString", value).apply()
        get() = sharedPreference.getString("consolidatedString", "") ?: ""

    var lastChecked: Long
        set(value) = sharedPreference.edit().putLong("lastChecked", value).apply()
        get() = sharedPreference.getLong("lastChecked", 0)


}