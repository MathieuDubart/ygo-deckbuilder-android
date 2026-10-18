package dev.ygo.domain

/**
 * L'adresse du serveur telle qu'on la tape, ramenée à une forme canonique.
 *
 * Ce qu'on enregistre est l'adresse du **serveur**, pas celle de l'API : le segment `api` est
 * ajouté par le client HTTP à chaque requête. Deux conséquences, qui sont tout l'objet de ce
 * fichier.
 *
 * On accepte une adresse sans schéma — « ygo.example.com », « 192.168.1.20:4000 » — parce que
 * c'est ce qu'on tape naturellement, et refuser ça serait absurde. HTTPS par défaut ; `http://`
 * est conservé s'il est écrit, pour un serveur joint en réseau local ou par VPN.
 *
 * Et on retire un `/api` final. Quelqu'un qui connaît l'API colle volontiers l'adresse qu'il
 * voit dans son navigateur ou dans son reverse proxy, et sans ça on demanderait `/api/api/…`
 * pour un 404 incompréhensible. Le test porte sur `/api` et non sur `api`, pour ne pas mutiler
 * un hôte qui s'appelle `api.example.com` ; un serveur installé sous un chemin, lui, garde son
 * préfixe (`https://hote/ygo/api` → `https://hote/ygo`).
 *
 * Même comportement que `APIClient.normalize` côté iOS. Les deux doivent rester d'accord : une
 * adresse acceptée sur un téléphone et pas sur l'autre, c'est indébogable à distance.
 */
object ServerAddress {

    /**
     * Null si le texte ne peut pas donner une adresse utilisable.
     *
     * Le schéma est traité avant le chemin, et pas l'inverse : raboter les barres obliques
     * d'abord transforme « https:// » en « https: », qui n'a plus l'air d'avoir un schéma et
     * se voit donc préfixer d'un second — « https://https: », une adresse acceptée qui ne
     * mène nulle part.
     */
    fun normalize(input: String): String? {
        var text = input.trim()
        if (text.isEmpty()) return null

        if (!text.contains("://")) text = "https://$text"

        val scheme = text.substringBefore("://").lowercase()
        if (scheme != "http" && scheme != "https") return null

        var rest = text.substringAfter("://").trimEnd('/')
        if (rest.endsWith("/api", ignoreCase = true)) {
            rest = rest.dropLast(4).trimEnd('/')
        }

        // Un hôte vide (« https:// ») ou réduit à un port (« https://:4000 ») ne mène nulle
        // part : mieux vaut un champ qui reste invalide qu'une première requête qui échoue.
        val host = rest.substringBefore('/')
        if (host.isEmpty() || host.startsWith(":")) return null

        return "$scheme://$rest"
    }
}
