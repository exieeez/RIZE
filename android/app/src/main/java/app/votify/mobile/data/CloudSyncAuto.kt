package app.votify.mobile.data

import app.votify.mobile.VotifyApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Automatic cloud backup while signed in: every library or settings change (favorite added,
 * playlist created/edited, theme/customization changed) is pushed to the user's private
 * Firestore document a few seconds after the last change — no buttons, no user action.
 *
 * Safety rules:
 *  - nothing is pushed before the account appears (and never for server accounts);
 *  - the first emission after sign-in is only a baseline — a second device with existing
 *    local data never overwrites the cloud silently; pushes start from the user's NEXT change;
 *  - [suppressFor] mutes the watcher while a cloud restore is being written into the local
 *    database (otherwise importing would immediately push back).
 */
object CloudSyncAuto {

    @Volatile
    private var started = false

    @Volatile
    private var suppressUntil = 0L

    /** Cached Firebase idToken (they expire hourly) with its issue time. */
    private var cachedToken: String? = null
    private var cachedTokenAt = 0L

    /** Mute auto-push for [ms] milliseconds (used during cloud restores). */
    fun suppressFor(ms: Long) {
        suppressUntil = System.currentTimeMillis() + ms
    }

    fun start(app: VotifyApp) {
        if (started) return
        started = true
        val db = app.database
        val repo = app.settings

        // 1. Auto-push: observe local changes (favorites, playlists, history, settings) and push to cloud
        app.appScope.launch {
            var armed = false
            combine(
                repo.account,                        // sign-in / sign-out
                db.favorites().observeCount(),       // any favorite change
                db.playlists().observeSummaries(),   // any playlist change
                db.history().observeDistinctCount(), // any history change
                repo.settings,                       // theme / customization changes
            ) { acct, _, _, _, _ -> acct }
                .collectLatest { acct ->
                    if (acct == null || !acct.isFirebase) {
                        armed = false
                        return@collectLatest
                    }
                    // Baseline right after sign-in / app start: remember, don't push —
                    // overwriting the cloud from a fresh device must be a deliberate act.
                    if (!armed) {
                        armed = true
                        return@collectLatest
                    }
                    // Debounce: let a burst of changes settle quickly
                    delay(2000)
                    if (System.currentTimeMillis() < suppressUntil) return@collectLatest
                    val sync = CloudSync(db, repo)
                    if (!sync.hasLocalData()) return@collectLatest
                    pushQuietly(app, acct)
                }
        }

        // 2. Real-time auto-pull: detect cloud changes from PC / other devices and sync in real time
        app.appScope.launch {
            repo.account.collectLatest { acct ->
                if (acct == null || !acct.isFirebase) return@collectLatest
                var lastSeenUpdateTime: String? = null
                val sync = CloudSync(db, repo)

                // Initial baseline fetch on sign-in
                try {
                    val cfg = FirebaseRest.effectiveConfig(repo.settings.first().firebaseConfig)
                    if (cfg != null) {
                        val client = FirebaseRest(cfg)
                        val token = token(client, acct)
                        lastSeenUpdateTime = client.fetchCloudLibraryUpdateTime(token, acct.uid)
                        if (!sync.hasLocalData() && lastSeenUpdateTime != null) {
                            val cloud = client.pullFullCloudSync(token, acct.uid)
                            if (cloud != null && (cloud.favorites.isNotEmpty() || cloud.playlists.isNotEmpty() || cloud.history.isNotEmpty())) {
                                suppressFor(6000)
                                sync.importBlob(cloud)
                            }
                        }
                    }
                } catch (ignored: Exception) {}

                // Polling loop: checks if Firestore updateTime changed (from PC actions)
                while (true) {
                    delay(3500)
                    if (System.currentTimeMillis() < suppressUntil) continue
                    try {
                        val cfg = FirebaseRest.effectiveConfig(repo.settings.first().firebaseConfig) ?: continue
                        val client = FirebaseRest(cfg)
                        val token = token(client, acct)
                        val currentUpdateTime = client.fetchCloudLibraryUpdateTime(token, acct.uid)
                        if (currentUpdateTime != null && currentUpdateTime != lastSeenUpdateTime) {
                            if (lastSeenUpdateTime != null) {
                                // Cloud updated from PC! Pull and update local Room DB immediately
                                val cloud = client.pullFullCloudSync(token, acct.uid)
                                if (cloud != null) {
                                    suppressFor(6000)
                                    sync.importBlob(cloud)
                                }
                            }
                            lastSeenUpdateTime = currentUpdateTime
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (ignored: Exception) {
                        // Offline or transient network issue
                    }
                }
            }
        }
    }

    private suspend fun pushQuietly(app: VotifyApp, acct: Account) {
        try {
            val cfg = FirebaseRest.effectiveConfig(app.settings.settings.first().firebaseConfig) ?: return
            val client = FirebaseRest(cfg)
            val blobObj = CloudSync(app.database, app.settings).exportBlob()
            try {
                client.publishLibrary(token(client, acct), acct.uid, blobObj)
            } catch (e: FirebaseRestException) {
                if (e.code == "HTTP 401") {
                    // Token expired mid-flight — refresh once and retry.
                    cachedToken = null
                    try {
                        client.publishLibrary(token(client, acct), acct.uid, blobObj)
                    } catch (ignored: Exception) {
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline / transient error: silent — the next change retries.
        }
    }

    private suspend fun token(client: FirebaseRest, acct: Account): String {
        val now = System.currentTimeMillis()
        cachedToken?.let { if (now - cachedTokenAt < 45 * 60 * 1000L) return it }
        val fresh = try {
            client.refreshIdToken(acct.refreshToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: acct.token
        cachedToken = fresh
        cachedTokenAt = now
        return fresh
    }
}
