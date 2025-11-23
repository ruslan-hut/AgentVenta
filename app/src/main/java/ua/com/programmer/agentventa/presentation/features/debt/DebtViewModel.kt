package ua.com.programmer.agentventa.presentation.features.debt

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import ua.com.programmer.agentventa.domain.repository.ClientRepository
import ua.com.programmer.agentventa.domain.repository.NetworkRepository
import javax.inject.Inject

@HiltViewModel
class DebtViewModel @Inject constructor(
    private val clientRepository: ClientRepository,
    private val networkRepository: NetworkRepository
): ViewModel() {

    private val gson = Gson()

    private val _content = MutableLiveData<Content>()
    val content get() = _content
    private val _error = MutableLiveData<String>()
    val error get() = _error
    private val _loading = MutableLiveData<Boolean>()
    val loading get() = _loading

    fun setDebtParameters(guid: String, docId: String) {
        viewModelScope.launch {
            clientRepository.getDebt(guid, docId).collect { debt ->
                if (debt.content.isEmpty()) {
                    _content.value = Content()

                    _loading.value = true
                    networkRepository.getDebtContent(debt.docType, debt.docGuid).collect {
                        if (it is ua.com.programmer.agentventa.data.remote.Result.Error) {
                            _error.value = it.message
                        }
                        _loading.value = false
                    }

                    return@collect
                }
                try {
                    _content.value = gson.fromJson(debt.content, Content::class.java)
                }catch (e: Exception) {
                    _content.value = Content()
                    Log.e("DebtViewModel", "read content: $e")
                }
            }
        }
    }

    data class Item(
        val item: String = "",
        val code: String = "",
        val unit: String = "",
        val quantity: String = "",
        val price: String = "",
        val sum: String = ""
        )

    data class Content(
        val is_processed: Int = 0,
        val title: String = "",
        val company: String = "",
        val warehouse: String = "",
        val total: String = "",
        val contractor: String = "",
        val editor: String = "",
        val items: List<Item> = listOf()
    )
}