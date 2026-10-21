package com.lucabol.onelinejournal.cloud

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

class ReconnectRequired : IllegalStateException("Reconnect Google Drive in Settings to continue backing up.")

class DriveAuthorization(private val context: Context) {
    suspend fun authorize(email: String? = null): AuthorizationResult {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.file")))
        email?.let { request.setAccount(Account(it, "com.google")) }
        return Identity.getAuthorizationClient(context).authorize(request.build()).await()
    }

    suspend fun backgroundToken(email: String): String {
        val result = authorize(email)
        if (result.hasResolution()) throw ReconnectRequired()
        return result.accessToken ?: throw ReconnectRequired()
    }

    suspend fun invalidate(token: String) {
        Identity.getAuthorizationClient(context)
            .clearToken(ClearTokenRequest.builder().setToken(token).build()).await()
    }
}
