package ua.com.programmer.agentventa.printer

import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PrinterSettingsFragment: Fragment() {

    private val viewModel: PrinterViewModel by activityViewModels()

}