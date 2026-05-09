package com.futtv.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.futtv.app.ui.home.HomeScreen
import com.futtv.app.ui.theme.Background
import com.futtv.app.ui.theme.FutTVTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FutTVTheme {
                HomeScreen(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Background)
                )
            }
        }
    }
}
