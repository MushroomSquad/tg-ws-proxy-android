package com.flowseal.tgwsproxy.byedpi.ui

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.flowseal.tgwsproxy.R
import com.flowseal.tgwsproxy.byedpi.fragments.MainSettingsFragment
import com.flowseal.tgwsproxy.byedpi.services.byeDpiRunning

class ByeDpiSettingsActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_byedpi_settings)
        setTitle(R.string.title_settings)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.settings_container, MainSettingsFragment())
            }
        }

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setTitle(R.string.title_settings)
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (supportFragmentManager.backStackEntryCount > 0) {
                        supportFragmentManager.popBackStack()
                    } else {
                        finish()
                    }
                }
            },
        )
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference,
    ): Boolean {
        val fragmentClass = pref.fragment ?: return false
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            fragmentClass,
        )
        fragment.arguments = pref.extras
        supportFragmentManager.commit {
            replace(R.id.settings_container, fragment)
            addToBackStack(null)
            setReorderingAllowed(true)
        }
        title = pref.title
        return true
    }

    override fun onResume() {
        super.onResume()
        if (byeDpiRunning.value && supportFragmentManager.backStackEntryCount == 0) {
            title = getString(R.string.title_settings) + " (VPN running)"
        }
    }
}
