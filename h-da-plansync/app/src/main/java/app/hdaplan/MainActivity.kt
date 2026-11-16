package app.hdaplan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Model.init(this)
        Notifier.ensureChannel(this)
        setContent { AppTheme { AppRoot() } }
        Model.autoRefresh()
    }

    override fun onResume() {
        super.onResume()
        Model.reload()
    }
}
