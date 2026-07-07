package com.apphud.demo

import android.util.Log
import com.apphud.sdk.Apphud
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Receives FCM push tokens and messages and forwards them to Apphud so that Apphud Rules
 * (surveys, feedback, billing issue and paywall screens) can be delivered via push.
 */
class DemoFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "onNewToken: $token")
        Apphud.submitPushNotificationsToken(token) { success ->
            Log.d(TAG, "submitPushNotificationsToken success=$success")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "onMessageReceived data=${message.data}")

        val handledByApphud = Apphud.handlePushNotification(message.data)
        if (!handledByApphud) {
            Log.d(TAG, "Push was not an Apphud rule, handle it in your own app logic")
        }
    }

    private companion object {
        const val TAG = "ApphudLogsDemo"
    }
}
