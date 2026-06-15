package fi.veneappi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import fi.veneappi.app.ui.VeneappiApp

class MainActivity : ComponentActivity() {
    @Volatile
    var keepAndroidSplashScreen: Boolean = true
        private set

    fun dismissAndroidSplashScreen() {
        keepAndroidSplashScreen = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { keepAndroidSplashScreen }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VeneappiApp()
        }
    }
}
