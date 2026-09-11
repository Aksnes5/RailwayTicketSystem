package com.railway.ticketsystem.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.railway.ticketsystem.model.Passenger

class PassengerRepository(context: Context) {
    
    private val prefs: SharedPreferences = SecurePreferences.open(context, "secure_passenger_data", "passenger_data")
    private val gson = Gson()
    
    fun addPassenger(passenger: Passenger) {
        val passengers = getPassengersByUserId(passenger.userId).toMutableList()
        passengers.add(passenger)
        val json = gson.toJson(passengers)
        prefs.edit().putString("passengers_${passenger.userId}", json).commit()
    }
    
    fun getPassengersByUserId(userId: String): List<Passenger> {
        val json = prefs.getString("passengers_$userId", null)
        return if (json != null) {
            val type = object : TypeToken<List<Passenger>>() {}.type
            runCatching {
                gson.fromJson<List<Passenger>>(json, type) ?: emptyList()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }
    
    fun deletePassenger(passengerId: String, userId: String) {
        val passengers = getPassengersByUserId(userId).toMutableList()
        passengers.removeAll { it.id == passengerId }
        val json = gson.toJson(passengers)
        prefs.edit().putString("passengers_$userId", json).commit()
    }
    
    fun updatePassenger(passenger: Passenger) {
        val passengers = getPassengersByUserId(passenger.userId).toMutableList()
        val index = passengers.indexOfFirst { it.id == passenger.id }
        if (index != -1) {
            passengers[index] = passenger
            val json = gson.toJson(passengers)
            prefs.edit().putString("passengers_${passenger.userId}", json).commit()
        }
    }
}







