package dev.ygo.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.ygo.app.core.state.AppState
import dev.ygo.app.feature.auth.LoginScreen
import dev.ygo.app.feature.home.HomeScreen
import dev.ygo.app.feature.onboarding.ServerSetupScreen
import dev.ygo.app.feature.scanner.ScannerScreen

/**
 * Le routeur de l'app. Trois états avant d'arriver au contenu, dans cet ordre : a-t-on une
 * instance, y est-on connecté, et seulement là ce qu'on vient y faire. Les franchir dans
 * l'autre sens — proposer de se connecter sans savoir à quoi — ne mène qu'à des erreurs
 * réseau incompréhensibles.
 */
@Composable
fun Root(app: AppState) {
    LaunchedEffect(Unit) { app.start() }

    when (app.session) {
        AppState.Session.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        AppState.Session.NeedsServer -> ServerSetupScreen(app)
        AppState.Session.SignedOut -> LoginScreen(app)
        is AppState.Session.SignedIn -> SignedInNav(app)
    }
}

private object Routes {
    const val HOME = "home"
    const val SCAN = "scan"
}

@Composable
private fun SignedInNav(app: AppState) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(app = app, onScan = { nav.navigate(Routes.SCAN) })
        }
        composable(Routes.SCAN) {
            ScannerScreen(app = app, onClose = { nav.popBackStack() })
        }
    }
}
