package com.ankit.vaccineavailabilitytracker.apis

import com.google.gson.annotations.Expose

import com.google.gson.annotations.SerializedName

/**
 * JSON pojo for API call, filled by GSON in retrofit call.
 */
class VaccineLocationByPinPOJO {
    @SerializedName("centers")
    @Expose
    var centers: List<Center>? = null
}

class Center {

    @SerializedName("center_id")
    @Expose
    var centerId = 0

    @SerializedName("name")
    @Expose
    var name: String? = null

    @SerializedName("state_name")
    @Expose
    var stateName: String? = null

    @SerializedName("district_name")
    @Expose
    var districtName: String? = null

    @SerializedName("block_name")
    @Expose
    var blockName: String? = null

    @SerializedName("pincode")
    @Expose
    var pincode = 0

    @SerializedName("lat")
    @Expose
    var lat = 0

    @SerializedName("long")
    @Expose
    var _long = 0

    @SerializedName("from")
    @Expose
    var from: String? = null

    @SerializedName("to")
    @Expose
    var to: String? = null

    @SerializedName("fee_type")
    @Expose
    var feeType: String? = null

    @SerializedName("sessions")
    @Expose
    var sessions: List<Session>? = null
}

class Session {
    @SerializedName("session_id")
    @Expose
    var sessionId: String? = null

    @SerializedName("date")
    @Expose
    var date: String? = null

    @SerializedName("available_capacity")
    @Expose
    var availableCapacity = 0

    @SerializedName("min_age_limit")
    @Expose
    var minAgeLimit = 0

    @SerializedName("vaccine")
    @Expose
    var vaccine: String? = null

    @SerializedName("slots")
    @Expose
    var slots: List<String>? = null
}