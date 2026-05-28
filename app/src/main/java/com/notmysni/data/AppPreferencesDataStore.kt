package com.notmysni.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

internal val Context.appPreferencesDataStore by preferencesDataStore(name = "notmysni_prefs")
