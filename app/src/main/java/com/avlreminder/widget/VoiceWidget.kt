package com.avlreminder.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.avlreminder.ui.theme.DarkColors
import com.avlreminder.ui.theme.LightColors
import com.avlreminder.voice.VoiceActivity

class VoiceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = VoiceWidget()
}

/** One button: a tap starts the quick reminder by voice (Anf. 7.35). */
class VoiceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Box(
                GlanceModifier.fillMaxSize().cornerRadius(20.dp)
                    .background(ColorProvider(day = LightColors.accentFill, night = DarkColors.accentFill))
                    .clickable(actionStartActivity(VoiceActivity.intent(LocalContext.current))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Sprach-\nReminder",
                    style = TextStyle(color = ColorProvider(day = Color.White, night = Color.White), fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                )
            }
        }
    }
}
