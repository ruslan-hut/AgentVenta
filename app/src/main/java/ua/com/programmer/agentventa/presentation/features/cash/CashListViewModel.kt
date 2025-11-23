package ua.com.programmer.agentventa.presentation.features.cash

import dagger.hilt.android.lifecycle.HiltViewModel
import ua.com.programmer.agentventa.data.local.entity.Cash
import ua.com.programmer.agentventa.presentation.common.document.DocumentListViewModel
import ua.com.programmer.agentventa.domain.repository.CashRepository
import ua.com.programmer.agentventa.domain.repository.UserAccountRepository
import javax.inject.Inject

@HiltViewModel
class CashListViewModel @Inject constructor(
    cashRepository: CashRepository,
    userAccountRepository: UserAccountRepository
): DocumentListViewModel<Cash>(cashRepository, userAccountRepository) {

    init {
        setTotalsVisible(true)
    }

}