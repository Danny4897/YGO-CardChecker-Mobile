package com.ygochecker.android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ygochecker.android.MainActivity
import com.ygochecker.android.R
import com.ygochecker.core.designsystem.R as DesignR
import com.ygochecker.core.domain.RandomPlayableCard
import com.ygochecker.core.model.GameFormat
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Home-screen shortcut: tap to reveal a random HAT-playable card name and open Search.
 */
class RandomHatWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> update(context, manager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RandomHatWidgetProvider::class.java))
            ids.forEach { update(context, manager, it) }
        }
    }

    private fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val entry = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
        val card = runBlocking {
            runCatching { entry.randomPlayable().invoke(GameFormat.HAT).first() }.getOrNull()
        }
        val views = RemoteViews(context.packageName, R.layout.widget_random_hat)
        views.setTextViewText(
            R.id.widget_title,
            context.getString(DesignR.string.widget_random_hat),
        )
        views.setTextViewText(
            R.id.widget_card_name,
            card?.name ?: "—",
        )
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_QUERY, card?.name.orEmpty())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val refresh = PendingIntent.getBroadcast(
            context,
            id + 1000,
            Intent(context, RandomHatWidgetProvider::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        views.setOnClickPendingIntent(R.id.widget_refresh, refresh)
        manager.updateAppWidget(id, views)
    }

    companion object {
        const val ACTION_REFRESH = "com.ygochecker.android.widget.REFRESH_HAT"
        const val EXTRA_QUERY = "widget_query"
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun randomPlayable(): RandomPlayableCard
}
