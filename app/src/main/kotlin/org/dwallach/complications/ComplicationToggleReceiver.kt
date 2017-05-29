package org.dwallach.complications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.support.wearable.complications.ProviderUpdateRequester

/**
 * Simple [BroadcastReceiver] subclass for asynchronously incrementing an integer for any
 * complication id triggered via TapAction on complication. Also, provides static method to create a
 * [PendingIntent] that triggers this receiver.
 */
class ComplicationToggleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extras = intent.extras
        val provider = extras.getParcelable<ComponentName>(EXTRA_PROVIDER_COMPONENT)
        val complicationId = extras.getInt(EXTRA_COMPLICATION_ID)

        val preferenceKey = getPreferenceKey(provider, complicationId)
        val sharedPreferences = context.getSharedPreferences(COMPLICATION_PROVIDER_PREFERENCES_FILE_KEY, 0)

        var value = sharedPreferences.getInt(preferenceKey, 0)

        // Updates data for complication.
        value = (value + 1) % MAX_NUMBER

        val editor = sharedPreferences.edit()
        editor.putInt(preferenceKey, value)
        editor.apply()

        // Request an update for the complication that has just been toggled.
        val requester = ProviderUpdateRequester(context, provider!!)
        requester.requestUpdate(complicationId)
    }

    companion object {
        private val EXTRA_PROVIDER_COMPONENT = "com.example.android.wearable.watchface.provider.action.PROVIDER_COMPONENT"
        private val EXTRA_COMPLICATION_ID = "com.example.android.wearable.watchface.provider.action.COMPLICATION_ID"

        internal val MAX_NUMBER = 20
        internal val COMPLICATION_PROVIDER_PREFERENCES_FILE_KEY = "com.example.android.wearable.watchface.COMPLICATION_PROVIDER_PREFERENCES_FILE_KEY"

        /**
         * Returns a pending intent, suitable for use as a tap intent, that causes a complication to be
         * toggled and updated.
         */
        internal fun getToggleIntent(
                context: Context, provider: ComponentName, complicationId: Int): PendingIntent {
            val intent = Intent(context, ComplicationToggleReceiver::class.java)
            intent.putExtra(EXTRA_PROVIDER_COMPONENT, provider)
            intent.putExtra(EXTRA_COMPLICATION_ID, complicationId)

            // Pass complicationId as the requestCode to ensure that different complications get
            // different intents.
            return PendingIntent.getBroadcast(
                    context, complicationId, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        /**
         * Returns the key for the shared preference used to hold the current state of a given
         * complication.
         */
        internal fun getPreferenceKey(provider: ComponentName, complicationId: Int): String {
            return provider.className + complicationId
        }
    }
}