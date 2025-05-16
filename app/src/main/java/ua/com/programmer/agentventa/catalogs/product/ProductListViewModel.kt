package ua.com.programmer.agentventa.catalogs.product

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
import ua.com.programmer.agentventa.dao.entity.LProduct
import ua.com.programmer.agentventa.repository.ProductRepository
import ua.com.programmer.agentventa.shared.SharedParameters
import javax.inject.Inject

@HiltViewModel
class ProductListViewModel @Inject constructor(
    private val productRepository: ProductRepository
): ViewModel() {

    private val currentGroupGuid = MutableLiveData("")
    private var _selectMode = false
    val selectMode get() = _selectMode

    private val listParams = MutableLiveData<SharedParameters>()

    private val _products = MediatorLiveData<List<LProduct>>()
    val products get() = _products

    val currentGroup get() = currentGroupGuid.switchMap {
        productRepository.getProduct(it).asLiveData()
    }

    private val _searchVisibility = MutableLiveData(View.GONE)
    val searchVisibility : LiveData<Int>
        get() = _searchVisibility

    private val _searchText = MutableLiveData("")
    val searchText : LiveData<String>
        get() = _searchText

    private val _noDataTextVisibility = MutableLiveData(View.VISIBLE)
    val noDataTextVisibility : LiveData<Int>
        get() = _noDataTextVisibility

    fun toggleSearchVisibility() {
        if (_searchVisibility.value == View.VISIBLE) {
            _searchVisibility.value = View.GONE
            _searchText.value = ""
        } else {
            _searchVisibility.value = View.VISIBLE
        }
    }

    /**
    Method is called in EditText, so it must have parameters like an EditText's method 'onTextChanged'
     */
    @Suppress("UNUSED_PARAMETER")
    fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
        _searchText.value = text.toString()
    }

    fun setCurrentGroup(groupGuid: String?) {
        currentGroupGuid.value = groupGuid ?: ""
    }

    fun setSelectMode(mode: Boolean) {
        _selectMode = mode
    }

    fun setListParams(params: SharedParameters) {
        listParams.value = params
    }

    private fun loadData() {
        val sharedParams = listParams.value ?: return
        _noDataTextVisibility.value = View.GONE

        val params = sharedParams.copy(
            filter = _searchText.value ?: "",
            groupGuid = currentGroupGuid.value ?: "",
        )

        viewModelScope.launch {
            launch (Dispatchers.IO) {
                productRepository.getProducts(params).collect {
                    withContext(Dispatchers.Main) {
                        _noDataTextVisibility.value = if (it.isEmpty()) View.VISIBLE else View.GONE
                        _products.value = it
                    }
                }

            }
        }
    }

    init {
        _products.addSource(searchText) {
            loadData()
        }
        _products.addSource(listParams) {
            loadData()
        }
    }

}