package ua.com.programmer.agentventa.extensions

import ua.com.programmer.agentventa.data.local.dao.DiscountDao
import ua.com.programmer.agentventa.data.local.entity.DiscountMatch

/**
 * Resolves the best-matching discount for a product by walking up the product group hierarchy.
 *
 * Discount sign convention:
 * - Negative = discount (price reduction)
 * - Positive = surcharge (price increase)
 *
 * Priority (highest to lowest):
 * 1. Exact client + exact product
 * 2. Exact client + parent group
 * 3. Exact client + grandparent group (and so on)
 * 4. Exact client + wildcard ("")
 * 5. Wildcard client + exact product
 * 6. Wildcard client + parent group
 * 7. Wildcard client + grandparent group (and so on)
 * 8. Wildcard client + wildcard ("")
 */
object DiscountResolver {

    /**
     * Resolves the discount percentage for a product+client combination.
     *
     * @param discountDao DAO for discount and product queries
     * @param dbGuid current account database GUID
     * @param clientGuid client GUID (the specific client)
     * @param productGuid product GUID
     * @return discount percentage (negative = discount, positive = surcharge, 0.0 = none)
     */
    suspend fun resolve(
        discountDao: DiscountDao,
        dbGuid: String,
        clientGuid: String,
        productGuid: String,
    ): Double {
        // Build ancestor chain: [productGuid, parentGroup, grandparentGroup, ..., ""]
        val ancestors = buildAncestorChain(discountDao, dbGuid, productGuid)

        // Fetch all matching discount rules in one query
        val matches = discountDao.getMatchingDiscounts(dbGuid, clientGuid, ancestors)
        if (matches.isEmpty()) return 0.0

        // Pick best match by priority
        return pickBest(matches, clientGuid, ancestors)?.discount ?: 0.0
    }

    /**
     * Builds the ordered ancestor chain for a product.
     * Result: [productGuid, parentGroupGuid, grandparentGroupGuid, ..., ""]
     * The empty string "" is always the last element (wildcard = any product).
     */
    private suspend fun buildAncestorChain(
        discountDao: DiscountDao,
        dbGuid: String,
        productGuid: String
    ): List<String> {
        val ancestors = mutableListOf(productGuid)
        var currentGuid = productGuid
        // Walk up the tree (max 10 levels as safety against circular references)
        for (i in 0 until 10) {
            val parentGuid = discountDao.getProductGroupGuid(dbGuid, currentGuid)
            if (parentGuid.isNullOrEmpty()) break
            ancestors.add(parentGuid)
            currentGuid = parentGuid
        }
        ancestors.add("") // wildcard
        return ancestors
    }

    /**
     * Picks the best matching discount rule by priority.
     * Exact client > wildcard client, then by ancestor depth (earlier = higher priority).
     */
    private fun pickBest(
        matches: List<DiscountMatch>,
        clientGuid: String,
        ancestors: List<String>
    ): DiscountMatch? {
        // Build position map for O(1) lookup
        val ancestorPriority = HashMap<String, Int>(ancestors.size)
        ancestors.forEachIndexed { index, guid -> ancestorPriority[guid] = index }

        return matches.minByOrNull { match ->
            val clientPriority = if (match.clientGuid == clientGuid) 0 else 1
            val productPriority = ancestorPriority[match.productGuid] ?: Int.MAX_VALUE
            // Combine: client priority is more significant
            clientPriority * 10000 + productPriority
        }
    }
}
