package com.ankit.vaccineavailabilitytracker.apis

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

//Cowin public APIs
//documentation at https://apisetu.gov.in/api/cowin

/**
 * public version of "GET vaccine centers by pincode"
 *
 * User of Call<T> is by deliberation, we want flexibility to call synchronously and asynchronously,
 * and the async stuff is handled by Rx.
 */
interface CowinAPI {
    @GET("api/v2/appointment/sessions/public/calendarByPin")
    fun getVaccineSpotsByPin(
        @Query("pincode") pin: String,
        @Query("date") date: String
    ): Call<VaccineLocationByPinPOJO>
}