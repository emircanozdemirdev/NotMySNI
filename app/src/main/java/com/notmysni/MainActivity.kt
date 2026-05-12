package com.notmysni

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.notmysni.ui.navigation.NotMySniApp
import com.notmysni.ui.theme.NotMySniTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NotMySniTheme {
                NotMySniApp()
            }
        }
    }
}
