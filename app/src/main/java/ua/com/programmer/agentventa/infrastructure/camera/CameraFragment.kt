package ua.com.programmer.agentventa.infrastructure.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.findNavController
import androidx.navigation.fragment.navArgs
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import ua.com.programmer.agentventa.R
import ua.com.programmer.agentventa.databinding.FragmentCameraBinding
import ua.com.programmer.agentventa.presentation.common.viewmodel.SharedViewModel
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class CameraFragment : Fragment() {

    private val sharedModel: SharedViewModel by activityViewModels()
    private val navArgs: CameraFragmentArgs by navArgs()
    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!
    private var fileName: String = ""
    private val imageGuid = UUID.randomUUID().toString()

    private val cameraProvider: ListenableFuture<ProcessCameraProvider> by lazy {
        ProcessCameraProvider.getInstance(requireContext())
    }
    private val cameraExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }
    private var imageCapture: ImageCapture? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity?.setTitle(R.string.camera)
        val clientGuid = navArgs.clientGuid
        fileName = "$clientGuid#$imageGuid.jpg"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater,container,false)
        binding.lifecycleOwner = viewLifecycleOwner
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (permissionGranted()) {
                setupCamera()
            } else {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.CAMERA),
                    1
                )
                // navigate back
                binding.root.findNavController().navigateUp()
            }
        } else {
            setupCamera()
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onResume() {
        super.onResume()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onPause() {
        super.onPause()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    private fun permissionGranted(): Boolean {
        val state = ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
        return state == PackageManager.PERMISSION_GRANTED
    }

    private fun setupCamera() {
        imageCapture = ImageCapture.Builder()
            .setTargetResolution(Size(768, 1024))
            .build()
        cameraProvider.addListener({
            try {
                val provider = cameraProvider.get()
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(binding.cameraView.surfaceProvider)
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                provider.unbindAll()
                provider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("Camera", "setupCamera", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
        binding.fab.setOnClickListener {
            takePhoto()
        }
    }

    private fun takePhoto() {
        val fileOptions = ImageCapture.OutputFileOptions.Builder(
            sharedModel.fileInCache(fileName)
        ).build()
        imageCapture?.takePicture(fileOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                stopCamera()
            }
            override fun onError(exception: ImageCaptureException) {
                Log.e("Camera", "take photo", exception)
            }
        })
    }

    private fun stopCamera() {

        sharedModel.saveClientImage(navArgs.clientGuid, imageGuid)

        val handler = Handler(requireContext().mainLooper)
        handler.post {
            try {
                cameraProvider.get().unbindAll()
            } catch (e: Exception) {
                Log.e("Camera", "stopCamera", e)
            }
            binding.root.findNavController().navigateUp()
        }
    }

}