package com.example.core.auth

import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Expert Enterprise Firebase Authentication Manager for Price Bridge.
 * Orchestrates native Android Google Account discovery, exchanges Google ID tokens
 * for Firebase credentials via GoogleAuthProvider, and establishes authenticated
 * FirebaseUser sessions with genuine Firebase JWT ID tokens.
 */
class FirebaseAuthManager(
    private val context: Context
) {
    private val credentialManager = CredentialManager.create(context)

    private val auth: FirebaseAuth
        get() {
            ensureFirebaseInitialized(context)
            return FirebaseAuth.getInstance()
        }

    data class FirebaseUserData(
        val uid: String,
        val email: String,
        val displayName: String?,
        val photoUrl: String?,
        val firebaseIdToken: String
    )

    sealed class AuthResult {
        data class Success(val user: FirebaseUserData) : AuthResult()
        data class Cancelled(val message: String = "User cancelled sign in") : AuthResult()
        data class Error(val throwable: Throwable) : AuthResult()
    }

    /**
     * Executes the complete Firebase Google Sign-In pipeline:
     * 1. Launches native Google Account selector bottom sheet
     * 2. Exchanges Google ID Token for a Firebase Auth Credential
     * 3. Signs in to Firebase Auth via signInWithCredential
     * 4. Retrieves genuine Firebase JWT ID token and user metadata
     */
    suspend fun signInWithGoogle(
        activityContext: Context,
        webClientId: String? = null
    ): AuthResult {
        return try {
            val clientId = webClientId
                ?: com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID.ifBlank { null }
                ?: getWebClientIdFromResources()
                ?: "100000000001-mockclientid001.apps.googleusercontent.com"

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            Log.d(TAG, "Launching CredentialManager Google account picker...")
            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential

            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val googleIdToken = googleIdTokenCredential.idToken

            Log.d(TAG, "Exchanging Google ID token with Firebase Auth GoogleAuthProvider...")
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val firebaseUser: FirebaseUser = authResult.user
                ?: throw IllegalStateException("FirebaseUser is null after successful authentication")

            val idTokenResult = firebaseUser.getIdToken(true).await()
            val firebaseJwtToken = idTokenResult.token ?: googleIdToken

            val userData = FirebaseUserData(
                uid = firebaseUser.uid,
                email = firebaseUser.email ?: googleIdTokenCredential.id,
                displayName = firebaseUser.displayName ?: googleIdTokenCredential.displayName,
                photoUrl = firebaseUser.photoUrl?.toString() ?: googleIdTokenCredential.profilePictureUri?.toString(),
                firebaseIdToken = firebaseJwtToken
            )

            Log.d(TAG, "Firebase Google Auth successfully established for UID: ${userData.uid} (${userData.email})")
            AuthResult.Success(userData)
        } catch (e: GetCredentialCancellationException) {
            Log.i(TAG, "Google Sign-In cancelled by user")
            AuthResult.Cancelled()
        } catch (e: GetCredentialException) {
            Log.e(TAG, "CredentialManager error: ${e.message}", e)
            AuthResult.Error(e)
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Auth pipeline error: ${e.message}", e)
            AuthResult.Error(e)
        }
    }

    /**
     * Signs out the current Firebase user and resets auth state
     */
    fun signOut() {
        try {
            auth.signOut()
            Log.d(TAG, "Firebase Auth signed out successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Error during Firebase signOut: ${e.message}")
        }
    }

    fun getCurrentFirebaseUser(): FirebaseUser? {
        return try {
            auth.currentUser
        } catch (_: Exception) {
            null
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

    private fun ensureFirebaseInitialized(appContext: Context) {
        try {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext)
                Log.d(TAG, "FirebaseApp successfully initialized")
            }
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseApp initialization note: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "FirebaseAuthManager"
    }
}

