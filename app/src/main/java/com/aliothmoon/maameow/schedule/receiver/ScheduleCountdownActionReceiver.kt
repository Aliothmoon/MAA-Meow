package com.aliothmoon.maameow.schedule.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aliothmoon.maameow.domain.launch.LaunchPipeline
import com.aliothmoon.maameow.domain.launch.LaunchUserEvent
import org.koin.core.context.GlobalContext

/** 静默启动通知上的「取消」「立即开始」 */
class ScheduleCountdownActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        val event = when (intent.action) {
            ACTION_CANCEL -> LaunchUserEvent.Cancel
            ACTION_START_NOW -> LaunchUserEvent.StartNow
            else -> return
        }
        GlobalContext.getOrNull()?.get<LaunchPipeline>()?.submit(event, requestId)
    }

    companion object {
        const val ACTION_CANCEL = "com.aliothmoon.maameow.action.SCHEDULE_COUNTDOWN_CANCEL"
        const val ACTION_START_NOW = "com.aliothmoon.maameow.action.SCHEDULE_COUNTDOWN_START_NOW"
        private const val EXTRA_REQUEST_ID = "extra_request_id"

        fun pendingIntent(context: Context, action: String, requestId: String): PendingIntent {
            val intent = Intent(context, ScheduleCountdownActionReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_REQUEST_ID, requestId)
            return PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
