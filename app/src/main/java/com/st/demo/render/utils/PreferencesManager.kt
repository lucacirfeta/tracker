package com.st.demo.render.utils

import android.content.Context
import androidx.core.content.edit
import com.st.blue_sdk.features.sensor_fusion.Quaternion


class PreferencesManager private constructor(context: Context) {

    companion object {
        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private val sharedPreferences =
        context.getSharedPreferences("RACKET_PREFS", Context.MODE_PRIVATE)

    fun saveResetPosition(qi: Float, qj: Float, qk: Float, qs: Float) {
        sharedPreferences.edit {
            putFloat("QI", qi)
            putFloat("QJ", qj)
            putFloat("QK", qk)
            putFloat("QS", qs)
        }
    }

    fun loadResetPosition(): Quaternion? {
        return if (sharedPreferences.contains("QI")) {
            Quaternion(
                timeStamp = 0L,
                qi = sharedPreferences.getFloat("QI", 0f),
                qj = sharedPreferences.getFloat("QJ", 0f),
                qk = sharedPreferences.getFloat("QK", 0f),
                qs = sharedPreferences.getFloat("QS", 1f)
            )
        } else {
            null
        }
    }
}