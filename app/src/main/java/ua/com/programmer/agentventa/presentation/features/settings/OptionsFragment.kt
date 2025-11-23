package ua.com.programmer.agentventa.presentation.features.settings

import android.os.Bundle
import androidx.fragment.app.viewModels
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class OptionsFragment: PreferenceFragmentCompat() {

    private val viewModel: OptionsViewModel by viewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        viewModel.options.observe(this) { options ->
            val context = preferenceManager.context
            val screen = preferenceManager.createPreferenceScreen(context)

            screen.addPreference(CheckBoxPreference(context).apply {
                title = "Load images"
                summary = "Show images, loaded from a database server"
                key = "loadImages"
                isChecked = options.loadImages
                //isEnabled = false
                setOnPreferenceClickListener { isChecked = options.loadImages; true }
            })

            screen.addPreference(CheckBoxPreference(context).apply {
                title = "Use location services"
                summary = "Enable location services for collecting locations of clients and locations of the user"
                key = "locations"
                isChecked = options.locations
                //isEnabled = false
                setOnPreferenceClickListener { isChecked = options.locations; true }
            })

            screen.addPreference(CheckBoxPreference(context).apply {
                title = "Load clients locations"
                summary = "Show client location on map"
                key = "clientsLocations"
                isChecked = options.clientsLocations
                setOnPreferenceClickListener { isChecked = options.clientsLocations; true }
            })

            screen.addPreference(CheckBoxPreference(context).apply {
                title = "Edit clients locations"
                summary = "Enable editing client location on map"
                key = "editLocations"
                isChecked = options.editLocations
                setOnPreferenceClickListener { isChecked = options.editLocations; true }
            })

            preferenceScreen = screen
        }
    }

}