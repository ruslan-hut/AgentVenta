package ua.com.programmer.agentventa.catalogs.client

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.dao.entity.ClientImage
import ua.com.programmer.agentventa.repository.FilesRepository
import javax.inject.Inject

@HiltViewModel
class ClientImageViewModel@Inject constructor(
    private val filesRepository: FilesRepository
): ViewModel() {

    private val _image = MutableLiveData<ClientImage>()
    val image get() = _image

    fun setImageParameters(guid: String) {
        viewModelScope.launch {
            filesRepository.getClientImage(guid).collect {
                _image.value = it
            }
        }
    }

    fun changeDescription(description: String) {
        viewModelScope.launch {
            filesRepository.saveClientImage(image.value!!.copy(description = description))
        }
    }

    fun setDefault() {
        viewModelScope.launch {
            filesRepository.setAsDefault(image.value!!)
        }
    }

    fun deleteImage() {
        viewModelScope.launch {
            filesRepository.deleteClientImage(image.value!!.guid)
        }
    }
}