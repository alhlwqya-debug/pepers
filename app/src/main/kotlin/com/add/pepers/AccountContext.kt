package com.add.pepers

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single source of truth for the currently authenticated application context.
 *
 * The app has three different identities that must never be mixed:
 * - userId: Supabase account identity.
 * - shopId: local shop/workspace identity.
 * - role: TAILOR or ASSISTANT.
 *
 * This object does not grant permissions by itself. It only resolves and stores
 * the current context so screens, reports and sharing flows can use the same
 * account/shop selection instead of reading unrelated preferences independently.
 */
data class AppAccountContext(
    val userId: String,
    val role: AccountRole,
    val shopId: Long?,
    val shopName: String,
    val ready: Boolean
)

enum class AccountRole {
    TAILOR,
    ASSISTANT;

    companion object {
        fun from(value: String?): AccountRole =
            if (value.equals("ASSISTANT", ignoreCase = true)) ASSISTANT else TAILOR
    }
}

internal object AccountContextStore {
    private const val PREFS = "pepers_account_context"
    private const val USER_ID = "user_id"
    private const val ROLE = "role"
    private const val SHOP_ID = "shop_id"

    fun save(context: Context, account: AppAccountContext) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(USER_ID, account.userId)
            .putString(ROLE, account.role.name)
            .apply {
                if (account.shopId != null) putLong(SHOP_ID, account.shopId)
                else remove(SHOP_ID)
            }
            .apply()
    }

    fun load(context: Context): AppAccountContext? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val userId = prefs.getString(USER_ID, null)?.trim().orEmpty()
        if (userId.isBlank()) return null

        val shopId = prefs.getLong(SHOP_ID, -1L).takeIf { it > 0L }
        val role = AccountRole.from(prefs.getString(ROLE, null))
        val shopName = shopId?.let {
            runCatching { Database(context.applicationContext).use { db ->
                db.getShops().firstOrNull { shop -> shop.id == it }?.name.orEmpty()
            } }.getOrDefault("")
        }.orEmpty()

        return AppAccountContext(
            userId = userId,
            role = role,
            shopId = shopId,
            shopName = shopName,
            ready = true
        )
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    fun setCurrentShop(context: Context, shopId: Long?) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (shopId != null && shopId > 0L) putLong(SHOP_ID, shopId)
            else remove(SHOP_ID)
        }.apply()
    }

    suspend fun resolve(
        context: Context,
        userId: String,
        role: String?
    ): AppAccountContext = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val cleanUserId = userId.trim()
        require(cleanUserId.isNotBlank()) { "userId is required" }

        if (!LocalDatabaseAccountManager.ensureBoundUser(appContext, cleanUserId)) {
            throw IllegalStateException("Local database is not bound to the authenticated account")
        }

        val database = Database(appContext)
        val shops = try {
            database.getShops()
        } finally {
            database.close()
        }

        val accountRole = AccountRole.from(role)
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val savedShopId = prefs.getLong(SHOP_ID, -1L).takeIf { it > 0L }
        val selected = if (accountRole == AccountRole.ASSISTANT) {
            // An assistant account does not own the tailor's shop. Its access is
            // granted through the approved assistant-link RPCs, not by selecting
            // an owner shop from the local SQLite database.
            null
        } else {
            shops.firstOrNull { it.id == savedShopId }
                ?: shops.firstOrNull()
        }

        val account = AppAccountContext(
            userId = cleanUserId,
            role = accountRole,
            shopId = selected?.id,
            shopName = selected?.name.orEmpty(),
            ready = true
        )
        save(appContext, account)
        account
    }
}
