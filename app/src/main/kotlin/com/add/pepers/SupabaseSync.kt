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
            "entries", "individual_entries", "individual_entry_items"
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
        listOf("user_profiles", "shops", "workers", "months", "pieces", "days", "entries", "individual_entries", "individual_entry_items").forEach { table ->
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
        val db = Database(context).readableDatabase
        val shopKeys = mutableMapOf<Long, String>()
        val workerKeys = mutableMapOf<Long, String>()
        val monthKeys = mutableMapOf<Long, String>()
        val pieceKeys = mutableMapOf<Long, String>()
        val dayKeys = mutableMapOf<Long, String>()
        val entryKeys = mutableMapOf<Long, String>()
        fun key(table: String, id: Long) = "$deviceId:$table:$id"
        fun base(table: String, id: Long): JSONObject = JSONObject().apply {
            put("user_id", userId)
            put("legacy_id", id)
            put("record_key", key(table, id))
            put("source_device_id", deviceId)
        }
        fun rows(sql: String, fill: (JSONObject, Cursor) -> Unit): JSONArray {
            val array = JSONArray()
            db.rawQuery(sql, null).use { cursor ->
                while (cursor.moveToNext()) array.put(JSONObject().also { fill(it, cursor) })
            }
            return array
        }
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
        })
        val shops = rows("SELECT id,name,default_worker_id,registration_mode,registration_number FROM shops") { o, c ->
            val id = c.getLong(0); shopKeys[id] = key("shops", id); o.put("user_id", userId).put("legacy_id", id).put("record_key", key("shops", id)).put("source_device_id", deviceId).put("name", c.getString(1)).put("default_worker_legacy_id", if (c.isNull(2)) JSONObject.NULL else c.getLong(2)).put("registration_mode", c.getString(3)).put("registration_number", c.getString(4))
        }
        val workers = rows("SELECT id,shop_id,name FROM workers") { o, c ->
            val id = c.getLong(0); o.put("user_id", userId).put("legacy_id", id).put("record_key", key("workers", id)).put("source_device_id", deviceId).put("shop_legacy_id", c.getLong(1)).put("shop_record_key", shopKeys[c.getLong(1)] ?: key("shops", c.getLong(1))).put("name", c.getString(2)); workerKeys[id] = key("workers", id)
        }
        val months = rows("SELECT id,shop_id,worker_id,year,month_number,name,worker_name,start_date,deduct_expense FROM months") { o, c ->
            val id = c.getLong(0); o.put("user_id", userId).put("legacy_id", id).put("record_key", key("months", id)).put("source_device_id", deviceId).put("shop_legacy_id", c.getLong(1)).put("shop_record_key", shopKeys[c.getLong(1)] ?: key("shops", c.getLong(1))).put("worker_legacy_id", if (c.isNull(2)) JSONObject.NULL else c.getLong(2)).put("worker_record_key", if (c.isNull(2)) JSONObject.NULL else workerKeys[c.getLong(2)]).put("year", c.getInt(3)).put("month_number", c.getInt(4)).put("name", c.getString(5)).put("worker_name", c.getString(6)).put("start_date", c.getString(7)).put("deduct_expense", c.getInt(8) != 0); monthKeys[id] = key("months", id)
        }
        val pieces = rows("SELECT id,shop_id,name,price FROM pieces") { o, c ->
            val id = c.getLong(0); o.put("user_id", userId).put("legacy_id", id).put("record_key", key("pieces", id)).put("source_device_id", deviceId).put("shop_legacy_id", c.getLong(1)).put("shop_record_key", shopKeys[c.getLong(1)] ?: key("shops", c.getLong(1))).put("name", c.getString(2)).put("price", c.getInt(3)); pieceKeys[id] = key("pieces", id)
        }
        val days = rows("SELECT id,month_id,date_value,expense,expense_note FROM days") { o, c ->
            val id = c.getLong(0); o.put("user_id", userId).put("legacy_id", id).put("record_key", key("days", id)).put("source_device_id", deviceId).put("month_legacy_id", c.getLong(1)).put("month_record_key", monthKeys[c.getLong(1)] ?: key("months", c.getLong(1))).put("date_value", c.getString(2)).put("day_name", "").put("expense", c.getInt(3)).put("expense_note", c.getString(4)); dayKeys[id] = key("days", id)
        }
        val entries = rows("SELECT id,day_id,piece_id,quantity,unit_price FROM entries") { o, c ->
            val id = c.getLong(0); o.put("user_id", userId).put("legacy_id", id).put("record_key", key("entries", id)).put("source_device_id", deviceId).put("day_legacy_id", c.getLong(1)).put("day_record_key", dayKeys[c.getLong(1)] ?: key("days", c.getLong(1))).put("piece_legacy_id", c.getLong(2)).put("piece_record_key", pieceKeys[c.getLong(2)] ?: key("pieces", c.getLong(2))).put("quantity", c.getInt(3)).put("unit_price", c.getInt(4));
            entryKeys[id] = key("entries", id)
        }
        val individual = rows("SELECT id,day_id,customer_name,page_number,customer_search,customer_search_dotless FROM individual_entries") { o, c ->
            val id = c.getLong(0); o.put("user_id", userId).put("legacy_id", id).put("record_key", key("individual_entries", id)).put("source_device_id", deviceId).put("day_legacy_id", c.getLong(1)).put("day_record_key", dayKeys[c.getLong(1)] ?: key("days", c.getLong(1))).put("customer_name", c.getString(2)).put("page_number", c.getString(3)).put("customer_search", c.getString(4)).put("customer_search_dotless", c.getString(5))
        }
        val items = rows("SELECT id,entry_id,piece_id,quantity,unit_price FROM individual_entry_items") { o, c ->
            val id = c.getLong(0); o.put("user_id", userId).put("legacy_id", id).put("record_key", key("individual_entry_items", id)).put("source_device_id", deviceId).put("entry_legacy_id", c.getLong(1)).put("entry_record_key", entryKeys[c.getLong(1)] ?: key("individual_entries", c.getLong(1))).put("piece_legacy_id", c.getLong(2)).put("piece_record_key", pieceKeys[c.getLong(2)] ?: key("pieces", c.getLong(2))).put("quantity", c.getInt(3)).put("unit_price", c.getInt(4))
        }
        return linkedMapOf(
            "user_profiles" to profiles, "shops" to shops, "workers" to workers, "months" to months,
            "pieces" to pieces, "days" to days, "entries" to entries, "individual_entries" to individual,
            "individual_entry_items" to items
        )
    }
}