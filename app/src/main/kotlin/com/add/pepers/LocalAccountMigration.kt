package com.add.pepers

import android.content.Context
import android.database.sqlite.SQLiteDatabase

internal object LocalAccountMigration {
    private const val PREFS = "pepers_database_binding"
    private const val CLEANUP_PREFIX = "cleanup_done_"

    fun isReviewed(context: Context, userId: String): Boolean {
        val key = CLEANUP_PREFIX + userId.hashCode()
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(key, false)
    }

    fun markReviewed(context: Context, userId: String) {
        val key = CLEANUP_PREFIX + userId.hashCode()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(key, true)
            .apply()
    }

    fun clearActiveBusinessData(context: Context) {
        val database = Database(context)
        val db = database.writableDatabase
        db.beginTransaction()
        try {
            listOf(
                "assistant_withdrawals",
                "assistant_daily_records",
                "assistant_piece_rates",
                "assistants",
                "individual_entry_items",
                "individual_entries",
                "entries",
                "days",
                "months",
                "workers",
                "pieces",
                "shops",
                "sync_records"
            ).forEach { table ->
                if (tableExists(db, table)) db.delete(table, null, null)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            database.close()
        }
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean {
        db.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }
}
