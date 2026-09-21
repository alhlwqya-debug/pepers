package com.add.pepers

import android.content.Context
import android.content.ContentValues
import java.io.File
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Public client configuration. Never put a service_role key in the app. */
internal object SupabaseConfig {
    const val URL = "https://qieukleyxkvwygzxfgsm.supabase.co"
    const val PUBLISHABLE_KEY = "sb_publishable_5Y18fNgiYV2tRPyJH5_Ojg_lhf-Ww47"
}

internal data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val expiresAt: Long = 0L
)

internal object SupabaseSessionStore {
    private const val PREFS = "supabase_session"
    private const val ACCESS = "access_token"
    private const val REFRESH = "refresh_token"
    private const val USER = "user_id"
    private const val EXPIRES = "expires_at"

    fun load(context: Context): SupabaseSession? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val access = prefs.getString(ACCESS, null).orEmpty()
        val refresh = prefs.getString(REFRESH, null).orEmpty()
        val userId = prefs.getString(USER, null).orEmpty()
        if (access.isBlank() || refresh.isBlank() || userId.isBlank()) return null
        return SupabaseSession(access, refresh, userId, prefs.getLong(EXPIRES, 0L))
    }

    fun save(context: Context, session: SupabaseSession) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ACCESS, session.accessToken)
            .putString(REFRESH, session.refreshToken)
            .putString(USER, session.userId)
            .putLong(EXPIRES, session.expiresAt)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences("sync_identity", Context.MODE_PRIVATE)
        val existing = prefs.getString("device_id", null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString("device_id", created).apply()
        return created
    }
}

internal object SupabaseAuth {
    suspend fun signIn(context: Context, email: String, password: String): SupabaseSession =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("email", email).put("password", password)
            val json = SupabaseHttp.request(
                method = "POST",
                path = "/auth/v1/token?grant_type=password",
                body = body.toString()
            )
            val session = sessionFrom(json)
            SupabaseSessionStore.save(context, session)
            session
        }

    suspend fun signUp(context: Context, email: String, password: String): SupabaseSession? =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("email", email).put("password", password)
            val json = SupabaseHttp.request("POST", "/auth/v1/signup", body.toString())
            if (!json.has("access_token")) return@withContext null
            val session = sessionFrom(json)
            SupabaseSessionStore.save(context, session)
            session
        }

    suspend fun refresh(context: Context, session: SupabaseSession): SupabaseSession =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("refresh_token", session.refreshToken)
            val json = SupabaseHttp.request("POST", "/auth/v1/token?grant_type=refresh_token", body.toString())
            val refreshed = sessionFrom(json)
            SupabaseSessionStore.save(context, refreshed)
            refreshed
        }

    private fun sessionFrom(json: JSONObject): SupabaseSession {
        val user = json.optJSONObject("user") ?: throw IllegalStateException("لم يعثر Supabase على بيانات المستخدم")
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        val userId = user.optString("id")
        if (access.isBlank() || refresh.isBlank() || userId.isBlank()) {
            throw IllegalStateException("لم تكتمل جلسة الحساب. إذا كان تأكيد البريد مفعلاً، افتح رابط التأكيد أولاً.")
        }
        val expires = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L
        return SupabaseSession(access, refresh, userId, expires)
    }
}

internal object SupabaseSyncManager {
    data class SyncResult(val uploadedRows: Int, val message: String)

    suspend fun sync(context: Context, session: SupabaseSession): SyncResult = withContext(Dispatchers.IO) {
        SupabaseStorage.uploadProfileImage(context, session)
        val snapshot = LocalSyncSnapshot.read(context, session.userId, SupabaseSessionStore.deviceId(context))
        val order = listOf(
            "user_profiles", "shops", "workers", "months", "pieces", "days",
            "entries", "individual_entries", "individual_entry_items",
            "assistants", "assistant_piece_rates", "assistant_daily_records", "assistant_withdrawals"
        )
        var uploaded = 0
        for (table in order) {
            val rows = snapshot[table] ?: JSONArray()
            if (rows.length() == 0) continue
            SupabaseHttp.request(
                method = "POST",
                path = if (table == "user_profiles") "/rest/v1/$table?on_conflict=user_id" else "/rest/v1/$table?on_conflict=user_id,record_key",
                body = rows.toString(),
                session = session,
                prefer = "resolution=merge-duplicates,return=minimal",
            )
            uploaded += rows.length()
        }
        val remote = downloadRemote(session)
        remote["user_profiles"]?.optJSONObject(0)?.optString("avatar_path")?.takeIf { it.isNotBlank() }?.let {
            SupabaseStorage.downloadProfileImage(context, session, it)
        }
        LocalSyncImporter.apply(context, remote, SupabaseSessionStore.deviceId(context))
        SyncResult(uploaded, "تمت مزامنة $uploaded سجلًا")
    }

    private suspend fun downloadRemote(session: SupabaseSession): Map<String, JSONArray> = withContext(Dispatchers.IO) {
        val result = linkedMapOf<String, JSONArray>()
        listOf("user_profiles", "shops", "workers", "months", "pieces", "days", "entries", "individual_entries", "individual_entry_items", "assistants", "assistant_piece_rates", "assistant_daily_records", "assistant_withdrawals").forEach { table ->
            val response = SupabaseHttp.request("GET", "/rest/v1/$table?select=*&user_id=eq.${session.userId}", session = session)
            result[table] = response.optJSONArray("data") ?: JSONArray()
        }
        result
    }
}

private object LocalSyncImporter {
    fun apply(context: Context, remote: Map<String, JSONArray>, deviceId: String) {
        val db = Database(context).writableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_records (record_key TEXT PRIMARY KEY, table_name TEXT NOT NULL, local_id INTEGER NOT NULL)")
        val ids = mutableMapOf<String, Long>()
        fun resolve(table: String, recordKey: String, legacyId: Long): Long? {
            ids[recordKey]?.let { return it }
            db.rawQuery("SELECT local_id FROM sync_records WHERE record_key = ?", arrayOf(recordKey)).use { c ->
                if (c.moveToFirst()) return c.getLong(0).also { ids[recordKey] = it }
            }
            if (recordKey.startsWith("$deviceId:")) {
                db.rawQuery("SELECT id FROM $table WHERE id = ?", arrayOf(legacyId.toString())).use { c ->
                    if (c.moveToFirst()) return c.getLong(0).also { remember(db, recordKey, table, it); ids[recordKey] = it }
                }
            }
            return null
        }
        fun ensure(table: String, recordKey: String, legacyId: Long, values: ContentValues): Long {
            val existing = resolve(table, recordKey, legacyId)
            val id = if (existing != null) {
                db.update(table, values, "id = ?", arrayOf(existing.toString()))
                existing
            } else {
                db.insertOrThrow(table, null, values)
            }
            remember(db, recordKey, table, id)
            ids[recordKey] = id
            return id
        }
        fun long(o: JSONObject, name: String): Long = o.optLong(name, 0L)
        fun text(o: JSONObject, name: String): String = o.optString(name, "")
        fun nullableLong(o: JSONObject, name: String): Long? = if (o.isNull(name)) null else o.optLong(name).takeIf { it != 0L }
        fun rows(name: String) = remote[name] ?: JSONArray()

        rows("shops").forEachObject { o ->
            val id = long(o, "legacy_id")
            val values = ContentValues().apply { put("name", text(o, "name")); nullableLong(o, "default_worker_legacy_id")?.let { put("default_worker_id", it) }; put("registration_mode", text(o, "registration_mode")); put("registration_number", text(o, "registration_number")) }
            ensure("shops", text(o, "record_key"), id, values)
        }
        rows("workers").forEachObject { o ->
            val id = long(o, "legacy_id")
            val shopId = resolve("shops", text(o, "shop_record_key"), long(o, "shop_legacy_id")) ?: return@forEachObject
            ensure("workers", text(o, "record_key"), id, ContentValues().apply { put("shop_id", shopId); put("name", text(o, "name")) })
        }
        rows("months").forEachObject { o ->
            val id = long(o, "legacy_id")
            val shopId = resolve("shops", text(o, "shop_record_key"), long(o, "shop_legacy_id")) ?: return@forEachObject
            val workerId = if (o.isNull("worker_record_key")) null else resolve("workers", text(o, "worker_record_key"), long(o, "worker_legacy_id"))
            ensure("months", text(o, "record_key"), id, ContentValues().apply { put("shop_id", shopId); if (workerId != null) put("worker_id", workerId) else putNull("worker_id"); put("year", o.optInt("year")); put("month_number", o.optInt("month_number")); put("name", text(o, "name")); put("worker_name", text(o, "worker_name")); put("start_date", text(o, "start_date")); put("deduct_expense", if (o.optBoolean("deduct_expense", true)) 1 else 0) })
        }
        rows("pieces").forEachObject { o ->
            val id = long(o, "legacy_id")
            val shopId = resolve("shops", text(o, "shop_record_key"), long(o, "shop_legacy_id")) ?: return@forEachObject
            ensure("pieces", text(o, "record_key"), id, ContentValues().apply { put("shop_id", shopId); put("name", text(o, "name")); put("price", o.optInt("price")) })
        }
        rows("days").forEachObject { o ->
            val id = long(o, "legacy_id")
            val monthId = resolve("months", text(o, "month_record_key"), long(o, "month_legacy_id")) ?: return@forEachObject
            ensure("days", text(o, "record_key"), id, ContentValues().apply { put("month_id", monthId); put("date_value", text(o, "date_value")); put("expense", o.optInt("expense")); put("expense_note", text(o, "expense_note")) })
        }
        rows("entries").forEachObject { o ->
            val id = long(o, "legacy_id")
            val dayId = resolve("days", text(o, "day_record_key"), long(o, "day_legacy_id")) ?: return@forEachObject
            val pieceId = resolve("pieces", text(o, "piece_record_key"), long(o, "piece_legacy_id")) ?: return@forEachObject
            ensure("entries", text(o, "record_key"), id, ContentValues().apply { put("day_id", dayId); put("piece_id", pieceId); put("quantity", o.optInt("quantity")); put("unit_price", o.optInt("unit_price")) })
        }
        rows("individual_entries").forEachObject { o ->
            val id = long(o, "legacy_id")
            val dayId = resolve("days", text(o, "day_record_key"), long(o, "day_legacy_id")) ?: return@forEachObject
            ensure("individual_entries", text(o, "record_key"), id, ContentValues().apply { put("day_id", dayId); put("customer_name", text(o, "customer_name")); put("page_number", text(o, "page_number")); put("customer_search", text(o, "customer_search")); put("customer_search_dotless", text(o, "customer_search_dotless")) })
        }
        rows("individual_entry_items").forEachObject { o ->
            val id = long(o, "legacy_id")
            val entryId = resolve("individual_entries", text(o, "entry_record_key"), long(o, "entry_legacy_id")) ?: return@forEachObject
            val pieceId = resolve("pieces", text(o, "piece_record_key"), long(o, "piece_legacy_id")) ?: return@forEachObject
            ensure("individual_entry_items", text(o, "record_key"), id, ContentValues().apply { put("entry_id", entryId); put("piece_id", pieceId); put("quantity", o.optInt("quantity")); put("unit_price", o.optInt("unit_price")) })
        }
        rows("assistants").forEachObject { o ->
            val id = long(o, "legacy_id")
            val shopId = resolve("shops", text(o, "shop_record_key"), long(o, "shop_legacy_id")) ?: return@forEachObject
            val workerId = if (o.isNull("worker_record_key")) null else resolve("workers", text(o, "worker_record_key"), long(o, "worker_legacy_id"))
            ensure("assistants", text(o, "record_key"), id, ContentValues().apply {
                put("shop_id", shopId); if (workerId != null) put("worker_id", workerId) else putNull("worker_id")
                put("name", text(o, "name")); put("task", text(o, "task")); put("phone", text(o, "phone")); put("link_code", text(o, "link_code")); put("default_rate", o.optInt("default_rate")); put("start_date", text(o, "start_date"))
                if (o.isNull("end_date")) putNull("end_date") else put("end_date", text(o, "end_date"))
                put("active", if (o.optBoolean("active", true)) 1 else 0); put("notes", text(o, "notes"))
            })
        }
        rows("assistant_piece_rates").forEachObject { o ->
            val id = long(o, "legacy_id")
            val assistantId = resolve("assistants", text(o, "assistant_record_key"), long(o, "assistant_legacy_id")) ?: return@forEachObject
            val pieceId = resolve("pieces", text(o, "piece_record_key"), long(o, "piece_legacy_id")) ?: return@forEachObject
            ensure("assistant_piece_rates", text(o, "record_key"), id, ContentValues().apply {
                put("assistant_id", assistantId); put("piece_id", pieceId); put("rate", o.optInt("rate"))
                put("effective_from", text(o, "effective_from")); if (o.isNull("effective_to")) putNull("effective_to") else put("effective_to", text(o, "effective_to"))
            })
        }
        rows("assistant_daily_records").forEachObject { o ->
            val id = long(o, "legacy_id")
            val assistantId = resolve("assistants", text(o, "assistant_record_key"), long(o, "assistant_legacy_id")) ?: return@forEachObject
            val dayId = resolve("days", text(o, "day_record_key"), long(o, "day_legacy_id")) ?: return@forEachObject
            ensure("assistant_daily_records", text(o, "record_key"), id, ContentValues().apply {
                put("assistant_id", assistantId); put("day_id", dayId); put("status", text(o, "status"))
                put("expense", o.optInt("expense")); put("expense_note", text(o, "expense_note")); put("notes", text(o, "notes"))
            })
        }
        rows("assistant_withdrawals").forEachObject { o ->
            val id = long(o, "legacy_id")
            val assistantId = resolve("assistants", text(o, "assistant_record_key"), long(o, "assistant_legacy_id")) ?: return@forEachObject
            ensure("assistant_withdrawals", text(o, "record_key"), id, ContentValues().apply {
                put("assistant_id", assistantId); put("date_value", text(o, "date_value"))
                put("amount", o.optInt("amount")); put("note", text(o, "note"))
            })
        }

        remote["user_profiles"]?.optJSONObject(0)?.let { profile ->
            context.getSharedPreferences("add_paper_user", Context.MODE_PRIVATE).edit()
                .putString("user_name", profile.optString("display_name", ""))
                .putString("user_phone", profile.optString("phone", ""))
                .putString("user_email", profile.optString("email", ""))
                .putString("user_shop", profile.optString("shop_name", ""))
                .putString(
                "user_image",
                profile.optString("avatar_path", "").let { avatarPath ->
                    if (avatarPath.endsWith("/profile.jpg")) File(context.filesDir, "profile_image.jpg").absolutePath else avatarPath
                }
            )
                .apply()
        }
    }

    private fun remember(db: SQLiteDatabase, recordKey: String, table: String, localId: Long) {
        db.insertWithOnConflict("sync_records", null, ContentValues().apply { put("record_key", recordKey); put("table_name", table); put("local_id", localId) }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
        for (index in 0 until length()) action(optJSONObject(index) ?: continue)
    }
}
private object SupabaseHttp {
    fun request(
        method: String,
        path: String,
        body: String? = null,
        session: SupabaseSession? = null,
        prefer: String? = null,
    ): JSONObject {
        val connection = (URL(SupabaseConfig.URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            doInput = true
            setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            session?.let { setRequestProperty("Authorization", "Bearer ${it.accessToken}") }
            prefer?.let { setRequestProperty("Prefer", it) }
            if (body != null) doOutput = true
        }
        try {
            if (body != null) OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { reader -> reader.readText() } }.orEmpty()
            if (status !in 200..299) {
                val message = try { JSONObject(response).optString("message").ifBlank { response } } catch (_: Exception) { response }
                throw IllegalStateException("Supabase $status: $message")
            }
            return if (response.isBlank()) JSONObject() else if (response.trimStart().startsWith("[")) JSONObject().put("data", JSONArray(response)) else JSONObject(response)
        } finally {
            connection.disconnect()
        }
    }
}

private object LocalSyncSnapshot {
    fun read(context: Context, userId: String, deviceId: String): Map<String, JSONArray> {
        val db = Database(context).writableDatabase
        ensureSyncState(db)

        val shopKeys = mutableMapOf<Long, String>()
        val workerKeys = mutableMapOf<Long, String>()
        val monthKeys = mutableMapOf<Long, String>()
        val pieceKeys = mutableMapOf<Long, String>()
        val dayKeys = mutableMapOf<Long, String>()
        val entryKeys = mutableMapOf<Long, String>()

        fun syncKey(table: String, id: Long): String {
            db.rawQuery(
                "SELECT record_key FROM sync_records WHERE table_name = ? AND local_id = ? LIMIT 1",
                arrayOf(table, id.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
            val created = "$deviceId:$table:$id"
            db.insertWithOnConflict(
                "sync_records",
                null,
                ContentValues().apply {
                    put("record_key", created)
                    put("table_name", table)
                    put("local_id", id)
                },
                SQLiteDatabase.CONFLICT_IGNORE
            )
            db.rawQuery(
                "SELECT record_key FROM sync_records WHERE table_name = ? AND local_id = ? LIMIT 1",
                arrayOf(table, id.toString())
            ).use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
            return created
        }

        fun row(table: String, id: Long, build: JSONObject.() -> Unit): JSONObject =
            JSONObject().apply {
                put("record_key", syncKey(table, id))
                put("source_device_id", deviceId)
                build()
            }.let { stamp(db, it, table, id) }

        val profiles = JSONArray().put(JSONObject().apply {
            put("user_id", userId)
            put("display_name", context.getSharedPreferences("add_paper_user", Context.MODE_PRIVATE).getString("user_name", "").orEmpty())
            put("phone", context.getSharedPreferences("add_paper_user", Context.MODE_PRIVATE).getString("user_phone", "").orEmpty())
            put("email", context.getSharedPreferences("add_paper_user", Context.MODE_PRIVATE).getString("user_email", "").orEmpty())
            put("shop_name", context.getSharedPreferences("add_paper_user", Context.MODE_PRIVATE).getString("user_shop", "").orEmpty())
            val localImagePath = context.getSharedPreferences("add_paper_user", Context.MODE_PRIVATE).getString("user_image", "").orEmpty()
            if (localImagePath.isNotBlank() && File(localImagePath).exists()) {
                put("avatar_path", SupabaseStorage.profileObjectPath(userId))
            }
        }.let { stamp(db, it, "user_profiles", 0L, "profile:$userId") })

        val shops = JSONArray()
        db.rawQuery("SELECT id,name,default_worker_id,registration_mode,registration_number FROM shops", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val shopId = id
                shopKeys[id] = syncKey("shops", id)
                shops.put(row("shops", shopId) {
                    put("user_id", userId)
                    put("legacy_id", id)
                    put("name", c.getString(1))
                    put("default_worker_legacy_id", if (c.isNull(2)) JSONObject.NULL else c.getLong(2))
                    put("registration_mode", c.getString(3))
                    put("registration_number", c.getString(4))
                })
            }
        }

        val workers = JSONArray()
        db.rawQuery("SELECT id,shop_id,name FROM workers", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val shopId = c.getLong(1)
                workerKeys[id] = syncKey("workers", id)
                workers.put(row("workers", id) {
                    put("user_id", userId)
                    put("legacy_id", id)
                    put("shop_legacy_id", shopId)
                    put("shop_record_key", shopKeys[shopId] ?: syncKey("shops", shopId))
                    put("name", c.getString(2))
                })
            }
        }

        val months = JSONArray()
        db.rawQuery("SELECT id,shop_id,worker_id,year,month_number,name,worker_name,start_date,deduct_expense FROM months", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val shopId = c.getLong(1)
                val workerId = if (c.isNull(2)) null else c.getLong(2)
                monthKeys[id] = syncKey("months", id)
                months.put(row("months", id) {
                    put("user_id", userId)
                    put("legacy_id", id)
                    put("shop_legacy_id", shopId)
                    put("shop_record_key", shopKeys[shopId] ?: syncKey("shops", shopId))
                    put("worker_legacy_id", workerId ?: JSONObject.NULL)
                    put("worker_record_key", workerId?.let { workerKeys[it] ?: syncKey("workers", it) } ?: JSONObject.NULL)
                    put("year", c.getInt(3))
                    put("month_number", c.getInt(4))
                    put("name", c.getString(5))
                    put("worker_name", c.getString(6))
                    put("start_date", c.getString(7))
                    put("deduct_expense", c.getInt(8) != 0)
                })
            }
        }

        val pieces = JSONArray()
        db.rawQuery("SELECT id,shop_id,name,price FROM pieces", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val shopId = c.getLong(1)
                pieceKeys[id] = syncKey("pieces", id)
                pieces.put(row("pieces", id) {
                    put("user_id", userId)
                    put("legacy_id", id)
                    put("shop_legacy_id", shopId)
                    put("shop_record_key", shopKeys[shopId] ?: syncKey("shops", shopId))
                    put("name", c.getString(2))
                    put("price", c.getInt(3))
                })
            }
        }

        val days = JSONArray()
        db.rawQuery("SELECT id,month_id,date_value,expense,expense_note FROM days", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val monthId = c.getLong(1)
                dayKeys[id] = syncKey("days", id)
                days.put(row("days", id) {
                    put("user_id", userId)
                    put("legacy_id", id)
                    put("month_legacy_id", monthId)
                    put("month_record_key", monthKeys[monthId] ?: syncKey("months", monthId))
                    put("date_value", c.getString(2))
                    put("day_name", "")
                    put("expense", c.getInt(3))
                    put("expense_note", c.getString(4))
                })
            }
        }

        val entries = JSONArray()
        db.rawQuery("SELECT id,day_id,piece_id,quantity,unit_price FROM entries", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val dayId = c.getLong(1)
                val pieceId = c.getLong(2)
                entryKeys[id] = syncKey("entries", id)
                entries.put(row("entries", id) {
                    put("user_id", userId)
                    put("legacy_id", id)
                    put("day_legacy_id", dayId)
                    put("day_record_key", dayKeys[dayId] ?: syncKey("days", dayId))
                    put("piece_legacy_id", pieceId)
                    put("piece_record_key", pieceKeys[pieceId] ?: syncKey("pieces", pieceId))
                    put("quantity", c.getInt(3))
                    put("unit_price", c.getInt(4))
                })
            }
        }

        val individual = JSONArray()
        db.rawQuery("SELECT id,day_id,customer_name,page_number,customer_search,customer_search_dotless FROM individual_entries", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val dayId = c.getLong(1)
                individual.put(row("individual_entries", id) {
                    put("user_id", userId)
                    put("legacy_id", id)
                    put("day_legacy_id", dayId)
                    put("day_record_key", dayKeys[dayId] ?: syncKey("days", dayId))
                    put("customer_name", c.getString(2))
                    put("page_number", c.getString(3))
                    put("customer_search", c.getString(4))
                    put("customer_search_dotless", c.getString(5))
                })
            }
        }

        val items = JSONArray()
        db.rawQuery("SELECT id,entry_id,piece_id,quantity,unit_price FROM individual_entry_items", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val entryId = c.getLong(1)
                val pieceId = c.getLong(2)
                items.put(row("individual_entry_items", id) {
                    put("user_id", userId)
                    put("legacy_id", id)
                    put("entry_legacy_id", entryId)
                    put("entry_record_key", entryKeys[entryId] ?: syncKey("individual_entries", entryId))
                    put("piece_legacy_id", pieceId)
                    put("piece_record_key", pieceKeys[pieceId] ?: syncKey("pieces", pieceId))
                    put("quantity", c.getInt(3))
                    put("unit_price", c.getInt(4))
                })
            }
        }

        val assistants = JSONArray()
        db.rawQuery("SELECT id,shop_id,worker_id,name,task,phone,link_code,default_rate,start_date,end_date,active,notes FROM assistants", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0); val shopId = c.getLong(1); val workerId = if (c.isNull(2)) null else c.getLong(2)
                assistants.put(row("assistants", id) {
                    put("user_id", userId); put("legacy_id", id)
                    put("shop_legacy_id", shopId); put("shop_record_key", shopKeys[shopId] ?: syncKey("shops", shopId))
                    put("worker_legacy_id", workerId ?: JSONObject.NULL)
                    put("worker_record_key", workerId?.let { workerKeys[it] ?: syncKey("workers", it) } ?: JSONObject.NULL)
                    put("name", c.getString(3)); put("task", c.getString(4)); put("phone", c.getString(5)); put("link_code", c.getString(6)); put("default_rate", c.getInt(7)); put("start_date", c.getString(8))
                    put("end_date", if (c.isNull(9)) JSONObject.NULL else c.getString(9)); put("active", c.getInt(10) != 0); put("notes", c.getString(11))
                })
            }
        }
        val assistantRates = JSONArray()
        db.rawQuery("SELECT id,assistant_id,piece_id,rate,effective_from,effective_to FROM assistant_piece_rates", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0); val assistantId = c.getLong(1); val pieceId = c.getLong(2)
                assistantRates.put(row("assistant_piece_rates", id) {
                    put("user_id", userId); put("legacy_id", id)
                    put("assistant_legacy_id", assistantId); put("assistant_record_key", syncKey("assistants", assistantId))
                    put("piece_legacy_id", pieceId); put("piece_record_key", pieceKeys[pieceId] ?: syncKey("pieces", pieceId))
                    put("rate", c.getInt(3)); put("effective_from", c.getString(4)); put("effective_to", if (c.isNull(5)) JSONObject.NULL else c.getString(5))
                })
            }
        }
        val assistantDaily = JSONArray()
        db.rawQuery("SELECT id,assistant_id,day_id,status,expense,expense_note,notes,reported_quantity,entered_by,approval_status FROM assistant_daily_records", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0); val assistantId = c.getLong(1); val dayId = c.getLong(2)
                assistantDaily.put(row("assistant_daily_records", id) {
                    put("user_id", userId); put("legacy_id", id)
                    put("assistant_legacy_id", assistantId); put("assistant_record_key", syncKey("assistants", assistantId))
                    put("day_legacy_id", dayId); put("day_record_key", dayKeys[dayId] ?: syncKey("days", dayId))
                    put("status", c.getString(3)); put("expense", c.getInt(4)); put("expense_note", c.getString(5)); put("notes", c.getString(6)); put("reported_quantity", c.getInt(7)); put("entered_by", c.getString(8)); put("approval_status", c.getString(9))
                })
            }
        }
        val assistantWithdrawals = JSONArray()
        db.rawQuery("SELECT id,assistant_id,date_value,amount,note FROM assistant_withdrawals", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0); val assistantId = c.getLong(1)
                assistantWithdrawals.put(row("assistant_withdrawals", id) {
                    put("user_id", userId); put("legacy_id", id)
                    put("assistant_legacy_id", assistantId); put("assistant_record_key", syncKey("assistants", assistantId))
                    put("date_value", c.getString(2)); put("amount", c.getInt(3)); put("note", c.getString(4))
                })
            }
        }
        return linkedMapOf(
            "user_profiles" to profiles, "shops" to shops, "workers" to workers, "months" to months,
            "pieces" to pieces, "days" to days, "entries" to entries, "individual_entries" to individual,
            "individual_entry_items" to items, "assistants" to assistants,
            "assistant_piece_rates" to assistantRates, "assistant_daily_records" to assistantDaily,
            "assistant_withdrawals" to assistantWithdrawals
        )
    }

    private fun ensureSyncState(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS sync_records (" +
                "record_key TEXT PRIMARY KEY, table_name TEXT NOT NULL, local_id INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL DEFAULT 0, content_hash TEXT NOT NULL DEFAULT '')"
        )
        runCatching { db.execSQL("ALTER TABLE sync_records ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0") }
        runCatching { db.execSQL("ALTER TABLE sync_records ADD COLUMN content_hash TEXT NOT NULL DEFAULT ''") }
    }

    private fun stamp(
        db: SQLiteDatabase,
        value: JSONObject,
        table: String,
        localId: Long,
        overrideKey: String? = null
    ): JSONObject {
        val recordKey = overrideKey ?: value.optString("record_key")
        value.put("record_key", recordKey)
        if (recordKey.contains(":")) value.put("source_device_id", recordKey.substringBefore(":"))
        value.remove("updated_at")
        val hash = sha256(value.toString())

        var timestamp = 0L
        var previousHash = ""
        db.rawQuery(
            "SELECT updated_at,content_hash FROM sync_records WHERE record_key = ?",
            arrayOf(recordKey)
        ).use { c ->
            if (c.moveToFirst()) {
                timestamp = c.getLong(0)
                previousHash = c.getString(1).orEmpty()
            }
        }

        if (timestamp <= 0L || previousHash != hash) {
            timestamp = System.currentTimeMillis()
        }

        db.insertWithOnConflict(
            "sync_records",
            null,
            ContentValues().apply {
                put("record_key", recordKey)
                put("table_name", table)
                put("local_id", localId)
                put("updated_at", timestamp)
                put("content_hash", hash)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )

        value.put("updated_at", isoUtc(timestamp))
        return value
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun isoUtc(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(millis))
}