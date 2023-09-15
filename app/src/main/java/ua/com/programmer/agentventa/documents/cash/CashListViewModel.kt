package ua.com.programmer.agentventa.documents.cash

import android.view.View
import dagger.hilt.android.lifecycle.HiltViewModel
import ua.com.programmer.agentventa.dao.entity.Cash
import ua.com.programmer.agentventa.documents.common.DocumentListViewModel
import ua.com.programmer.agentventa.repository.CashRepository
import ua.com.programmer.agentventa.repository.UserAccountRepository
import javax.inject.Inject

@HiltViewModel
class CashListViewModel @Inject constructor(
    cashRepository: CashRepository,
    userAccountRepository: UserAccountRepository
): DocumentListViewModel<Cash>(cashRepository, userAccountRepository) {

    init {
        totalsVisibility.value = View.VISIBLE
    }

}