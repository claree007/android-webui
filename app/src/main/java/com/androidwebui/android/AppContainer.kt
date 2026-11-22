package com.androidwebui.android

import android.content.Context
import com.androidwebui.android.core.database.AppDatabase
import com.androidwebui.android.core.database.LocalProfile
import com.androidwebui.android.core.database.LocalProfileRepository
import com.androidwebui.android.core.prefs.DevicePrefs
import com.androidwebui.android.core.prefs.SharedPreferencesStorage
import com.androidwebui.android.core.security.AndroidKeystoreKey
import com.androidwebui.android.core.security.EncryptedSecretStore
import com.androidwebui.android.core.security.SecretStore

/** Manual dependency wiring (D6). Created once by [AndroidWebuiApp]. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val devicePrefs: DevicePrefs by lazy { DevicePrefs(SharedPreferencesStorage(appContext, "device_prefs")) }

    val secretStore: SecretStore by lazy {
        EncryptedSecretStore(AndroidKeystoreKey::get, SharedPreferencesStorage(appContext, "secrets"))
    }

    val database: AppDatabase by lazy { AppDatabase(appContext) }

    /** Opens (creating if needed) the database and ensures the local **User** profile exists. Blocking. */
    fun loadProfile(): LocalProfile = LocalProfileRepository.ensure(database.store())
}
