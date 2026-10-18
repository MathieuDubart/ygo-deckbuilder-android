package dev.ygo.app

import android.app.Application
import dev.ygo.app.core.l10n.L10n

class YgoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Les textes avant le premier écran : ils sont lus depuis les assets, donc
        // disponibles tout de suite, sans requête.
        L10n.load(this)
    }
}
