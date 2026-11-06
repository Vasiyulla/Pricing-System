package com.example.core.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Enterprise Google Authentication Manager leveraging native Android 14+ Credential Manager.
 * Directly launches the native Android Google Account selector bottom sheet and retrieves
 * the genuine signed Google ID Token without third-party wrapper overhead.
 */
class GoogleAuthManager(
    private val context: Context
) {
    private val credentialManager = CredentialManager.create(context)

    data class GoogleUserData(
        val email: String,
        val displayName: String?,
        val photoUrl: String?,
        val idToken: String
    )

    sealed class AuthResult {
        data class Success(val data: GoogleUserData) : AuthResult()
        data class Cancelled(val message: String = "User cancelled sign in") : AuthResult()
        data class Error(val throwable: Throwable) : AuthResult()
    }

    /**
     * Launches the native Android Google Account selector bottom sheet and returns
     * genuine Google credentials.
     */
    suspend fun signInWithGoogle(
        activityContext: Context,
        webClientId: String? = null
    ): AuthResult {
        return try {
            val clientId = webClientId
                ?: getWebClientIdFromResources()
                ?: "default_web_client_id"

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            Log.d(TAG, "Launching Android CredentialManager account selector...")
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val userData = GoogleUserData(
                email = googleIdTokenCredential.id,
                displayName = googleIdTokenCredential.displayName,
                photoUrl = googleIdTokenCredential.profilePictureUri?.toString(),
                idToken = googleIdTokenCredential.idToken
            )

            Log.d(TAG, "Genuine Google Sign-In succeeded for ${userData.email}")
            AuthResult.Success(userData)
        } catch (e: GetCredentialCancellationException) {
            Log.i(TAG, "Google Sign-In cancelled by user")
            AuthResult.Cancelled()
        } catch (e: GetCredentialException) {
            Log.e(TAG, "CredentialManager error: ${e.message}", e)
            AuthResult.Error(e)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In unexpected error: ${e.message}", e)
            AuthResult.Error(e)
        }
    }

    private fun getWebClientIdFromResources(): String? {
        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) context.getString(resId) else null
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "GoogleAuthManager"
    }
}
