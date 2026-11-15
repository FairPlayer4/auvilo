package com.avlreminder.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.avlreminder.ui.theme.Avl

/**
 * The prominent disclosure Google Play asks for before Android's location dialog (Anf. 7.28 PS13,
 * playstore.md P5): what the app does with the location in the background, in words of its own,
 * before the system asks. "Nicht jetzt" leaves everything as without the permission.
 */
object LocationDisclosure {
    const val TITLE = "Standort im Hintergrund"
    const val TEXT = "AuViLo-Reminder nutzt deinen Standort und das verbundene WLAN, um zu erkennen, wann du an " +
        "einem gespeicherten Ort ankommst – auch wenn die App geschlossen ist oder nicht benutzt wird. Nur dann " +
        "klingeln Reminder mit „Bei Ankunft“ beim Ankommen statt erst zur spätesten Uhrzeit.\n\n" +
        "Standort und WLAN bleiben auf dem Handy und werden nicht weitergegeben.\n\n" +
        "Im nächsten Schritt fragt Android nach dem Standort; wähle dort „Immer erlauben“."
    const val CONTINUE = "Weiter"
    const val LATER = "Nicht jetzt"
}

@Composable
fun LocationDisclosureDialog(onContinue: () -> Unit, onLater: () -> Unit) {
    val c = Avl.colors
    AlertDialog(
        onDismissRequest = onLater,
        modifier = Modifier.testTag("locationDisclosure"),
        title = { Text(LocationDisclosure.TITLE) },
        text = { Text(LocationDisclosure.TEXT) },
        confirmButton = {
            TextButton(onClick = onContinue) { Text(LocationDisclosure.CONTINUE, color = c.accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onLater) { Text(LocationDisclosure.LATER, color = c.muted) } },
    )
}
