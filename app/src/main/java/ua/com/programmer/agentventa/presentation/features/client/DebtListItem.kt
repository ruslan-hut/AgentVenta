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
 * front of every named group. Headers appear only when there is something to
 * separate - two or more distinct groups; a single group (e.g. a client that
 * deals with one company) would just repeat the total already shown on top, so
 * the list stays flat. Documents without a group_name are never given a header.
 */
fun List<Debt>.withGroupHeaders(): List<DebtListItem> {
    val distinctGroups = mapNotNull { it.groupName.ifBlank { null } }.distinct()
    if (distinctGroups.size < 2) {
        return map { DebtListItem.Document(it) }
    }

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
