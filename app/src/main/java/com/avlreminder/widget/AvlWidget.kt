package com.avlreminder.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.avlreminder.core.rules.QuickReminders
import com.avlreminder.core.usecases.Entry
import com.avlreminder.core.usecases.Overview
import com.avlreminder.graph
import com.avlreminder.ui.Format
import com.avlreminder.ui.MainActivity
import com.avlreminder.ui.theme.DarkColors
import com.avlreminder.ui.theme.LightColors

class AvlWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AvlWidget()
}

/** Home-screen widget: next reminder, "+ Reminder" and one-tap meeting reminders. Follows the system mode. */
class AvlWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = context.graph
        val zone = graph.zone
        val now = graph.now()
        val next = Overview.next(graph.today(now))
        val label = next?.let { Format.moment(it.occurrence.currentAt, zone, now.atZone(zone).toLocalDate()) }
        provideContent { Content(next, label) }
    }

    @Composable
    private fun Content(next: Entry?, time: String?) {
        val text = color(LightColors.text, DarkColors.text)
        val muted = color(LightColors.muted, DarkColors.muted)
        Column(
            GlanceModifier.fillMaxSize().cornerRadius(20.dp)
                .background(color(LightColors.surface, DarkColors.surface)).padding(14.dp),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(time ?: "–", style = TextStyle(color = text, fontSize = 20.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.width(12.dp))
                Column {
                    Text("Als Nächstes", style = TextStyle(color = muted, fontSize = 12.sp))
                    Text(next?.reminder?.title ?: "nichts geplant", style = TextStyle(color = text, fontSize = 15.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
            }
            Spacer(GlanceModifier.height(10.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                WidgetButton("+ Reminder", primary = true, GlanceModifier.defaultWeight().clickable(actionStartActivity(MainActivity.newReminder(androidx.glance.LocalContext.current))))
                Spacer(GlanceModifier.width(6.dp))
                WidgetButton("Meeting +30", primary = false, GlanceModifier.defaultWeight().clickable(meeting(30)))
                Spacer(GlanceModifier.width(6.dp))
                WidgetButton("Meeting +60", primary = false, GlanceModifier.defaultWeight().clickable(meeting(60)))
            }
        }
    }

    @Composable
    private fun WidgetButton(label: String, primary: Boolean, modifier: GlanceModifier) {
        val background = if (primary) color(LightColors.accentFill, DarkColors.accentFill) else color(LightColors.accentSoft, DarkColors.accentSoft)
        val foreground = if (primary) color(Color.White, Color.White) else color(LightColors.onAccentSoft, DarkColors.onAccentSoft)
        Box(modifier.height(44.dp).cornerRadius(12.dp).background(background), contentAlignment = Alignment.Center) {
            Text(label, style = TextStyle(color = foreground, fontSize = 13.sp, fontWeight = FontWeight.Medium), maxLines = 1)
        }
    }

    private fun color(day: Color, night: Color): ColorProvider = ColorProvider(day = day, night = night)

    private fun meeting(minutes: Long) = actionRunCallback<MeetingAction>(actionParametersOf(MeetingAction.MINUTES to minutes))
}

/** Creates "Meeting in N minutes" without opening the app. */
class MeetingAction : ActionCallback {
    companion object {
        val MINUTES = ActionParameters.Key<Long>("minutes")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val minutes = parameters[MINUTES] ?: return
        val graph = context.graph
        graph.withEngine { it.save(QuickReminders.meeting(minutes, graph.now(), graph.zone)) }
    }
}
