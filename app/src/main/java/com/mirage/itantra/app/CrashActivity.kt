package com.mirage.itantra.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mirage.itantra.ui.theme.ITantraTheme

class CrashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crashLog = intent.getStringExtra("crash_log") ?: "No crash log provided."
        
        setContent {
            ITantraTheme {
                Scaffold { padding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp)
                    ) {
                        item {
                            Text(
                                text = "FATAL CRASH",
                                fontSize = 24.sp,
                                color = androidx.compose.ui.graphics.Color.Red
                            )
                        }
                        item {
                            Text(
                                text = crashLog,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
