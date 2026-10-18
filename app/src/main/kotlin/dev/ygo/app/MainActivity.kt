package dev.ygo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import dev.ygo.app.core.state.AppState
import dev.ygo.app.ui.Root
import dev.ygo.app.ui.theme.YgoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Une seule instance pour toute l'app, créée ici et passée par le haut : même
        // principe que l'`AppState` de l'app iOS.
        val app = AppState(applicationContext, lifecycleScope)
        setContent {
            YgoTheme {
                Root(app = remember { app })
            }
        }
    }
}
