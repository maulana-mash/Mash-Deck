package com.mash.deck.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mash.deck.ui.theme.*

@Composable
fun PairingDialog(
    fingerprint: String,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = SurfaceCard,
        shape = RoundedCornerShape(RadiusLg),
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Konfirmasi Pairing PC",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Periksa jendela Stream Deck Desktop di komputer Anda. Klik 'Trust' pada notifikasi verifikasi yang muncul di PC:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Modern Monospace Fingerprint Display Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(RadiusMd))
                        .background(SurfaceInput)
                        .border(1.dp, Color(0x333B82F6), RoundedCornerShape(RadiusMd))
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = fingerprint.uppercase().chunked(2).joinToString(" "),
                        color = VividBlue,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        color = SoftBlue,
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Menunggu konfirmasi di PC...",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onCancel,
                shape = RoundedCornerShape(RadiusSm)
            ) {
                Text("Batal", color = AccentRed, fontSize = 13.sp)
            }
        }
    )
}
