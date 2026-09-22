package com.add.pepers

import android.content.Context

/**
 * Persistent device-local memory for the last shop and the last registration
 * mode used for each shop. This state is intentionally kept local: it is UI
 * navigation state and must not be replaced by an older cloud snapshot.
 */
object SmartShopMemory {
    private const val PREFS = "pepers_smart_navigation"
    private const val LAST_SHOP_ID = "last_shop_id"
    private const val MODE_PREFIX = "shop_mode_"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun rememberShop(context: Context, shopId: Long) {
        prefs(context).edit().putLong(LAST_SHOP_ID, shopId).apply()
    }

    fun lastShopId(context: Context): Long? {
        val value = prefs(context).getLong(LAST_SHOP_ID, Long.MIN_VALUE)
        return value.takeUnless { it == Long.MIN_VALUE }
    }

    fun rememberMode(context: Context, shopId: Long, mode: RegistrationMode) {
        prefs(context).edit()
            .putString(MODE_PREFIX + shopId, mode.name)
            .putLong(LAST_SHOP_ID, shopId)
            .apply()
    }

    fun modeForShop(context: Context, shopId: Long): RegistrationMode? {
        val value = prefs(context).getString(MODE_PREFIX + shopId, null) ?: return null
        return runCatching { RegistrationMode.valueOf(value) }.getOrNull()
    }

    /**
     * Prefer the explicitly remembered local mode for this device/shop and
     * fall back to the persisted shop value when no local choice exists.
     */
    fun resolveMode(
        context: Context,
        shopId: Long,
        persistedMode: RegistrationMode?
    ): RegistrationMode = modeForShop(context, shopId) ?: persistedMode ?: RegistrationMode.NUMERIC
}
