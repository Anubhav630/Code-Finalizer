package com.runanywhere.kotlin_starter_example.debugger.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text

@Composable
fun ImageSourceDialog(
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onDismiss: () -> Unit
) {

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Code Image Source") },
        text = {

            Column {

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onGallery
                ) {
                    Text("Upload from device")
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onCamera
                ) {
                    Text("Capture handwritten code")
                }

            }
        },
        confirmButton = {}
    )
}