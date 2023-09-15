package ua.com.programmer.agentventa.catalogs.product

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import com.igreenwood.loupe.Loupe
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.databinding.ProductImageFragmentBinding
import ua.com.programmer.agentventa.extensions.format
import ua.com.programmer.agentventa.shared.SharedViewModel


@AndroidEntryPoint
class ProductImageFragment: Fragment() {
    private val viewModel: ProductImageViewModel by viewModels()
    private val sharedModel: SharedViewModel by activityViewModels()
    private val navigationArgs: ProductImageFragmentArgs by navArgs()
    private var _binding: ProductImageFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel.setProductParameters(navigationArgs.productGuid)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = ProductImageFragmentBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.product.observe(viewLifecycleOwner) {
            val text = it.description + "\n" + it.code + "\n" + it.price.format(2)
            binding.apply {
                product = it
                itemText.text = text
            }
            sharedModel.loadImage(it, binding.itemImage)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}