package com.zariapps.qrgenerator

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// ── Colors ───────────────────────────────────────────────

val BgColor      = Color(0xFF0A0F0D)
val SurfaceColor = Color(0xFF111A16)
val RaisedColor  = Color(0xFF1A2720)
val BorderColor  = Color(0xFF2A3D35)
val AccentColor  = Color(0xFF00D9A3)
val AccentDark   = Color(0xFF00916B)
val TextColor    = Color(0xFFE0F5EE)
val MutedColor   = Color(0xFF6A8A7A)
val FieldBg      = Color(0xFF162019)

// ── QR types ─────────────────────────────────────────────

enum class QrType(val label: String, val icon: String, val hint: String) {
    URL("URL",    "🔗", "https://example.com"),
    TEXT("Text",  "📝", "Enter any text..."),
    EMAIL("Email","✉",  "name@example.com"),
    PHONE("Phone","📞", "+1 234 567 8900"),
    WIFI("WiFi",  "📶", "Network name"),
}

fun buildQrContent(
    type: QrType,
    url: String, text: String, email: String,
    phone: String, ssid: String, wifiPass: String, wifiSec: String,
): String = when (type) {
    QrType.URL   -> url.trim().let { if (it.startsWith("http")) it else "https://$it" }
    QrType.TEXT  -> text.trim()
    QrType.EMAIL -> "mailto:${email.trim()}"
    QrType.PHONE -> "tel:${phone.trim()}"
    QrType.WIFI  -> "WIFI:T:$wifiSec;S:$ssid;P:$wifiPass;;"
}

// ── QR generation ─────────────────────────────────────────

fun generateQrBitmap(content: String, size: Int = 768): Bitmap? {
    if (content.isBlank()) return null
    return try {
        val hints = mapOf(EncodeHintType.MARGIN to 2)
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bmp.setPixel(x, y,
                    if (matrix[x, y]) android.graphics.Color.BLACK
                    else android.graphics.Color.WHITE
                )
            }
        }
        bmp
    } catch (_: Exception) { null }
}

// ── Save / share ──────────────────────────────────────────

fun saveToGallery(context: Context, bitmap: Bitmap): Boolean {
    return try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "QR_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/QR Codes")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(it, values, null, null)
            }
        }
        true
    } catch (_: Exception) { false }
}

fun shareQrBitmap(context: Context, bitmap: Bitmap) {
    val cacheDir = File(context.cacheDir, "qr_codes").also { it.mkdirs() }
    val file = File(cacheDir, "qr_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.startActivity(Intent.createChooser(
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Share QR Code"
    ))
}

// ── Activity ─────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { QrScreen() } }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background   = BgColor,
            surface      = SurfaceColor,
            primary      = AccentColor,
            onPrimary    = Color(0xFF0A0F0D),
            onBackground = TextColor,
            onSurface    = TextColor,
        ),
        content = content,
    )
}

// ── Screen ───────────────────────────────────────────────

@Composable
fun QrScreen() {
    var selectedType by remember { mutableStateOf(QrType.URL) }

    // Per-type inputs
    var url      by remember { mutableStateOf("") }
    var text     by remember { mutableStateOf("") }
    var email    by remember { mutableStateOf("") }
    var phone    by remember { mutableStateOf("") }
    var ssid     by remember { mutableStateOf("") }
    var wifiPass by remember { mutableStateOf("") }
    var wifiSec  by remember { mutableStateOf("WPA") }

    var qrBitmap    by remember { mutableStateOf<Bitmap?>(null) }
    var showSaved   by remember { mutableStateOf(false) }
    var showError   by remember { mutableStateOf(false) }

    val scope   = rememberCoroutineScope()
    val haptic  = LocalHapticFeedback.current
    val context = LocalContext.current

    // Build content string from current type + inputs
    val content = buildQrContent(selectedType, url, text, email, phone, ssid, wifiPass, wifiSec)

    // Auto-generate with debounce whenever content changes
    LaunchedEffect(content) {
        delay(350)
        if (content.isNotBlank()) {
            qrBitmap = withContext(Dispatchers.IO) { generateQrBitmap(content) }
        } else {
            qrBitmap = null
        }
    }

    fun doSave() {
        val bmp = qrBitmap ?: return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val ok = withContext(Dispatchers.IO) { saveToGallery(context, bmp) }
            if (ok) {
                showSaved = true
                delay(2000)
                showSaved = false
            } else {
                showError = true
                delay(2000)
                showError = false
            }
        }
    }

    fun doShare() {
        val bmp = qrBitmap ?: return
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { withContext(Dispatchers.IO) { shareQrBitmap(context, bmp) } }
    }

    Scaffold(
        containerColor = BgColor,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 32.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // ── Header ──────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("QR Code", fontSize = 36.sp, fontWeight = FontWeight.Black, color = AccentColor, letterSpacing = 1.sp)
                Text("Generator", fontSize = 36.sp, fontWeight = FontWeight.Black, color = TextColor, letterSpacing = 1.sp)
                Spacer(Modifier.height(2.dp))
                Text("Create  ·  Save  ·  Share", fontSize = 12.sp, color = MutedColor, letterSpacing = 2.sp)
            }

            // ── Type selector ────────────────────────────
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QrType.entries.forEach { type ->
                    val active = selectedType == type
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) AccentColor.copy(alpha = 0.15f) else RaisedColor)
                            .border(1.5.dp, if (active) AccentColor else BorderColor, RoundedCornerShape(12.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedType = type
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(type.icon, fontSize = 14.sp)
                        Text(
                            text = type.label,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) AccentColor else MutedColor,
                        )
                    }
                }
            }

            // ── Input fields ─────────────────────────────
            AnimatedContent(
                targetState = selectedType,
                transitionSpec = { fadeIn(initialAlpha = 0f) togetherWith fadeOut() },
                label = "input",
            ) { type ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceColor)
                        .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (type) {
                        QrType.URL -> QrTextField(value = url, onChange = { url = it },
                            label = "Website URL", hint = "https://example.com",
                            keyboard = KeyboardType.Uri)

                        QrType.TEXT -> QrTextField(value = text, onChange = { text = it },
                            label = "Text", hint = "Enter any text or message...",
                            singleLine = false)

                        QrType.EMAIL -> QrTextField(value = email, onChange = { email = it },
                            label = "Email Address", hint = "name@example.com",
                            keyboard = KeyboardType.Email)

                        QrType.PHONE -> QrTextField(value = phone, onChange = { phone = it },
                            label = "Phone Number", hint = "+1 234 567 8900",
                            keyboard = KeyboardType.Phone)

                        QrType.WIFI -> {
                            QrTextField(value = ssid, onChange = { ssid = it },
                                label = "Network Name (SSID)", hint = "My WiFi Network")
                            QrTextField(value = wifiPass, onChange = { wifiPass = it },
                                label = "Password", hint = "••••••••",
                                isPassword = true)
                            // Security type selector
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Security", fontSize = 12.sp, color = MutedColor, letterSpacing = 1.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("WPA", "WEP", "None").forEach { sec ->
                                        val sel = wifiSec == sec
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (sel) AccentColor.copy(0.15f) else RaisedColor)
                                                .border(1.dp, if (sel) AccentColor else BorderColor, RoundedCornerShape(8.dp))
                                                .clickable { wifiSec = sec }
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                        ) {
                                            Text(sec, fontSize = 13.sp,
                                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (sel) AccentColor else MutedColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── QR preview ───────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(SurfaceColor)
                    .border(1.dp, BorderColor, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = qrBitmap,
                    transitionSpec = { (scaleIn(initialScale = 0.85f) + fadeIn()) togetherWith (scaleOut(targetScale = 0.85f) + fadeOut()) },
                    label = "qr",
                ) { bmp ->
                    if (bmp != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            // White padding around QR for scanners
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White)
                                    .padding(12.dp),
                            ) {
                                Image(
                                    painter = BitmapPainter(bmp.asImageBitmap()),
                                    contentDescription = "QR Code",
                                    modifier = Modifier.size(220.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                            Text(
                                text = content.take(50) + if (content.length > 50) "…" else "",
                                fontSize = 11.sp,
                                color = MutedColor,
                                letterSpacing = 0.5.sp,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text("⬜", fontSize = 48.sp)
                            Text("QR preview will appear here", fontSize = 13.sp, color = MutedColor)
                            Text("Fill in the fields above", fontSize = 11.sp, color = MutedColor.copy(0.6f))
                        }
                    }
                }
            }

            // ── Actions ──────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Save button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (qrBitmap != null)
                                Brush.linearGradient(listOf(AccentDark, AccentColor))
                            else
                                Brush.linearGradient(listOf(RaisedColor, RaisedColor))
                        )
                        .clickable(enabled = qrBitmap != null) { doSave() }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(if (showSaved) "✓" else "↓", fontSize = 16.sp,
                            color = if (qrBitmap != null) Color(0xFF0A0F0D) else MutedColor)
                        Text(
                            text = if (showSaved) "Saved!" else "Save",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (qrBitmap != null) Color(0xFF0A0F0D) else MutedColor,
                        )
                    }
                }

                // Share button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (qrBitmap != null) RaisedColor else RaisedColor.copy(0.5f))
                        .border(1.dp, if (qrBitmap != null) AccentColor else BorderColor, RoundedCornerShape(14.dp))
                        .clickable(enabled = qrBitmap != null) { doShare() }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("↗", fontSize = 16.sp, color = if (qrBitmap != null) AccentColor else MutedColor)
                        Text(
                            text = "Share",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (qrBitmap != null) AccentColor else MutedColor,
                        )
                    }
                }
            }

            // Error snackbar
            AnimatedVisibility(visible = showError) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF3A1A1A))
                        .border(1.dp, Color(0xFFEF4444).copy(0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                ) {
                    Text("Could not save. Check storage permission.", fontSize = 13.sp, color = Color(0xFFEF4444))
                }
            }
        }
    }
}

// ── Shared text field ─────────────────────────────────────

@Composable
fun QrTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    hint: String,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    isPassword: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 12.sp, color = MutedColor, letterSpacing = 1.sp)
        TextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            placeholder = { Text(hint, color = MutedColor.copy(0.5f), fontSize = 14.sp) },
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            colors = TextFieldDefaults.colors(
                focusedContainerColor   = FieldBg,
                unfocusedContainerColor = FieldBg,
                focusedIndicatorColor   = AccentColor,
                unfocusedIndicatorColor = BorderColor,
                focusedTextColor        = TextColor,
                unfocusedTextColor      = TextColor,
                cursorColor             = AccentColor,
            ),
            shape = RoundedCornerShape(10.dp),
        )
    }
}
