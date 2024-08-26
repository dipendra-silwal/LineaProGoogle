package com.datecs.lineagoogle

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.datecs.BuildInfo

class SettingsFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener {

    private var callbacks: LineaAction? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.settings)

        bindPreference("beep_upon_scan")
        bindPreference("scan_button")
        bindPreference("battery_charge")
        bindPreference("power_max_current")
        bindPreference("external_speaker")
        bindPreference("external_speaker_button")
        bindPreference("vibrate_upon_scan")
        bindPreference("device_timeout_period")
        bindPreference("code128_symbology")
        bindPreference("barcode_scan_mode")
        bindPreference("barcode_scope_scale_mode")

        findPreference<Preference>("reset_barcode_engine")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                callbacks?.actionResetBarcodeEngine()
                true
            }

        findPreference<Preference>("update_firmware")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                callbacks?.actionUpdateFirmware()
                true
            }

        findPreference<Preference>("about")!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {

                val context = requireContext() // Use context from the fragment
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val versionName = packageInfo.versionName
               // val message = getString(R.string.app_version, BuildInfo.VERSION)
                val message = getString(R.string.app_version, versionName)
                val dialog = AlertDialog.Builder(activity)
                    .setTitle(R.string.app_name)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                    .create()
                dialog.show()
                true
            }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {}

    override fun onResume() {
        super.onResume()
        preferenceManager.getSharedPreferences()!!.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        preferenceManager.getSharedPreferences()!!.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        callbacks = try {
            activity as LineaAction?
        } catch (e: ClassCastException) {
            throw ClassCastException(activity.toString() + " must implement LineaAction")
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        val preference = findPreference<Preference>(key!!) ?: return
        if (preference is ListPreference) {
            preference.setValue(sharedPreferences.getString(key, ""))
        } else if (preference is CheckBoxPreference) {
            preference.setChecked(sharedPreferences.getBoolean(key, false))
        }
    }

    private fun bindPreference(key: String) {
        val preference = findPreference<Preference>(key)
        if (preference is ListPreference) {
            preference.setSummary(preference.getEntry())
            preference.setOnPreferenceChangeListener(Preference.OnPreferenceChangeListener { preference12: Preference, newValue: Any ->
                val i = (preference12 as ListPreference).findIndexOfValue(newValue.toString())
                val entries = preference12.entries
                preference12.setSummary(entries[i])
                setSetting(preference12.getKey(), newValue as String)
                true
            })
        } else (preference as? CheckBoxPreference)?.onPreferenceChangeListener =
            Preference.OnPreferenceChangeListener { preference1: Preference, newValue: Any ->
                setSetting(preference1.key, newValue.toString())
                true
            }
    }

    private fun setSetting(key: String, value: String) {
        callbacks!!.actionUpdateSetting(key, value)
    }
}
