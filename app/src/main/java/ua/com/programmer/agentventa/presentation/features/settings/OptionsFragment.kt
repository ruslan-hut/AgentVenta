package ua.com.programmer.agentventa.presentation.features.settings

import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.viewModels
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R

@AndroidEntryPoint
class OptionsFragment: PreferenceFragmentCompat() {

    private val viewModel: OptionsViewModel by viewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        viewModel.options.observe(this) { options ->
            val context = preferenceManager.context
            val screen = preferenceManager.createPreferenceScreen(context)

            val notSet = getString(R.string.option_value_not_set)

            // General
            screen.addCategory(R.string.option_category_general) {
                addCheckBox(R.string.pref_title_load_images, options.loadImages)
                addCheckBox(R.string.option_title_printing, options.printingEnabled)
                addText(R.string.option_title_currency, options.currency, notSet)
                addCheckBox(R.string.option_title_differential_updates, options.differentialUpdates)
            }

            // Orders
            screen.addCategory(R.string.option_category_orders) {
                addCheckBox(R.string.pref_title_allow_price, options.allowPriceTypeChoose)
                addCheckBox(R.string.option_title_allow_return, options.allowReturn)
                addCheckBox(R.string.pref_title_delivery_date, options.requireDeliveryDate)
                addCheckBox(R.string.pref_title_show_client_price_only, options.showClientPriceOnly)
                addCheckBox(R.string.pref_title_set_client_price, options.setClientPrice)
                addCheckBox(R.string.pref_title_package_mark, options.usePackageMark)
                addCheckBox(R.string.option_title_use_demands, options.useDemands)
                addCheckBox(R.string.pref_title_check_order_location, options.checkOrderLocation)
            }

            // Clients
            screen.addCategory(R.string.option_category_clients) {
                addCheckBox(R.string.pref_title_clients_locations, options.clientsLocations)
                addCheckBox(R.string.option_title_edit_locations, options.editLocations)
                addCheckBox(R.string.option_title_clients_directions, options.clientsDirections)
                addCheckBox(R.string.pref_title_clients_goods, options.clientsProducts)
                addText(R.string.option_title_default_client, options.defaultClient, notSet)
            }

            // Companies & Stores
            screen.addCategory(R.string.option_category_companies) {
                addCheckBox(R.string.option_title_use_companies, options.useCompanies)
                addCheckBox(R.string.option_title_use_stores, options.useStores)
            }

            // Location
            screen.addCategory(R.string.option_category_location) {
                addCheckBox(R.string.pref_title_location_service, options.locations)
            }

            // Fiscal (only if provider is configured)
            if (options.fiscalProvider.isNotEmpty()) {
                screen.addCategory(R.string.option_category_fiscal) {
                    addText(R.string.option_title_fiscal_provider, options.fiscalProvider, notSet)
                    addText(R.string.option_title_fiscal_number, options.fiscalNumber, notSet)
                    addText(R.string.option_title_fiscal_cashier, options.fiscalCashier, notSet)
                    addText(R.string.option_title_fiscal_device_id, options.fiscalDeviceId, notSet)
                }
            }

            preferenceScreen = screen
        }
    }

    private inline fun PreferenceScreen.addCategory(
        @StringRes titleRes: Int,
        block: PreferenceCategory.() -> Unit
    ) {
        val category = PreferenceCategory(context).apply {
            title = getString(titleRes)
            isIconSpaceReserved = false
        }
        addPreference(category)
        category.block()
    }

    private fun PreferenceCategory.addCheckBox(
        @StringRes titleRes: Int,
        value: Boolean
    ) {
        addPreference(CheckBoxPreference(context).apply {
            title = getString(titleRes)
            isChecked = value
            isIconSpaceReserved = false
            setOnPreferenceClickListener { isChecked = value; true }
        })
    }

    private fun PreferenceCategory.addText(
        @StringRes titleRes: Int,
        value: String,
        fallback: String
    ) {
        addPreference(Preference(context).apply {
            title = getString(titleRes)
            summary = value.ifEmpty { fallback }
            isIconSpaceReserved = false
        })
    }
}
