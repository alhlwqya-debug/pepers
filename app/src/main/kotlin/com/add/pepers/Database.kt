package com.add.pepers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal fun normalizeArabicForSearch(value: String, ignoreDots: Boolean = false): String {
    val mapped = buildString(value.length) {
        for (raw in value.trim().lowercase(Locale.ROOT)) {
            val ch = when (raw) {
                'أ', 'إ', 'آ', 'ٱ', 'ا' -> 'ا'
                'ى', 'ي', 'ئ' -> 'ي'
                'ؤ', 'و' -> 'و'
                'ة', 'ه' -> 'ه'
                'ک', 'ك' -> 'ك'
                'گ' -> 'ك'
                'پ', 'ب' -> 'ب'
                'چ', 'ج' -> 'ج'
                else -> raw
            }
            if (ch !in '\u064B'..'\u065F' && ch != '\u0640') {
                if (ignoreDots && ch in setOf('ب','ت','ث','ج','خ','ذ','ز','ش','ض','ظ','غ','ف','ق','ن','ي')) {
                append(when (ch) {
                    'ب','ت','ث' -> 'ب'
                    'ج','خ' -> 'ج'
                    'ذ','ز' -> 'ز'
                    'ش' -> 'س'
                    'ض' -> 'ص'
                    'ظ' -> 'ط'
                    'غ' -> 'ع'
                    'ف','ق' -> 'ف'
                    'ن','ي' -> 'ي'
                    else -> ch
                })
                } else {
                    append(ch)
                }
            }
        }
    }
    return mapped.filter { it.isLetterOrDigit() || it.isWhitespace() }.replace(Regex("\\s+"), " ").trim()
}

internal fun normalizePageForSearch(value: String): String = value.trim().map {
    when (it) {
        '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
        '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
        else -> it
    }
}.joinToString("").filter { it.isDigit() || it.isLetter() }

data class ShopRecord(
    val id: Long,
    val name: String,
    val defaultWorkerId: Long?,
    val registrationMode: RegistrationMode
)

data class WorkerRecord(
    val id: Long,
    val shopId: Long,
    val name: String
)

data class MonthRecord(
    val id: Long,
    val shopId: Long,
    val year: Int,
    val month: Int,
    val name: String,
    val workerName: String,
    val startDate: String,
    val deductExpense: Boolean
)

data class PieceRecord(
    val id: Long,
    val shopId: Long,
    val name: String,
    val price: Int
)

data class IndividualSearchResult(
    val entryId: Long,
    val customerName: String,
    val pageNumber: String,
    val monthId: Long,
    val monthName: String,
    val date: String
)

data class IndividualEntryRecord(
    val id: Long,
    val dayId: Long,
    val customerName: String,
    val pageNumber: String,
    val quantities: Map<Long, Int>,
    val unitPrices: Map<Long, Int>
)

data class DayRecord(
    val id: Long,
    val monthId: Long,
    val date: String,
    val dayName: String,
    val expense: Int,
    val expenseNote: String,
    val quantities: Map<Long, Int>,
    val unitPrices: Map<Long, Int>
)

data class MonthBundle(
    val month: MonthRecord,
    val pieces: List<PieceRecord>,
    val days: List<DayRecord>
)

class Database(context: Context) :
SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {
        private const val DATABASE_NAME = "add_paper.db"
        private const val DATABASE_VERSION = 5

        private const val TABLE_SHOPS = "shops"
        private const val TABLE_WORKERS = "workers"
        private const val TABLE_MONTHS = "months"
        private const val TABLE_PIECES = "pieces"
        private const val TABLE_DAYS = "days"
        private const val TABLE_ENTRIES = "entries"
        private const val TABLE_INDIVIDUAL_ENTRIES = "individual_entries"
        private const val TABLE_INDIVIDUAL_ITEMS = "individual_entry_items"

        private const val COL_ID = "id"
        private const val COL_SHOP_ID = "shop_id"
        private const val COL_WORKER_ID = "worker_id"
        private const val COL_DEFAULT_WORKER_ID = "default_worker_id"
        private const val COL_REGISTRATION_MODE = "registration_mode"
        private const val COL_MONTH_ID = "month_id"
        private const val COL_DAY_ID = "day_id"
        private const val COL_PIECE_ID = "piece_id"
        private const val COL_NAME = "name"
        private const val COL_YEAR = "year"
        private const val COL_MONTH = "month_number"
        private const val COL_WORKER = "worker_name"
        private const val COL_START_DATE = "start_date"
        private const val COL_DEDUCT = "deduct_expense"
        private const val COL_PRICE = "price"
        private const val COL_DATE = "date_value"
        private const val COL_EXPENSE = "expense"
        private const val COL_EXPENSE_NOTE = "expense_note"
        private const val COL_QUANTITY = "quantity"
        private const val COL_UNIT_PRICE = "unit_price"
        private const val COL_CUSTOMER_NAME = "customer_name"
        private const val COL_PAGE_NUMBER = "page_number"
        private const val COL_ENTRY_ID = "entry_id"
        private const val COL_CUSTOMER_SEARCH = "customer_search"
        private const val COL_CUSTOMER_SEARCH_DOTLESS = "customer_search_dotless"
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Keep configuration limited to safe connection-level settings.
        // WAL mode is intentionally not forced here because changing
        // journal_mode from onConfigure can fail on some Android SQLite
        // implementations and can crash the app before the first screen.
        db.setForeignKeyConstraintsEnabled(true)
        executePragmaSafely(db, "synchronous=NORMAL")
        executePragmaSafely(db, "temp_store=MEMORY")
        executePragmaSafely(db, "foreign_keys=ON")
    }

    private fun executePragmaSafely(db: SQLiteDatabase, statement: String) {
        try {
            db.rawQuery("PRAGMA $statement", null).use { cursor ->
                while (cursor.moveToNext()) {
                    // Consume returned values when the Android API exposes them.
                }
            }
        } catch (_: Exception) {
            // PRAGMA tuning is optional. Database availability must never depend
            // on a device-specific SQLite implementation detail.
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        createSchema(db)
    }

    private fun createSchema(db: SQLiteDatabase) {
        db.execSQL("PRAGMA auto_vacuum=INCREMENTAL")
        db.execSQL("PRAGMA page_size=4096")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SHOPS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NAME TEXT NOT NULL,
                $COL_DEFAULT_WORKER_ID INTEGER,
                $COL_REGISTRATION_MODE TEXT NOT NULL DEFAULT 'NUMERIC'
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_WORKERS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SHOP_ID INTEGER NOT NULL,
                $COL_NAME TEXT NOT NULL,
                FOREIGN KEY($COL_SHOP_ID)
                    REFERENCES $TABLE_SHOPS($COL_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MONTHS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SHOP_ID INTEGER NOT NULL,
                $COL_WORKER_ID INTEGER,
                $COL_YEAR INTEGER NOT NULL,
                $COL_MONTH INTEGER NOT NULL,
                $COL_NAME TEXT NOT NULL,
                $COL_WORKER TEXT NOT NULL DEFAULT '',
                $COL_START_DATE TEXT NOT NULL DEFAULT '',
                $COL_DEDUCT INTEGER NOT NULL DEFAULT 1,
                FOREIGN KEY($COL_SHOP_ID)
                    REFERENCES $TABLE_SHOPS($COL_ID)
                    ON DELETE CASCADE,
                FOREIGN KEY($COL_WORKER_ID)
                    REFERENCES $TABLE_WORKERS($COL_ID)
                    ON DELETE SET NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_PIECES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_SHOP_ID INTEGER NOT NULL,
                $COL_NAME TEXT NOT NULL,
                $COL_PRICE INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY($COL_SHOP_ID)
                    REFERENCES $TABLE_SHOPS($COL_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_DAYS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_MONTH_ID INTEGER NOT NULL,
                $COL_DATE TEXT NOT NULL,
                $COL_EXPENSE INTEGER NOT NULL DEFAULT 0,
                $COL_EXPENSE_NOTE TEXT NOT NULL DEFAULT '',
                FOREIGN KEY($COL_MONTH_ID)
                    REFERENCES $TABLE_MONTHS($COL_ID)
                    ON DELETE CASCADE,
                UNIQUE($COL_MONTH_ID, $COL_DATE)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ENTRIES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DAY_ID INTEGER NOT NULL,
                $COL_PIECE_ID INTEGER NOT NULL,
                $COL_QUANTITY INTEGER NOT NULL DEFAULT 0,
                $COL_UNIT_PRICE INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY($COL_DAY_ID)
                    REFERENCES $TABLE_DAYS($COL_ID)
                    ON DELETE CASCADE,
                FOREIGN KEY($COL_PIECE_ID)
                    REFERENCES $TABLE_PIECES($COL_ID)
                    ON DELETE CASCADE,
                UNIQUE($COL_DAY_ID, $COL_PIECE_ID)
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_workers_shop ON $TABLE_WORKERS($COL_SHOP_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_months_shop ON $TABLE_MONTHS($COL_SHOP_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_pieces_shop ON $TABLE_PIECES($COL_SHOP_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_days_month ON $TABLE_DAYS($COL_MONTH_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_entries_day ON $TABLE_ENTRIES($COL_DAY_ID)")
        createIndividualSchema(db)
    }

    private fun createIndividualSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_INDIVIDUAL_ENTRIES (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DAY_ID INTEGER NOT NULL,
                $COL_CUSTOMER_NAME TEXT NOT NULL DEFAULT '',
                $COL_PAGE_NUMBER TEXT NOT NULL DEFAULT '',
                $COL_CUSTOMER_SEARCH TEXT NOT NULL DEFAULT '',
                $COL_CUSTOMER_SEARCH_DOTLESS TEXT NOT NULL DEFAULT '',
                FOREIGN KEY($COL_DAY_ID)
                    REFERENCES $TABLE_DAYS($COL_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_INDIVIDUAL_ITEMS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_ENTRY_ID INTEGER NOT NULL,
                $COL_PIECE_ID INTEGER NOT NULL,
                $COL_QUANTITY INTEGER NOT NULL DEFAULT 0,
                $COL_UNIT_PRICE INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY($COL_ENTRY_ID)
                    REFERENCES $TABLE_INDIVIDUAL_ENTRIES($COL_ID)
                    ON DELETE CASCADE,
                FOREIGN KEY($COL_PIECE_ID)
                    REFERENCES $TABLE_PIECES($COL_ID)
                    ON DELETE CASCADE,
                UNIQUE($COL_ENTRY_ID, $COL_PIECE_ID)
            )
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_individual_entries_day ON $TABLE_INDIVIDUAL_ENTRIES($COL_DAY_ID)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_individual_entries_search ON $TABLE_INDIVIDUAL_ENTRIES($COL_CUSTOMER_SEARCH)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_individual_entries_page ON $TABLE_INDIVIDUAL_ENTRIES($COL_PAGE_NUMBER)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_individual_items_entry ON $TABLE_INDIVIDUAL_ITEMS($COL_ENTRY_ID)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            migrateToVersion2(db)
        }
        if (oldVersion < 3) {
            createIndividualSchema(db)
        }
        if (oldVersion < 4) {
            migrateToVersion4(db)
        }
        if (oldVersion < 5) {
            migrateToVersion5(db)
        }
    }

    private fun migrateToVersion4(db: SQLiteDatabase) {
        val columns = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info($TABLE_SHOPS)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            while (cursor.moveToNext()) {
                if (nameIndex >= 0) columns.add(cursor.getString(nameIndex))
            }
        }
        if (!columns.contains(COL_REGISTRATION_MODE)) {
            db.execSQL("ALTER TABLE $TABLE_SHOPS ADD COLUMN $COL_REGISTRATION_MODE TEXT NOT NULL DEFAULT 'NUMERIC'")
        }
    }

    private fun migrateToVersion5(db: SQLiteDatabase) {
        addColumnIfMissing(db, TABLE_INDIVIDUAL_ENTRIES, COL_CUSTOMER_SEARCH, "TEXT NOT NULL DEFAULT ''")
        addColumnIfMissing(db, TABLE_INDIVIDUAL_ENTRIES, COL_CUSTOMER_SEARCH_DOTLESS, "TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_individual_entries_search ON $TABLE_INDIVIDUAL_ENTRIES($COL_CUSTOMER_SEARCH)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_individual_entries_page ON $TABLE_INDIVIDUAL_ENTRIES($COL_PAGE_NUMBER)")
        db.rawQuery("SELECT $COL_ID, $COL_CUSTOMER_NAME FROM $TABLE_INDIVIDUAL_ENTRIES", null).use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val name = cursor.getString(1) ?: ""
                val values = ContentValues().apply {
                    put(COL_CUSTOMER_SEARCH, normalizeArabicForSearch(name))
                    put(COL_CUSTOMER_SEARCH_DOTLESS, normalizeArabicForSearch(name, ignoreDots = true))
                }
                db.update(TABLE_INDIVIDUAL_ENTRIES, values, "$COL_ID = ?", arrayOf(id.toString()))
            }
        }
        executePragmaSafely(db, "optimize")
    }

    private fun migrateToVersion2(db: SQLiteDatabase) {
        db.beginTransaction()
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS $TABLE_WORKERS (
                    $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COL_SHOP_ID INTEGER NOT NULL,
                    $COL_NAME TEXT NOT NULL,
                    FOREIGN KEY($COL_SHOP_ID)
                        REFERENCES $TABLE_SHOPS($COL_ID)
                        ON DELETE CASCADE
                )
                """.trimIndent()
            )

            addColumnIfMissing(
                db,
                TABLE_SHOPS,
                COL_DEFAULT_WORKER_ID,
                "INTEGER"
            )

            addColumnIfMissing(
                db,
                TABLE_MONTHS,
                COL_WORKER_ID,
                "INTEGER"
            )

            val cursor = db.rawQuery(
                "SELECT $COL_ID, $COL_SHOP_ID, $COL_WORKER FROM $TABLE_MONTHS WHERE TRIM($COL_WORKER) <> ''",
                null
            )

            cursor.use {
                while (it.moveToNext()) {
                    val monthId = it.getLong(0)
                    val shopId = it.getLong(1)
                    val workerName = it.getString(2).trim()

                    var workerId = findWorkerId(db, shopId, workerName)
                    if (workerId <= 0L) {
                        workerId = insertWorker(db, shopId, workerName)
                    }

                    val values = ContentValues().apply {
                        put(COL_WORKER_ID, workerId)
                    }
                    db.update(
                        TABLE_MONTHS,
                        values,
                        "$COL_ID = ?",
                        arrayOf(monthId.toString())
                    )
                }
            }

            val shopsCursor = db.rawQuery(
                "SELECT $COL_ID FROM $TABLE_SHOPS",
                null
            )

            shopsCursor.use {
                while (it.moveToNext()) {
                    val shopId = it.getLong(0)
                    val workerId = findFirstWorkerId(db, shopId)
                    if (workerId > 0L) {
                        val values = ContentValues().apply {
                            put(COL_DEFAULT_WORKER_ID, workerId)
                        }
                        db.update(
                            TABLE_SHOPS,
                            values,
                            "$COL_ID = ?",
                            arrayOf(shopId.toString())
                        )
                    }
                }
            }

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_workers_shop ON $TABLE_WORKERS($COL_SHOP_ID)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_months_shop ON $TABLE_MONTHS($COL_SHOP_ID)")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun addColumnIfMissing(
        db: SQLiteDatabase,
        table: String,
        column: String,
        definition: String
    ) {
        val cursor = db.rawQuery("PRAGMA table_info($table)", null)
        var exists = false
        cursor.use {
            while (it.moveToNext()) {
                if (it.getString(1).equals(column, ignoreCase = true)) {
                    exists = true
                    break
                }
            }
        }
        if (!exists) {
            db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
        }
    }

    private fun insertWorker(
        db: SQLiteDatabase,
        shopId: Long,
        name: String
    ): Long {
        val values = ContentValues().apply {
            put(COL_SHOP_ID, shopId)
            put(COL_NAME, name.trim())
        }
        return db.insert(TABLE_WORKERS, null, values)
    }

    private fun findWorkerId(
        db: SQLiteDatabase,
        shopId: Long,
        name: String
    ): Long {
        db.query(
            TABLE_WORKERS,
            arrayOf(COL_ID),
            "$COL_SHOP_ID = ? AND $COL_NAME = ?",
            arrayOf(shopId.toString(), name),
            null,
            null,
            "$COL_ID ASC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return -1L
    }

    private fun findFirstWorkerId(
        db: SQLiteDatabase,
        shopId: Long
    ): Long {
        db.query(
            TABLE_WORKERS,
            arrayOf(COL_ID),
            "$COL_SHOP_ID = ?",
            arrayOf(shopId.toString()),
            null,
            null,
            "$COL_ID ASC",
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return -1L
    }

    fun getShops(): List<ShopRecord> {
        val result = mutableListOf<ShopRecord>()
        readableDatabase.query(
            TABLE_SHOPS,
            arrayOf(COL_ID, COL_NAME, COL_DEFAULT_WORKER_ID, COL_REGISTRATION_MODE),
            null,
            null,
            null,
            null,
            "$COL_ID ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val defaultIndex = cursor.getColumnIndex(COL_DEFAULT_WORKER_ID)
                val modeIndex = cursor.getColumnIndex(COL_REGISTRATION_MODE)
                val mode = if (modeIndex >= 0 && !cursor.isNull(modeIndex)) {
                    runCatching { RegistrationMode.valueOf(cursor.getString(modeIndex)) }.getOrDefault(RegistrationMode.NUMERIC)
                } else {
                    RegistrationMode.NUMERIC
                }
                result.add(
                    ShopRecord(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                        name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                        defaultWorkerId = if (defaultIndex >= 0 && !cursor.isNull(defaultIndex)) cursor.getLong(defaultIndex) else null,
                        registrationMode = mode
                    )
                )
            }
        }
        return result
    }

    fun addShop(name: String, registrationMode: RegistrationMode = RegistrationMode.NUMERIC): Long {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return -1L
        val values = ContentValues().apply {
            put(COL_NAME, cleanName)
            put(COL_REGISTRATION_MODE, registrationMode.name)
        }
        return writableDatabase.insert(TABLE_SHOPS, null, values)
    }

    fun updateShop(shopId: Long, name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val values = ContentValues().apply { put(COL_NAME, cleanName) }
        writableDatabase.update(TABLE_SHOPS, values, "$COL_ID = ?", arrayOf(shopId.toString()))
    }

    fun updateShopRegistrationMode(shopId: Long, mode: RegistrationMode) {
        val values = ContentValues().apply { put(COL_REGISTRATION_MODE, mode.name) }
        writableDatabase.update(TABLE_SHOPS, values, "$COL_ID = ?", arrayOf(shopId.toString()))
    }

    fun deleteShop(shopId: Long): Boolean {
        if (shopId <= 0L) return false
        val db = writableDatabase
        db.beginTransaction()
        try {
            val exists = db.query(
                TABLE_SHOPS, arrayOf(COL_ID), "$COL_ID = ?", arrayOf(shopId.toString()), null, null, null, "1"
            ).use { it.moveToFirst() }
            if (!exists) return false

            // حذف بيانات المحل فقط وبترتيب آمن حتى لا يتأثر أي محل آخر.
            db.execSQL("DELETE FROM $TABLE_INDIVIDUAL_ITEMS WHERE $COL_ENTRY_ID IN (SELECT $COL_ID FROM $TABLE_INDIVIDUAL_ENTRIES WHERE $COL_DAY_ID IN (SELECT $COL_ID FROM $TABLE_DAYS WHERE $COL_MONTH_ID IN (SELECT $COL_ID FROM $TABLE_MONTHS WHERE $COL_SHOP_ID = ?)))", arrayOf(shopId))
            db.execSQL("DELETE FROM $TABLE_INDIVIDUAL_ENTRIES WHERE $COL_DAY_ID IN (SELECT $COL_ID FROM $TABLE_DAYS WHERE $COL_MONTH_ID IN (SELECT $COL_ID FROM $TABLE_MONTHS WHERE $COL_SHOP_ID = ?))", arrayOf(shopId))
            db.execSQL("DELETE FROM $TABLE_ENTRIES WHERE $COL_DAY_ID IN (SELECT $COL_ID FROM $TABLE_DAYS WHERE $COL_MONTH_ID IN (SELECT $COL_ID FROM $TABLE_MONTHS WHERE $COL_SHOP_ID = ?))", arrayOf(shopId))
            db.execSQL("DELETE FROM $TABLE_DAYS WHERE $COL_MONTH_ID IN (SELECT $COL_ID FROM $TABLE_MONTHS WHERE $COL_SHOP_ID = ?)", arrayOf(shopId))
            db.delete(TABLE_MONTHS, "$COL_SHOP_ID = ?", arrayOf(shopId.toString()))
            db.delete(TABLE_PIECES, "$COL_SHOP_ID = ?", arrayOf(shopId.toString()))
            db.delete(TABLE_WORKERS, "$COL_SHOP_ID = ?", arrayOf(shopId.toString()))
            val deleted = db.delete(TABLE_SHOPS, "$COL_ID = ?", arrayOf(shopId.toString()))
            db.setTransactionSuccessful()
            return deleted > 0
        } finally {
            db.endTransaction()
        }
    }

    fun getWorkers(shopId: Long): List<WorkerRecord> {
        val result = mutableListOf<WorkerRecord>()
        readableDatabase.query(
            TABLE_WORKERS,
            arrayOf(COL_ID, COL_SHOP_ID, COL_NAME),
            "$COL_SHOP_ID = ?",
            arrayOf(shopId.toString()),
            null,
            null,
            "$COL_ID ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    WorkerRecord(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                        shopId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SHOP_ID)),
                        name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME))
                    )
                )
            }
        }
        return result
    }

    fun addWorker(shopId: Long, name: String, makeDefault: Boolean = true): Long {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return -1L

        val existing = findWorkerId(readableDatabase, shopId, cleanName)
        val workerId = if (existing > 0L) existing else insertWorker(writableDatabase, shopId, cleanName)
        if (workerId <= 0L) return -1L

        val shouldDefault = makeDefault || getDefaultWorkerId(shopId) == null
        if (shouldDefault) setDefaultWorker(shopId, workerId)

        val values = ContentValues().apply {
            put(COL_WORKER_ID, workerId)
            put(COL_WORKER, cleanName)
        }
        writableDatabase.update(
            TABLE_MONTHS,
            values,
            "$COL_SHOP_ID = ? AND (TRIM($COL_WORKER) = '' OR $COL_WORKER_ID IS NULL)",
            arrayOf(shopId.toString())
        )

        return workerId
    }

    fun updateWorker(workerId: Long, name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return

        val worker = getWorker(workerId) ?: return
        val values = ContentValues().apply { put(COL_NAME, cleanName) }
        writableDatabase.update(TABLE_WORKERS, values, "$COL_ID = ?", arrayOf(workerId.toString()))

        val monthValues = ContentValues().apply { put(COL_WORKER, cleanName) }
        writableDatabase.update(
            TABLE_MONTHS,
            monthValues,
            "$COL_WORKER_ID = ?",
            arrayOf(workerId.toString())
        )
    }

    fun deleteWorker(workerId: Long): Boolean {
        val worker = getWorker(workerId) ?: return false
        val workers = getWorkers(worker.shopId)
        if (workers.size <= 1) return false

        writableDatabase.delete(TABLE_WORKERS, "$COL_ID = ?", arrayOf(workerId.toString()))

        val defaultId = getDefaultWorkerId(worker.shopId)
        if (defaultId == workerId) {
            val replacement = getWorkers(worker.shopId).firstOrNull()
            if (replacement != null) setDefaultWorker(worker.shopId, replacement.id)
        }
        return true
    }

    private fun getWorker(workerId: Long): WorkerRecord? {
        readableDatabase.query(
            TABLE_WORKERS,
            arrayOf(COL_ID, COL_SHOP_ID, COL_NAME),
            "$COL_ID = ?",
            arrayOf(workerId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return WorkerRecord(
                    cursor.getLong(0),
                    cursor.getLong(1),
                    cursor.getString(2)
                )
            }
        }
        return null
    }

    fun getDefaultWorkerId(shopId: Long): Long? {
        readableDatabase.query(
            TABLE_SHOPS,
            arrayOf(COL_DEFAULT_WORKER_ID),
            "$COL_ID = ?",
            arrayOf(shopId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(COL_DEFAULT_WORKER_ID)
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
            }
        }
        return null
    }

    fun setDefaultWorker(shopId: Long, workerId: Long) {
        val worker = getWorker(workerId) ?: return
        if (worker.shopId != shopId) return
        val values = ContentValues().apply { put(COL_DEFAULT_WORKER_ID, workerId) }
        writableDatabase.update(TABLE_SHOPS, values, "$COL_ID = ?", arrayOf(shopId.toString()))
    }

    fun getMonths(shopId: Long): List<MonthRecord> {
        val result = mutableListOf<MonthRecord>()
        readableDatabase.query(
            TABLE_MONTHS,
            arrayOf(COL_ID, COL_SHOP_ID, COL_YEAR, COL_MONTH, COL_NAME, COL_WORKER, COL_START_DATE, COL_DEDUCT),
            "$COL_SHOP_ID = ?",
            arrayOf(shopId.toString()),
            null,
            null,
            "$COL_YEAR DESC, $COL_MONTH DESC, $COL_ID DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    MonthRecord(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COL_ID)),
                        shopId = cursor.getLong(cursor.getColumnIndexOrThrow(COL_SHOP_ID)),
                        year = cursor.getInt(cursor.getColumnIndexOrThrow(COL_YEAR)),
                        month = cursor.getInt(cursor.getColumnIndexOrThrow(COL_MONTH)),
                        name = cursor.getString(cursor.getColumnIndexOrThrow(COL_NAME)),
                        workerName = cursor.getString(cursor.getColumnIndexOrThrow(COL_WORKER)),
                        startDate = cursor.getString(cursor.getColumnIndexOrThrow(COL_START_DATE)),
                        deductExpense = cursor.getInt(cursor.getColumnIndexOrThrow(COL_DEDUCT)) == 1
                    )
                )
            }
        }
        return result
    }

    fun addMonth(
        shopId: Long,
        year: Int,
        month: Int,
        name: String,
        workerName: String,
        startDate: String,
        deductExpense: Boolean,
        workerId: Long? = null
    ): Long {
        val selectedWorkerId = workerId ?: getDefaultWorkerId(shopId)
        val selectedWorkerName = if (workerName.trim().isNotEmpty()) {
            workerName.trim()
        } else {
            selectedWorkerId?.let { getWorker(it)?.name } ?: ""
        }

        val values = ContentValues().apply {
            put(COL_SHOP_ID, shopId)
            put(COL_YEAR, year)
            put(COL_MONTH, month)
            put(COL_NAME, name.trim())
            put(COL_WORKER, selectedWorkerName)
            put(COL_START_DATE, startDate.trim())
            put(COL_DEDUCT, if (deductExpense) 1 else 0)
            if (selectedWorkerId != null) put(COL_WORKER_ID, selectedWorkerId)
        }

        val monthId = writableDatabase.insert(TABLE_MONTHS, null, values)
        if (monthId > 0L) {
            generateDaysForMonth(monthId, year, month)
        }
        return monthId
    }

    fun copyMonth(sourceMonthId: Long, newName: String, year: Int, month: Int): Long {
        val source = getMonth(sourceMonthId) ?: return -1L
        return addMonth(
            shopId = source.shopId,
            year = year,
            month = month,
            name = newName,
            workerName = source.workerName,
            startDate = source.startDate,
            deductExpense = source.deductExpense,
            workerId = findWorkerId(readableDatabase, source.shopId, source.workerName).takeIf { it > 0L }
        )
    }

    fun getMonth(monthId: Long): MonthRecord? {
        readableDatabase.query(
            TABLE_MONTHS,
            arrayOf(COL_ID, COL_SHOP_ID, COL_YEAR, COL_MONTH, COL_NAME, COL_WORKER, COL_START_DATE, COL_DEDUCT),
            "$COL_ID = ?",
            arrayOf(monthId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return MonthRecord(
                    id = cursor.getLong(0),
                    shopId = cursor.getLong(1),
                    year = cursor.getInt(2),
                    month = cursor.getInt(3),
                    name = cursor.getString(4),
                    workerName = cursor.getString(5),
                    startDate = cursor.getString(6),
                    deductExpense = cursor.getInt(7) == 1
                )
            }
        }
        return null
    }

    fun updateMonthInfo(
        monthId: Long,
        workerName: String,
        startDate: String,
        deductExpense: Boolean,
        workerId: Long? = null
    ) {
        val values = ContentValues().apply {
            put(COL_WORKER, workerName.trim())
            put(COL_START_DATE, startDate.trim())
            put(COL_DEDUCT, if (deductExpense) 1 else 0)
            if (workerId != null) put(COL_WORKER_ID, workerId) else putNull(COL_WORKER_ID)
        }
        writableDatabase.update(TABLE_MONTHS, values, "$COL_ID = ?", arrayOf(monthId.toString()))
    }

    fun renameMonth(monthId: Long, name: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val values = ContentValues().apply { put(COL_NAME, cleanName) }
        writableDatabase.update(TABLE_MONTHS, values, "$COL_ID = ?", arrayOf(monthId.toString()))
    }

    fun deleteMonth(monthId: Long) {
        writableDatabase.delete(TABLE_MONTHS, "$COL_ID = ?", arrayOf(monthId.toString()))
    }

    fun getPieceName(pieceId: Long): String {
        readableDatabase.query(TABLE_PIECES, arrayOf(COL_NAME), "$COL_ID = ?", arrayOf(pieceId.toString()), null, null, null, "1").use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return "قطعة"
    }

    fun getPieces(shopId: Long): List<PieceRecord> {
        val result = mutableListOf<PieceRecord>()
        readableDatabase.query(
            TABLE_PIECES,
            arrayOf(COL_ID, COL_SHOP_ID, COL_NAME, COL_PRICE),
            "$COL_SHOP_ID = ?",
            arrayOf(shopId.toString()),
            null,
            null,
            "$COL_ID ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    PieceRecord(
                        id = cursor.getLong(0),
                        shopId = cursor.getLong(1),
                        name = cursor.getString(2),
                        price = cursor.getInt(3)
                    )
                )
            }
        }
        return result
    }

    fun addPiece(shopId: Long, name: String, price: Int): Long {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return -1L
        val values = ContentValues().apply {
            put(COL_SHOP_ID, shopId)
            put(COL_NAME, cleanName)
            put(COL_PRICE, price.coerceAtLeast(0))
        }
        return writableDatabase.insert(TABLE_PIECES, null, values)
    }

    fun updatePiece(pieceId: Long, name: String, price: Int) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val values = ContentValues().apply {
            put(COL_NAME, cleanName)
            put(COL_PRICE, price.coerceAtLeast(0))
        }
        writableDatabase.update(TABLE_PIECES, values, "$COL_ID = ?", arrayOf(pieceId.toString()))
    }

    fun deletePiece(pieceId: Long): Boolean {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_ENTRIES WHERE $COL_PIECE_ID = ?",
            arrayOf(pieceId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst() && cursor.getInt(0) > 0) return false
        }
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $TABLE_INDIVIDUAL_ITEMS WHERE $COL_PIECE_ID = ?",
            arrayOf(pieceId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst() && cursor.getInt(0) > 0) return false
        }
        writableDatabase.delete(TABLE_PIECES, "$COL_ID = ?", arrayOf(pieceId.toString()))
        return true
    }

    // ✅ الدالة المصححة - توليد الأيام تلقائياً من اليوم الأول للشهر
    private fun generateDaysForMonth(monthId: Long, year: Int, month: Int) {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (day in 1 .. maxDay) {
                calendar.set(Calendar.DAY_OF_MONTH, day)
                val values = ContentValues().apply {
                    put(COL_MONTH_ID, monthId)
                    put(COL_DATE, formatDate(calendar))
                    put(COL_EXPENSE, 0)
                    put(COL_EXPENSE_NOTE, "")
                }
                db.insertWithOnConflict(
                    TABLE_DAYS,
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getDays(monthId: Long): List<DayRecord> {
        val days = mutableListOf<DayRecord>()
        readableDatabase.query(
            TABLE_DAYS,
            arrayOf(COL_ID, COL_MONTH_ID, COL_DATE, COL_EXPENSE, COL_EXPENSE_NOTE),
            "$COL_MONTH_ID = ?",
            arrayOf(monthId.toString()),
            null,
            null,
            "$COL_DATE ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val dayId = cursor.getLong(0)
                val quantities = mutableMapOf<Long, Int>()
                val unitPrices = mutableMapOf<Long, Int>()

                readableDatabase.query(
                    TABLE_ENTRIES,
                    arrayOf(COL_PIECE_ID, COL_QUANTITY, COL_UNIT_PRICE),
                    "$COL_DAY_ID = ?",
                    arrayOf(dayId.toString()),
                    null,
                    null,
                    null
                ).use { entryCursor ->
                    while (entryCursor.moveToNext()) {
                        quantities[entryCursor.getLong(0)] = entryCursor.getInt(1)
                        unitPrices[entryCursor.getLong(0)] = entryCursor.getInt(2)
                    }
                }

                val date = cursor.getString(2)
                days.add(
                    DayRecord(
                        id = dayId,
                        monthId = cursor.getLong(1),
                        date = date,
                        dayName = getArabicDayName(date),
                        expense = cursor.getInt(3),
                        expenseNote = cursor.getString(4),
                        quantities = quantities,
                        unitPrices = unitPrices
                    )
                )
            }
        }
        return days
    }

    fun loadMonthBundle(monthId: Long): MonthBundle? {
        val month = getMonth(monthId) ?: return null
        return MonthBundle(
            month = month,
            pieces = getPieces(month.shopId),
            days = getDays(monthId)
        )
    }

    private fun getOrCreateDay(monthId: Long, date: String): Long {
        readableDatabase.query(
            TABLE_DAYS,
            arrayOf(COL_ID),
            "$COL_MONTH_ID = ? AND $COL_DATE = ?",
            arrayOf(monthId.toString(), date),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }

        val values = ContentValues().apply {
            put(COL_MONTH_ID, monthId)
            put(COL_DATE, date)
            put(COL_EXPENSE, 0)
            put(COL_EXPENSE_NOTE, "")
        }
        return writableDatabase.insert(TABLE_DAYS, null, values)
    }

    fun setQuantity(monthId: Long, date: String, pieceId: Long, quantity: Int) {
        val dayId = getOrCreateDay(monthId, date)
        val cleanQuantity = quantity.coerceAtLeast(0)

        if (cleanQuantity == 0) {
            writableDatabase.delete(
                TABLE_ENTRIES,
                "$COL_DAY_ID = ? AND $COL_PIECE_ID = ?",
                arrayOf(dayId.toString(), pieceId.toString())
            )
            return
        }

        var existingPrice: Int? = null
        readableDatabase.query(
            TABLE_ENTRIES,
            arrayOf(COL_UNIT_PRICE),
            "$COL_DAY_ID = ? AND $COL_PIECE_ID = ?",
            arrayOf(dayId.toString(), pieceId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) existingPrice = cursor.getInt(0)
        }

        val unitPrice = existingPrice ?: getPiecePrice(pieceId)
        val values = ContentValues().apply {
            put(COL_DAY_ID, dayId)
            put(COL_PIECE_ID, pieceId)
            put(COL_QUANTITY, cleanQuantity)
            put(COL_UNIT_PRICE, unitPrice)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_ENTRIES,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getIndividualEntries(monthId: Long, date: String): List<IndividualEntryRecord> {
        val result = mutableListOf<IndividualEntryRecord>()
        val dayId = findDayId(monthId, date) ?: return result

        readableDatabase.query(
            TABLE_INDIVIDUAL_ENTRIES,
            arrayOf(COL_ID, COL_DAY_ID, COL_CUSTOMER_NAME, COL_PAGE_NUMBER),
            "$COL_DAY_ID = ?",
            arrayOf(dayId.toString()),
            null,
            null,
            "$COL_ID ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val entryId = cursor.getLong(0)
                val quantities = mutableMapOf<Long, Int>()
                val unitPrices = mutableMapOf<Long, Int>()

                readableDatabase.query(
                    TABLE_INDIVIDUAL_ITEMS,
                    arrayOf(COL_PIECE_ID, COL_QUANTITY, COL_UNIT_PRICE),
                    "$COL_ENTRY_ID = ?",
                    arrayOf(entryId.toString()),
                    null,
                    null,
                    null
                ).use { itemCursor ->
                    while (itemCursor.moveToNext()) {
                        quantities[itemCursor.getLong(0)] = itemCursor.getInt(1)
                        unitPrices[itemCursor.getLong(0)] = itemCursor.getInt(2)
                    }
                }

                result.add(
                    IndividualEntryRecord(
                        id = entryId,
                        dayId = cursor.getLong(1),
                        customerName = cursor.getString(2),
                        pageNumber = cursor.getString(3),
                        quantities = quantities,
                        unitPrices = unitPrices
                    )
                )
            }
        }
        return result
    }

    fun addIndividualEntry(monthId: Long, date: String): Long {
        val dayId = getOrCreateDay(monthId, date)
        val values = ContentValues().apply {
            put(COL_DAY_ID, dayId)
            put(COL_CUSTOMER_NAME, "")
            put(COL_PAGE_NUMBER, "")
            put(COL_CUSTOMER_SEARCH, "")
            put(COL_CUSTOMER_SEARCH_DOTLESS, "")
        }
        return writableDatabase.insert(TABLE_INDIVIDUAL_ENTRIES, null, values)
    }

    fun updateIndividualEntry(entryId: Long, customerName: String, pageNumber: String) {
        val cleanName = customerName.trim()
        val cleanPage = pageNumber.trim()
        val values = ContentValues().apply {
            put(COL_CUSTOMER_NAME, cleanName)
            put(COL_PAGE_NUMBER, cleanPage)
            put(COL_CUSTOMER_SEARCH, normalizeArabicForSearch(cleanName))
            put(COL_CUSTOMER_SEARCH_DOTLESS, normalizeArabicForSearch(cleanName, ignoreDots = true))
        }
        writableDatabase.update(
            TABLE_INDIVIDUAL_ENTRIES,
            values,
            "$COL_ID = ?",
            arrayOf(entryId.toString())
        )
    }

    fun deleteIndividualEntry(entryId: Long) {
        writableDatabase.delete(
            TABLE_INDIVIDUAL_ENTRIES,
            "$COL_ID = ?",
            arrayOf(entryId.toString())
        )
    }

    fun searchIndividualCustomers(shopId: Long, query: String): List<IndividualSearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return emptyList()
        val normalized = normalizeArabicForSearch(cleanQuery)
        val dotless = normalizeArabicForSearch(cleanQuery, ignoreDots = true)
        val pageQuery = normalizePageForSearch(cleanQuery)
        val result = mutableListOf<IndividualSearchResult>()
        val sql = """
            SELECT ie.$COL_ID, ie.$COL_CUSTOMER_NAME, ie.$COL_PAGE_NUMBER,
                   m.$COL_ID, m.$COL_NAME, d.$COL_DATE,
                   ie.$COL_CUSTOMER_SEARCH, ie.$COL_CUSTOMER_SEARCH_DOTLESS
            FROM $TABLE_INDIVIDUAL_ENTRIES ie
            INNER JOIN $TABLE_DAYS d ON d.$COL_ID = ie.$COL_DAY_ID
            INNER JOIN $TABLE_MONTHS m ON m.$COL_ID = d.$COL_MONTH_ID
            WHERE m.$COL_SHOP_ID = ?
            ORDER BY d.$COL_DATE DESC, ie.$COL_ID DESC
        """.trimIndent()
        readableDatabase.rawQuery(sql, arrayOf(shopId.toString())).use { cursor ->
            while (cursor.moveToNext() && result.size < 100) {
                val customer = cursor.getString(1) ?: ""
                val page = cursor.getString(2) ?: ""
                val storedSearch = cursor.getString(6) ?: ""
                val storedDotless = cursor.getString(7) ?: ""
                val pageNormalized = normalizePageForSearch(page)
                val matches = normalized.isEmpty() || storedSearch.contains(normalized) ||
                    dotless.length >= 2 && storedDotless.contains(dotless) ||
                    pageQuery.isNotEmpty() && pageNormalized.contains(pageQuery)
                if (matches) {
                    result.add(
                        IndividualSearchResult(
                            entryId = cursor.getLong(0),
                            customerName = customer,
                            pageNumber = page,
                            monthId = cursor.getLong(3),
                            monthName = cursor.getString(4),
                            date = cursor.getString(5)
                        )
                    )
                }
            }
        }
        return result
    }

    fun optimizeDatabase() {
        writableDatabase.rawQuery("PRAGMA wal_checkpoint(PASSIVE)", null).use { it.moveToFirst() }
        executePragmaSafely(writableDatabase, "optimize")
        executePragmaSafely(writableDatabase, "incremental_vacuum(200)")
    }

    fun setIndividualQuantity(entryId: Long, pieceId: Long, quantity: Int) {
        val cleanQuantity = quantity.coerceAtLeast(0)
        if (cleanQuantity == 0) {
            writableDatabase.delete(
                TABLE_INDIVIDUAL_ITEMS,
                "$COL_ENTRY_ID = ? AND $COL_PIECE_ID = ?",
                arrayOf(entryId.toString(), pieceId.toString())
            )
            return
        }

        var existingPrice: Int? = null
        readableDatabase.query(
            TABLE_INDIVIDUAL_ITEMS,
            arrayOf(COL_UNIT_PRICE),
            "$COL_ENTRY_ID = ? AND $COL_PIECE_ID = ?",
            arrayOf(entryId.toString(), pieceId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) existingPrice = cursor.getInt(0)
        }

        val unitPrice = existingPrice ?: getPiecePrice(pieceId)
        val values = ContentValues().apply {
            put(COL_ENTRY_ID, entryId)
            put(COL_PIECE_ID, pieceId)
            put(COL_QUANTITY, cleanQuantity)
            put(COL_UNIT_PRICE, unitPrice)
        }
        writableDatabase.insertWithOnConflict(
            TABLE_INDIVIDUAL_ITEMS,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun calculateIndividualEntryEarned(entry: IndividualEntryRecord): Int {
        var total = 0
        entry.quantities.forEach { item ->
            val pieceId = item.key
            val quantity = item.value
            total += quantity * (entry.unitPrices[pieceId] ?: 0)
        }
        return total
    }

    fun calculateIndividualDayEarned(entries: List<IndividualEntryRecord>): Int =
        entries.sumOf(::calculateIndividualEntryEarned)

    fun getDayRecord(monthId: Long, date: String): DayRecord? {
        return getDays(monthId).firstOrNull { it.date == date }
    }

    private fun findDayId(monthId: Long, date: String): Long? {
        readableDatabase.query(
            TABLE_DAYS,
            arrayOf(COL_ID),
            "$COL_MONTH_ID = ? AND $COL_DATE = ?",
            arrayOf(monthId.toString(), date),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
    }

    private fun getPiecePrice(pieceId: Long): Int {
        readableDatabase.query(
            TABLE_PIECES,
            arrayOf(COL_PRICE),
            "$COL_ID = ?",
            arrayOf(pieceId.toString()),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getInt(0)
        }
        return 0
    }

    fun setExpense(monthId: Long, date: String, amount: Int, note: String) {
        val dayId = getOrCreateDay(monthId, date)
        val values = ContentValues().apply {
            put(COL_EXPENSE, amount.coerceAtLeast(0))
            put(COL_EXPENSE_NOTE, note.trim())
        }
        writableDatabase.update(TABLE_DAYS, values, "$COL_ID = ?", arrayOf(dayId.toString()))
    }

    /**
     * يتحقق من وجود عمل مسجل لليوم الحالي في أي محل/شهر.
     * يعتبر اليوم مسجلاً إذا وُجدت كمية قطعة أكبر من صفر أو مصروف أكبر من صفر.
     */
    fun hasWorkRecordedToday(): Boolean {
        val today = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).format(Calendar.getInstance().time)

        readableDatabase.rawQuery(
            """
            SELECT 1
            FROM $TABLE_DAYS d
            LEFT JOIN $TABLE_ENTRIES e ON e.$COL_DAY_ID = d.$COL_ID
            WHERE d.$COL_DATE = ?
              AND (
                    d.$COL_EXPENSE > 0
                    OR COALESCE(e.$COL_QUANTITY, 0) > 0
                    OR EXISTS (
                        SELECT 1
                        FROM $TABLE_INDIVIDUAL_ENTRIES ie
                        WHERE ie.$COL_DAY_ID = d.$COL_ID
                          AND (TRIM(ie.$COL_CUSTOMER_NAME) <> '' OR TRIM(ie.$COL_PAGE_NUMBER) <> '')
                    )
              )
            LIMIT 1
            """.trimIndent(),
            arrayOf(today)
        ).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    fun clearMonthData(monthId: Long) {
        writableDatabase.delete(
            TABLE_INDIVIDUAL_ITEMS,
            "$COL_ENTRY_ID IN (SELECT $COL_ID FROM $TABLE_INDIVIDUAL_ENTRIES WHERE $COL_DAY_ID IN (SELECT $COL_ID FROM $TABLE_DAYS WHERE $COL_MONTH_ID = ?))",
            arrayOf(monthId.toString())
        )
        writableDatabase.delete(
            TABLE_INDIVIDUAL_ENTRIES,
            "$COL_DAY_ID IN (SELECT $COL_ID FROM $TABLE_DAYS WHERE $COL_MONTH_ID = ?)",
            arrayOf(monthId.toString())
        )
        writableDatabase.delete(
            TABLE_ENTRIES,
            "$COL_DAY_ID IN (SELECT $COL_ID FROM $TABLE_DAYS WHERE $COL_MONTH_ID = ?)",
            arrayOf(monthId.toString())
        )
        val values = ContentValues().apply {
            put(COL_EXPENSE, 0)
            put(COL_EXPENSE_NOTE, "")
        }
        writableDatabase.update(TABLE_DAYS, values, "$COL_MONTH_ID = ?", arrayOf(monthId.toString()))
    }

    fun deleteDay(dayId: Long) {
        writableDatabase.delete(TABLE_DAYS, "$COL_ID = ?", arrayOf(dayId.toString()))
    }

    fun calculateDayEarned(day: DayRecord): Int {
        var total = 0
        day.quantities.forEach { (pieceId, quantity) ->
            total += quantity * (day.unitPrices[pieceId] ?: 0)
        }
        return total
    }

    fun calculateMonthEarned(bundle: MonthBundle): Int = bundle.days.sumOf(::calculateDayEarned)

    fun calculateMonthExpenses(bundle: MonthBundle): Int = bundle.days.sumOf { it.expense }

    fun calculateMonthNet(bundle: MonthBundle): Int {
        val earned = calculateMonthEarned(bundle)
        val expenses = calculateMonthExpenses(bundle)
        return if (bundle.month.deductExpense) earned - expenses else earned
    }

    fun pieceTotal(bundle: MonthBundle, pieceId: Long): Int =
    bundle.days.sumOf { it.quantities[pieceId] ?: 0 }

    fun pieceEarned(bundle: MonthBundle, pieceId: Long): Int =
    bundle.days.sumOf { day ->
        (day.quantities[pieceId] ?: 0) * (day.unitPrices[pieceId] ?: 0)
    }

    private fun formatDate(calendar: Calendar): String =
    SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH).format(calendar.time)

    private fun getArabicDayName(date: String): String {
        return try {
            val formatter = SimpleDateFormat("yyyy/MM/dd", Locale.ENGLISH)
            val parsed = formatter.parse(date) ?: return ""
            val calendar = Calendar.getInstance().apply { time = parsed }
            when (calendar.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> "الأحد"
                Calendar.MONDAY -> "الإثنين"
                Calendar.TUESDAY -> "الثلاثاء"
                Calendar.WEDNESDAY -> "الأربعاء"
                Calendar.THURSDAY -> "الخميس"
                Calendar.FRIDAY -> "الجمعة"
                Calendar.SATURDAY -> "السبت"
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    override fun close() {
        super.close()
    }
}
