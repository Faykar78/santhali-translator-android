package com.vernova.santhali

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import com.vernova.santhali.ui.screens.TranslatorScreen
import com.vernova.santhali.ui.theme.SanthaliTranslatorTheme
import com.vernova.santhali.viewmodel.TranslatorViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: TranslatorViewModel by viewModels()

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) showPermissionDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request microphone permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            SanthaliTranslatorTheme {
                TranslatorScreen(viewModel = viewModel)
            }
        }
    }

    private fun showPermissionDenied() {
        setContent {
            SanthaliTranslatorTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Microphone permission is required for speech recognition.",
                        color = Color.White
                    )
                }
            }
        }
    }
}
