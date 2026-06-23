package com.menulens.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CenterFocusWeak
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.menulens.app.R
import com.menulens.app.ui.theme.Hairline
import java.io.ByteArrayOutputStream

@Composable
fun ScanScreen(onMenuImageReady: (ByteArray) -> Unit) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            preview = bitmap
            message = null
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                ?: error("Could not decode image")
        }.onSuccess {
            preview = it
            message = null
        }.onFailure {
            message = "Could not read that image. Please choose another photo."
        }
    }

    AppScreen(
        title = "Read the menu,\nnot the guesswork.",
        subtitle = "Snap or upload a menu to understand it instantly.",
        showBrandAsBlock = true,
        topPadding = 16.dp
    ) {
        if (preview != null) {
            Image(
                bitmap = preview!!.asImageBitmap(),
                contentDescription = "Selected menu",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(9.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(9.dp))
            )
        } else {
            Image(
                painter = painterResource(R.drawable.restaurant_editorial),
                contentDescription = "Japanese restaurant",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(9.dp))
                    .border(1.dp, Hairline, RoundedCornerShape(9.dp))
            )
        }

        if (preview == null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { camera.launch(null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("take_menu_photo"),
                    shape = RoundedCornerShape(7.dp)
                ) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(19.dp))
                    Text("Take photo", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = { gallery.launch("image/*") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("upload_menu_photo"),
                    shape = RoundedCornerShape(7.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Outlined.Upload, contentDescription = null, modifier = Modifier.size(19.dp))
                    Text("Upload", modifier = Modifier.padding(start = 8.dp))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onMenuImageReady(preview!!.toJpegBytes()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("scan_menu_primary"),
                    shape = RoundedCornerShape(7.dp)
                ) {
                    Icon(Icons.Outlined.CenterFocusWeak, contentDescription = null, modifier = Modifier.size(19.dp))
                    Text("Scan this menu", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = { preview = null },
                    modifier = Modifier.fillMaxWidth().height(47.dp),
                    shape = RoundedCornerShape(7.dp)
                ) {
                    Text("Choose another photo")
                }
            }
        }

        EditorialDivider()
        Text(
            "For a cleaner scan",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Column {
            ScanTip(
                icon = { Icon(Icons.Outlined.LightMode, contentDescription = null) },
                title = "Use even light",
                detail = "Avoid glare and deep shadows."
            )
            EditorialDivider()
            ScanTip(
                icon = { Icon(Icons.Outlined.PhoneAndroid, contentDescription = null) },
                title = "Keep the page straight",
                detail = "Fill the frame without cropping prices."
            )
            EditorialDivider()
            ScanTip(
                icon = { Icon(Icons.Outlined.CenterFocusWeak, contentDescription = null) },
                title = "Focus before shooting",
                detail = "One menu page at a time works best."
            )
        }
        message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ScanTip(icon: @Composable () -> Unit, title: String, detail: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.secondary
        ) {
            Row(
                modifier = Modifier.size(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) { icon() }
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun Bitmap.toJpegBytes(): ByteArray {
    val output = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 90, output)
    return output.toByteArray()
}
