package ua.com.programmer.agentventa.presentation.features.client

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ua.com.programmer.agentventa.data.local.entity.Debt

class DebtListItemTest {

    private fun debt(docId: String, group: String = "", groupSum: Double = 0.0, sorting: Long = 0) =
        Debt(docId = docId, groupName = group, groupSum = groupSum, sorting = sorting)

    @Test
    fun `no group names produce a flat list without headers`() {
        val result = listOf(debt("A"), debt("B")).withGroupHeaders()

        assertThat(result).containsExactly(
            DebtListItem.Document(debt("A")),
            DebtListItem.Document(debt("B")),
        ).inOrder()
    }

    @Test
    fun `a single group is not given a header`() {
        val rows = listOf(
            debt("A", group = "Company 1", groupSum = 300.0),
            debt("B", group = "Company 1", groupSum = 300.0),
        )

        val result = rows.withGroupHeaders()

        assertThat(result).containsExactly(
            DebtListItem.Document(rows[0]),
            DebtListItem.Document(rows[1]),
        ).inOrder()
    }

    @Test
    fun `each named group gets one header carrying the sent group sum`() {
        val rows = listOf(
            debt("A", group = "Company 1", groupSum = 300.0),
            debt("B", group = "Company 1", groupSum = 300.0),
            debt("C", group = "Company 2", groupSum = 50.0),
        )

        val result = rows.withGroupHeaders()

        assertThat(result).containsExactly(
            DebtListItem.Header("Company 1", 300.0),
            DebtListItem.Document(rows[0]),
            DebtListItem.Document(rows[1]),
            DebtListItem.Header("Company 2", 50.0),
            DebtListItem.Document(rows[2]),
        ).inOrder()
    }

    @Test
    fun `group sum is shown as sent and is not the sum of the rows`() {
        // rows are only a slice (last N days); the source sends the full total
        val rows = listOf(
            debt("A", group = "Company 1", groupSum = 1000.0, sorting = 1),
            debt("B", group = "Company 1", groupSum = 1000.0, sorting = 2),
            debt("C", group = "Company 2", groupSum = 5.0),
        )

        val header = rows.withGroupHeaders().first() as DebtListItem.Header

        assertThat(header.sum).isEqualTo(1000.0)
    }

    @Test
    fun `ungrouped rows stay flat above the grouped ones`() {
        val rows = listOf(
            debt("A"),
            debt("B", group = "Company 1", groupSum = 10.0),
            debt("C", group = "Company 2", groupSum = 20.0),
        )

        val result = rows.withGroupHeaders()

        assertThat(result).containsExactly(
            DebtListItem.Document(rows[0]),
            DebtListItem.Header("Company 1", 10.0),
            DebtListItem.Document(rows[1]),
            DebtListItem.Header("Company 2", 20.0),
            DebtListItem.Document(rows[2]),
        ).inOrder()
    }
}
