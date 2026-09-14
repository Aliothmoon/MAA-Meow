package com.aliothmoon.maameow.schedule.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.aliothmoon.maameow.MainActivity

/** 定时通知与系统闹钟入口共用，回到主界面 */
internal fun mainActivityPendingIntent(context: Context): PendingIntent {
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
