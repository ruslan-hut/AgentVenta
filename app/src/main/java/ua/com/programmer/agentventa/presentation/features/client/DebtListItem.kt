package ua.com.programmer.agentventa.presentation.features.client

import ua.com.programmer.agentventa.data.local.entity.Debt

/**
 * A row of the client debts list. Headers are not a UI decision: they appear only
 * because the data source tagged the documents with a group_name, and the total
 * they show is the group_sum it sent - the app does not compute it.
 */
sealed class DebtListItem {
    data class Header(val name: String, val sum: Double) : DebtListItem()
    data class Document(val debt: Debt) : DebtListItem()
}

/**
 * Turns documents ordered by (group_name, sorting) into a list with a header in
 * front of every named group. Documents without a group_name keep the plain flat
 * look; since they sort first, they stay above the grouped ones.
 */
fun List<Debt>.withGroupHeaders(): List<DebtListItem> {
    val items = mutableListOf<DebtListItem>()
    var currentGroup: String? = null
    for (debt in this) {
        if (debt.groupName != currentGroup) {
            currentGroup = debt.groupName
            if (debt.groupName.isNotBlank()) {
                items.add(DebtListItem.Header(debt.groupName, debt.groupSum))
            }
        }
        items.add(DebtListItem.Document(debt))
    }
    return items
}
