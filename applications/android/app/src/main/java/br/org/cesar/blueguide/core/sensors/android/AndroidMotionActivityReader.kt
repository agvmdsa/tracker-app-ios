package br.org.cesar.blueguide.core.sensors.android

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import br.org.cesar.blueguide.core.sensors.MotionActivityReading
import br.org.cesar.blueguide.core.sensors.MotionActivitySample
import br.org.cesar.blueguide.core.sensors.MotionActivityState
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * [MotionActivityReading] over Google Play Services' `ActivityRecognitionClient` — the
 * counterpart of the iOS app's `MotionActivityReader` (`CMMotionActivityManager`). Needs
 * `ACTIVITY_RECOGNITION` (a runtime permission on API 29+) and Google Play Services; on a
 * device/emulator without either, [start] does nothing observable and [latest] stays null,
 * same "no update ever arrives" convention the iOS reader uses when its own hardware check
 * fails, rather than crashing.
 *
 * Delivery is via a dynamically registered `BroadcastReceiver` (not a manifest-declared
 * one), since this class's own lifetime already matches when updates should flow — there is
 * nothing for a manifest receiver to wake the process up for here.
 */
class AndroidMotionActivityReader @Inject constructor(@ApplicationContext private val context: Context) : MotionActivityReading {

    private val client = ActivityRecognition.getClient(context)
    private var pendingIntent: PendingIntent? = null
    private var receiver: BroadcastReceiver? = null

    override var latest: MotionActivitySample? = null
        private set

    override fun start() {
        if (pendingIntent != null) return // already started

        val localReceiver = object : BroadcastReceiver() {
            override fun onReceive(receivedContext: Context, intent: Intent) {
                val result = ActivityRecognitionResult.extractResult(intent) ?: return
                val activity = result.mostProbableActivity
                latest = MotionActivitySample(state = mapState(activity.type), confidence = activity.confidence)
            }
        }
        ContextCompat.registerReceiver(context, localReceiver, IntentFilter(ACTION), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiver = localReceiver

        // Explicit (setPackage), as Google's own docs recommend for a FLAG_MUTABLE
        // PendingIntent carrying extras: nothing outside this app can intercept it.
        val intent = Intent(ACTION).setPackage(context.packageName)
        val newPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        pendingIntent = newPendingIntent

        try {
            client.requestActivityUpdates(DETECTION_INTERVAL_MS, newPendingIntent)
        } catch (_: SecurityException) {
            // ACTIVITY_RECOGNITION not granted: leave latest null, same as the iOS reader's
            // "authorization wasn't granted" case.
        }
    }

    override fun stop() {
        try {
            pendingIntent?.let { client.removeActivityUpdates(it) }
        } catch (_: SecurityException) {
            // Permission could have been revoked after requestActivityUpdates() succeeded
            // (Settings > Permissions, mid-session) — nothing to clean up on the Play
            // Services side in that case either.
        }
        pendingIntent = null
        receiver?.let { context.unregisterReceiver(it) }
        receiver = null
        latest = null
    }

    private fun mapState(type: Int): MotionActivityState = when (type) {
        DetectedActivity.IN_VEHICLE -> MotionActivityState.AUTOMOTIVE
        DetectedActivity.ON_BICYCLE -> MotionActivityState.CYCLING
        DetectedActivity.RUNNING -> MotionActivityState.RUNNING
        DetectedActivity.WALKING, DetectedActivity.ON_FOOT -> MotionActivityState.WALKING
        DetectedActivity.STILL -> MotionActivityState.STATIONARY
        else -> MotionActivityState.UNKNOWN
    }

    private companion object {
        const val ACTION = "br.org.cesar.blueguide.ACTIVITY_RECOGNITION"
        const val REQUEST_CODE = 4821
        const val DETECTION_INTERVAL_MS = 5000L
    }
}
