package com.flowseal.tgwsproxy.byedpi.fragments

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.flowseal.tgwsproxy.R

class ByeDpiCommandLineSettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.byedpi_cmd_settings, rootKey)
    }
}
