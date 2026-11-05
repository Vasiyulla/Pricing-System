package com.example.core.session

import android.content.Context
import android.content.SharedPreferences
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private var prefs: SharedPreferences? = null

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _userProfile = MutableStateFlow(
        UserProfile(
            id = "usr_scout_1",
            displayName = "Alex Turner",
            email = "alex.turner@gmail.com",
            phoneNumber = "+44 7700 900077",
            karmaPoints = 350,
            pricesReported = 16,
            pricesVerified = 14,
            accuracyPercent = 98,
            memberSince = "Nov 2026",
            reputationLevel = "London Scout",
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
        token: String = ""
    ) {
        _isLoggedIn.value = loggedIn
        prefs?.edit()
            ?.putBoolean(KEY_IS_LOGGED_IN, loggedIn)
            ?.putString(KEY_AUTH_MODE, mode)
            ?.putString(KEY_USER_EMAIL, email)
            ?.putString(KEY_USER_NAME, name)
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
}

