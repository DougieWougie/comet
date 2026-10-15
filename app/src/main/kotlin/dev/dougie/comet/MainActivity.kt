package dev.dougie.comet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.dougie.comet.ui.CometApp
import dev.dougie.comet.ui.theme.CometTheme

class MainActivity : ComponentActivity() {
    private val viewModel: CometViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CometTheme { CometApp(viewModel) }
        }
    }
}
