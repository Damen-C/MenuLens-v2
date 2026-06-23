package com.menulens.app.auth

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await

object FirebaseAuthManager {
    private val auth by lazy { FirebaseAuth.getInstance() }

    suspend fun ensureSignedInAnonymously(): FirebaseUser {
        auth.currentUser?.let { return it }
        val result = auth.signInAnonymously().await()
        return result.user ?: throw IllegalStateException("Firebase anonymous sign-in returned no user.")
    }

    suspend fun getBearerTokenOrNull(forceRefresh: Boolean = false): String? {
        val user = auth.currentUser ?: return null
        val token = user.getIdToken(forceRefresh).await().token ?: return null
        return "Bearer $token"
    }
}
