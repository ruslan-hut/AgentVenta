package ua.com.programmer.agentventa.presentation.features.client

import android.view.View
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.switchMap
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.com.programmer.agentventa.data.local.entity.LClient
import ua.com.programmer.agentventa.domain.repository.ClientRepository
import ua.com.programmer.agentventa.presentation.common.viewmodel.SharedParameters
import javax.inject.Inject

@HiltViewModel
class ClientListViewModel @Inject constructor(
    private val repository: ClientRepository,
) : ViewModel() {

    private val currentGroupGuid = MutableLiveData("")
    private val currentCompany = MutableLiveData("")
    private var selectMode = false
    val searchText = MutableLiveData("")

    private val listParams = MutableLiveData<SharedParameters>()

    private val _clients = MediatorLiveData<List<LClient>>()
    val clients get() = _clients

    val noDataTextVisibility get() = clients.switchMap {
        MutableLiveData(if (it.isEmpty()) View.VISIBLE else View.GONE)
    }

    val currentGroup get() = currentGroupGuid.switchMap {
        repository.getClient(it, currentCompany.value ?: "").asLiveData()
    }

    private val _searchVisibility = MutableLiveData(View.GONE)
    val searchVisibility : LiveData<Int>
        get() = _searchVisibility

    fun toggleSearchVisibility() {
        if (_searchVisibility.value == View.VISIBLE) {
            _searchVisibility.value = View.GONE
            searchText.value = ""
        } else {
            _searchVisibility.value = View.VISIBLE
        }
    }

    fun setListParameters(sharedParameters: SharedParameters) {
        listParams.value = sharedParameters
    }

    fun docType(): String {
        return listParams.value?.docType ?: ""
    }

    private fun loadData() {
        val sharedParams = listParams.value ?: return
        //_noDataTextVisibility.value = View.GONE

        val params = sharedParams.copy(
            filter = searchText.value ?: "",
            groupGuid = currentGroupGuid.value ?: "",
        )

        viewModelScope.launch {
            launch (Dispatchers.IO) {
                repository.getClients(params.groupGuid, params.filter, params.companyGuid).collect {
                    withContext(Dispatchers.Main) {
                        //_noDataTextVisibility.value = if (it.isEmpty()) View.VISIBLE else View.GONE
                        _clients.value = it
                    }
                }

            }
        }
    }

    /**
    Method is called in EditText, so it must have parameters like an EditText's method 'onTextChanged'
     */
    @Suppress("UNUSED_PARAMETER")
    fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
        searchText.value = text.toString()
    }

    fun setCurrentGroup(groupGuid: String?) {
        currentGroupGuid.value = groupGuid ?: ""
    }

    fun setSelectMode(mode: Boolean) {
        selectMode = mode
    }

    init {
        _clients.addSource(searchText) { loadData() }
        _clients.addSource(listParams) { loadData() }
    }

}
