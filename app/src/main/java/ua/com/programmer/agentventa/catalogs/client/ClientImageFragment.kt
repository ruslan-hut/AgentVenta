package ua.com.programmer.agentventa.catalogs.client

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import com.igreenwood.loupe.Loupe
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.dao.entity.fileName
import ua.com.programmer.agentventa.databinding.ClientImageFragmentBinding
import ua.com.programmer.agentventa.shared.SharedViewModel

@AndroidEntryPoint
class ClientImageFragment: Fragment(), MenuProvider{
    private val viewModel: ClientImageViewModel by viewModels()
    private val sharedModel: SharedViewModel by activityViewModels()
    private val navigationArgs: ClientImageFragmentArgs by navArgs()
    private var _binding: ClientImageFragmentBinding? = null
    private val binding get() = _binding!!

    private var rotation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setImageParameters(navigationArgs.imageGuid)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ClientImageFragmentBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val menuHost : MenuHost = requireActivity()
        menuHost.addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)

        viewModel.image.observe(viewLifecycleOwner) {
            binding.apply {
                image = it
                isDefault.visibility = if (it.isDefault == 1) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                isSent.visibility = if (it.isSent == 0) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
                rotation = itemImage.rotation.toInt()
            }
            sharedModel.loadClientImage(it, binding.itemImage)
            val loupe = Loupe(binding.itemImage, binding.imageContainer)
            loupe.onViewTranslateListener = object : Loupe.OnViewTranslateListener {
                override fun onStart(view: ImageView) {
                    binding.imageContainer.visibility = View.VISIBLE
                }
                override fun onViewTranslate(view: ImageView, amount: Float) {}
                override fun onDismiss(view: ImageView) {
                    binding.imageContainer.visibility = View.GONE
                    binding.root.findNavController().popBackStack()
                }
                override fun onRestore(view: ImageView) {}
            }
        }

    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.menu_client_image, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.change_description -> {
                changeDescription()
            }
            R.id.set_default -> {
                viewModel.setDefault()
            }
            R.id.delete -> {
                AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.delete_data))
                    .setMessage(getString(R.string.text_erase_data))
                    .setPositiveButton(getString(R.string.delete_order)) { _, _ ->
                        deleteImage()
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }
            R.id.rotate -> {
                rotateImage()
            }
            else -> return false
        }
        return true
    }

    private fun rotateImage() {
        val image = viewModel.image.value ?: return
        rotation += 90
        sharedModel.loadClientImage(image, binding.itemImage, rotation)
    }

    private fun deleteImage() {
        val image = viewModel.image.value ?: return
        if (image.isLocal == 1) {
            sharedModel.deleteFileInCache(image.fileName())
        }
        viewModel.deleteImage()
        binding.root.findNavController().popBackStack()
    }

    private fun changeDescription() {
        val alertDialog = AlertDialog.Builder(requireContext())
        val editText = EditText(requireContext())

        alertDialog.setTitle(R.string.description)

        val lp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT)
        editText.layoutParams = lp
        editText.setText(viewModel.image.value?.description ?: "")
        alertDialog.setView(editText)

        alertDialog.setPositiveButton(R.string.save) { dialog, _ ->
            viewModel.changeDescription(editText.text.toString())
            dialog.dismiss()
        }

        alertDialog.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }

        alertDialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}