package com.example.core.session

import android.content.Context
import android.content.SharedPreferences
import com.example.core.model.SavedAddress
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Enterprise Session Manager persisting authentication state,
 * user profile, and auth tokens across app restarts.
 */
object AppSessionManager {
    private const val PREFS_NAME = "price_bridge_session"
    private const val KEY_IS_LOGGED_IN = "key_is_logged_in"
    private const val KEY_AUTH_MODE = "key_auth_mode"
    private const val KEY_USER_EMAIL = "key_user_email"
    private const val KEY_USER_NAME = "key_user_name"
    private const val KEY_USER_AVATAR = "key_user_avatar"
    private const val KEY_AUTH_TOKEN = "key_auth_token"
    private const val KEY_KARMA_POINTS = "key_karma_points"
    private const val KEY_SAVED_ADDRESSES = "key_saved_addresses"

    private var prefs: SharedPreferences? = null

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userProfile = MutableStateFlow(
        UserProfile(
            id = "usr_scout_1",
            displayName = "Alex Turner",
            email = "alex.turner@gmail.com",
            phoneNumber = null,
            karmaPoints = 350,
            pricesReported = 16,
            pricesVerified = 14,
            accuracyPercent = 98,
            memberSince = "Nov 2026",
            reputationLevel = "Community Scout",
            role = UserRole.ActiveContributor
        )
    )
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedLoggedIn = prefs?.getBoolean(KEY_IS_LOGGED_IN, false) ?: false
            _isLoggedIn.value = savedLoggedIn

            val savedName = prefs?.getString(KEY_USER_NAME, "Alex Turner") ?: "Alex Turner"
            val savedEmail = prefs?.getString(KEY_USER_EMAIL, "alex.turner@gmail.com") ?: "alex.turner@gmail.com"
            val savedKarma = prefs?.getInt(KEY_KARMA_POINTS, 350) ?: 350

            _userProfile.value = _userProfile.value.copy(
                displayName = savedName,
                email = savedEmail,
                karmaPoints = savedKarma
            )
        }
    }

    fun setLoggedIn(
        loggedIn: Boolean,
        mode: String = "GUEST",
        email: String = "alex.turner@gmail.com",
        name: String = "Alex Turner",
        avatarUrl: String? = null,
        token: String = ""
    ) {
        _isLoggedIn.value = loggedIn
        prefs?.edit()
            ?.putBoolean(KEY_IS_LOGGED_IN, loggedIn)
            ?.putString(KEY_AUTH_MODE, mode)
            ?.putString(KEY_USER_EMAIL, email)
            ?.putString(KEY_USER_NAME, name)
            ?.putString(KEY_USER_AVATAR, avatarUrl ?: "")
            ?.putString(KEY_AUTH_TOKEN, token)
            ?.apply()

        _userProfile.value = _userProfile.value.copy(
            displayName = name,
            email = email
        )
    }

    fun logout() {
        _isLoggedIn.value = false
        prefs?.edit()?.clear()?.apply()
    }

    /**
     * Returns the active bearer token for authenticated API requests.
     */
    fun getAuthToken(): String = prefs?.getString(KEY_AUTH_TOKEN, "") ?: ""

    /**
     * Retrieves saved delivery addresses (Home, Work, Other) persisted locally.
     */
    fun getSavedAddresses(): List<SavedAddress> {
        val raw = prefs?.getString(KEY_SAVED_ADDRESSES, null)
        if (raw.isNullOrBlank()) {
            return listOf(
                SavedAddress(
                    label = "Home",
                    addressLine = "Flat 402, Green Glen Layout",
                    locality = "Bellandur, Bengaluru",
                    latitude = 12.9279,
                    longitude = 77.6711
                ),
                SavedAddress(
                    label = "Work",
                    addressLine = "Ecospace Business Park, Outer Ring Rd",
                    locality = "Bellandur, Bengaluru",
                    latitude = 12.9260,
                    longitude = 77.6833
                )
            )
        }
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<SavedAddress>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    SavedAddress(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        label = obj.optString("label", "Other"),
                        addressLine = obj.optString("addressLine", ""),
                        locality = obj.optString("locality", ""),
                        latitude = obj.optDouble("latitude", 0.0),
                        longitude = obj.optDouble("longitude", 0.0)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Persists or updates a saved address.
     */
    fun saveAddress(address: SavedAddress) {
        val current = getSavedAddresses().toMutableList()
        val index = current.indexOfFirst { it.id == address.id || (it.label == address.label && it.label != "Other") }
        if (index >= 0) {
            current[index] = address
        } else {
            current.add(0, address)
        }
        val array = JSONArray()
        for (item in current) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("label", item.label)
            obj.put("addressLine", item.addressLine)
            obj.put("locality", item.locality)
            obj.put("latitude", item.latitude)
            obj.put("longitude", item.longitude)
            array.put(obj)
        }
        prefs?.edit()?.putString(KEY_SAVED_ADDRESSES, array.toString())?.apply()
    }

    /**
     * Removes a saved address by ID.
     */
    fun removeAddress(id: String) {
        val current = getSavedAddresses().filter { it.id != id }
        val array = JSONArray()
        for (item in current) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("label", item.label)
            obj.put("addressLine", item.addressLine)
            obj.put("locality", item.locality)
            obj.put("latitude", item.latitude)
            obj.put("longitude", item.longitude)
            array.put(obj)
        }
        prefs?.edit()?.putString(KEY_SAVED_ADDRESSES, array.toString())?.apply()
    }
}

