package ua.com.programmer.agentventa.documents.task

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.databinding.TaskFragmentBinding
import ua.com.programmer.agentventa.shared.SharedViewModel

@AndroidEntryPoint
class TaskFragment: Fragment(), MenuProvider {
    private val viewModel: TaskViewModel by activityViewModels()
    private val sharedModel: SharedViewModel by activityViewModels()
    private val navigationArgs: TaskFragmentArgs by navArgs()
    private var _binding: TaskFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setCurrentDocument(navigationArgs.taskGuid)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = TaskFragmentBinding.inflate(layoutInflater)
        binding.lifecycleOwner = viewLifecycleOwner

        binding.editDescription.setOnFocusChangeListener { v, hasFocus ->
            if(!hasFocus) {
                viewModel.onEditDescription((v as EditText).text.toString())
            }
        }

        binding.editNotes.setOnFocusChangeListener { v, hasFocus ->
            if(!hasFocus) {
                viewModel.onEditNotes((v as EditText).text.toString())
                //editNotes()
            }
        }

        binding.isDone.setOnCheckedChangeListener { _, isChecked ->
            val isDone = if(isChecked) 1 else 0
            viewModel.onEditDone(isDone)
        }

        val menuHost : MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

//        viewModel.document.observe(this.viewLifecycleOwner) {
//            var title = getString(R.string.task)
//            if (it != null) {
//                title += " ${it.number}"
//            }
//            (activity as AppCompatActivity).supportActionBar?.title = title
//            sharedModel.setDocumentGuid(it?.guid ?: "")
//            if (it.isProcessed > 0) {
//                binding.orderBottomBar.visibility = View.GONE
//            } else {
//                binding.orderBottomBar.visibility = View.VISIBLE
//            }
//        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.document.observe(viewLifecycleOwner) {
            binding.apply {
                dateCreated.text = it.date
                editDescription.setText(it.description)
                editNotes.setText(it.notes)
                isDone.isChecked = it.isDone > 0
            }
        }

        // Observe save events
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is ua.com.programmer.agentventa.shared.DocumentEvent.SaveSuccess -> {
                            Toast.makeText(
                                requireContext(),
                                getString(R.string.data_saved),
                                Toast.LENGTH_SHORT
                            ).show()
                            view.findNavController().popBackStack()
                        }
                        is ua.com.programmer.agentventa.shared.DocumentEvent.SaveError -> {
                            AlertDialog.Builder(requireContext())
                                .setTitle(getString(R.string.error))
                                .setMessage(event.message)
                                .setPositiveButton(getString(R.string.OK), null)
                                .show()
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        sharedModel.clearActions()
        viewModel.onDestroy()
        _binding = null
        super.onDestroy()
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_document, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.save_document -> {
                saveDocument()
            }
            R.id.edit_document -> viewModel.enableEdit()
            R.id.delete_document -> {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.delete_data))
                    .setMessage(getString(R.string.text_erase_data))
                    .setPositiveButton(getString(R.string.delete_order)) { _, _ ->
                        viewModel.deleteDocument()
                        view?.findNavController()?.popBackStack()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
            else -> return false
        }
        return true
    }

    private fun saveDocument() {
        // Capture current field values before saving
        val description = binding.editDescription.text.toString()
        val notes = binding.editNotes.text.toString()

        // Update ViewModel with current values
        viewModel.onEditDescription(description)
        viewModel.onEditNotes(notes)

        // Validate task before saving
        lifecycleScope.launch {
            val validationError = viewModel.validateTask()
            if (validationError != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.warning))
                    .setMessage(validationError)
                    .setPositiveButton(getString(R.string.OK), null)
                    .show()
                return@launch
            }

            // Save document
            viewModel.saveDocument()
        }
    }

    private fun editNotes() {
        val alertDialog = AlertDialog.Builder(requireContext())
        val editText = EditText(requireContext())

        alertDialog.setTitle(R.string.doc_notes)

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT)
        editText.layoutParams = lp
        editText.setText(viewModel.document.value?.notes ?: "")
        alertDialog.setView(editText)

        alertDialog.setPositiveButton(R.string.save) { dialog, _ ->
            viewModel.onEditNotes(editText.text.toString())
            dialog.dismiss()
        }

        alertDialog.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }

        alertDialog.show()
    }
}
