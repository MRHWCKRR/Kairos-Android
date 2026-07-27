package com.kairos.app.ui.navigation

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kairos.app.ui.theme.KairosTheme
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("BREADCRUMB", "1: onCreate started")
        
        try {
            Log.d("BREADCRUMB", "2: Initializing Firebase explicitly")
            FirebaseApp.initializeApp(this)
            
            Log.d("BREADCRUMB", "3: Setting content")
            setContent {
                KairosTheme {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Kairos: Isolate Mode (Hello World)")
                    }
                }
            }
            Log.d("BREADCRUMB", "4: setContent finished")
        } catch (e: Exception) {
            Log.e("BREADCRUMB", "FATAL CRASH in onCreate", e)
        }
    }
}
