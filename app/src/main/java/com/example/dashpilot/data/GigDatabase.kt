// app/src/main/java/com/example/gigpilot/data/GigDatabase.kt

package com.example.dashpilot.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.dashpilot.model.GigOrder

// ... (StatusLog, Shift, Earning, Expense entities remain unchanged) ...
@Entity(tableName = "status_logs")
data class StatusLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val status: String
)

@Entity(tableName = "shifts")
data class Shift(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val durationHours: Double,
    val miles: Double,
    val purpose: String = "Business"
)

@Entity(tableName = "earnings")
data class Earning(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val platform: String,
    val amount: Double,
    val tripCount: Int = 1
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val category: String,
    val amount: Double
)

// --- DAO ---
@Dao
interface GigDao {
    // --- Existing Methods ---
    @Insert suspend fun insertShift(shift: Shift)
    @Insert suspend fun insertEarning(earning: Earning)
    @Insert suspend fun insertExpense(expense: Expense)

    @Update suspend fun updateEarning(earning: Earning)
    @Update suspend fun updateShift(shift: Shift)

    @Query("SELECT * FROM shifts ORDER BY date DESC")
    fun getAllShifts(): Flow<List<Shift>>
    @Query("SELECT * FROM earnings ORDER BY date DESC")
    fun getAllEarnings(): Flow<List<Earning>>
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    // --- Order Methods ---
    @Insert suspend fun insertOrder(order: GigOrder)

    @Query("SELECT * FROM orders WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedOrders(start: Long, end: Long, limit: Int, offset: Int): List<GigOrder>

    // NEW: Bulk Insert for Import
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<GigOrder>)

    @Delete suspend fun deleteOrder(order: GigOrder)
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    fun getAllOrders(): Flow<List<GigOrder>>

    // NEW: One-shot fetch for Export
    @Query("SELECT * FROM orders ORDER BY timestamp DESC")
    suspend fun getOrdersList(): List<GigOrder>

    // Fetches only the requested window, sorted Chronologically (Oldest -> Newest)
    @Query("SELECT * FROM orders WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun getOrdersInRange(start: Long, end: Long): List<GigOrder>

    @Query("DELETE FROM orders") suspend fun clearOrders()

    @Query("""
        DELETE FROM orders 
        WHERE platform = :platform 
        AND price = :price 
        AND distanceMiles = :miles 
        AND timestamp > :cutoffTime
    """)
    suspend fun removeRecentDuplicates(
        platform: String,
        price: Double,
        miles: Double,
        cutoffTime: Long
    )

    // --- NEW: FOR SIMULATION ---
    @Query("SELECT * FROM status_logs WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp ASC")
    suspend fun getStatusLogsInRange(start: Long, end: Long): List<StatusLog>

    @Query("DELETE FROM status_logs") suspend fun clearStatusLogs()

    // --- Export Lists ---
    @Query("SELECT * FROM shifts") suspend fun getShiftsList(): List<Shift>
    @Query("SELECT * FROM earnings") suspend fun getEarningsList(): List<Earning>
    @Query("SELECT * FROM expenses") suspend fun getExpensesList(): List<Expense>

    // Clean Up
    @Query("DELETE FROM shifts") suspend fun clearShifts()
    @Query("DELETE FROM earnings") suspend fun clearEarnings()
    @Query("DELETE FROM expenses") suspend fun clearExpenses()

    // --- Status Logging ---
    @Insert suspend fun insertStatusLog(log: StatusLog)

    @Query("SELECT * FROM status_logs ORDER BY timestamp DESC")
    fun getAllStatusLogs(): Flow<List<StatusLog>>

    // [NEW] One-shot fetch for Exporting to CSV
    @Query("SELECT * FROM status_logs ORDER BY timestamp DESC")
    suspend fun getStatusLogsList(): List<StatusLog>

    @Transaction
    suspend fun clearAllData() {
        clearShifts()
        clearEarnings()
        clearExpenses()
        clearOrders()
        clearStatusLogs()
    }
}

// --- DATABASE SETUP ---
@Database(entities = [Shift::class, Earning::class, Expense::class, GigOrder::class, StatusLog::class], version = 8)
abstract class GigDatabase : RoomDatabase() {
    abstract fun gigDao(): GigDao

    companion object {
        @Volatile private var INSTANCE: GigDatabase? = null
        fun getDatabase(context: Context): GigDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    GigDatabase::class.java,
                    "gig_pilot_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}

// --- UNIFIED CSV HELPER ---
object UnifiedCsv {
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val orderFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    data class LedgerItem(
        val date: Long,
        val type: String,
        val category: String,
        val amount: Double,
        val extra: Double? = null
    )

    // --- EXISTING: MASTER LEDGER EXPORT ---
    suspend fun exportToStream(db: GigDatabase, outputStream: OutputStream) {
        val shifts = db.gigDao().getShiftsList().map { LedgerItem(it.date, "Mileage", it.purpose, it.miles, it.durationHours) }
        val earnings = db.gigDao().getEarningsList().map {
            val type = if (it.platform.equals("Tips", ignoreCase = true)) "Tip" else "Income"
            LedgerItem(it.date, type, it.platform, it.amount, it.tripCount.toDouble())
        }
        val expenses = db.gigDao().getExpensesList().map { LedgerItem(it.date, "Expense", it.category, it.amount) }

        val allItems = (shifts + earnings + expenses).sortedBy { it.date }

        outputStream.writer().use { writer ->
            writer.append("Date,Type,Category/Platform,Amount/Miles,Hours/Trips\n")
            allItems.forEach {
                val dateStr = fmt.format(Date(it.date))
                val extraStr = if (it.type == "Tip") "" else (it.extra?.toString() ?: "")
                writer.append("$dateStr,${it.type},${it.category},${it.amount},$extraStr\n")
            }
        }
    }

    // --- EXISTING: MASTER LEDGER IMPORT ---
    suspend fun importFromStream(db: GigDatabase, inputStream: InputStream): String {
        var count = 0
        inputStream.bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, line ->
                if (index == 0 || line.isBlank()) return@forEachIndexed
                try {
                    val cols = line.split(",")
                    if (cols.size < 4) return@forEachIndexed
                    val date = try { fmt.parse(cols[0])?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
                    val type = cols[1].trim()
                    val cat = cols[2].trim()
                    val amt = cols[3].toDoubleOrNull() ?: 0.0
                    val extra = cols.getOrNull(4)?.toDoubleOrNull() ?: 0.0

                    when (type) {
                        "Income" -> db.gigDao().insertEarning(Earning(date = date, platform = cat, amount = amt, tripCount = extra.toInt().coerceAtLeast(1)))
                        "Expense" -> db.gigDao().insertExpense(Expense(date = date, category = cat, amount = amt))
                        "Mileage" -> db.gigDao().insertShift(Shift(date = date, miles = amt, durationHours = extra, purpose = cat))
                    }
                    count++
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        return "Imported $count items."
    }

    // --- NEW: ORDER LOGS EXPORT ---
    suspend fun exportOrdersToStream(db: GigDatabase, outputStream: OutputStream) {
        val orders = db.gigDao().getOrdersList()
        outputStream.writer().use { writer ->
            // REMOVED: MerchantCount from Header
            writer.append("Timestamp,Date,Platform,Price,Miles,Minutes,IsEstimate,OrderCount,DropoffLocation\n")
            orders.forEach { order ->
                val dateStr = orderFmt.format(Date(order.timestamp))
                // REMOVED: ${order.merchant}
                writer.append("${order.timestamp},$dateStr,${order.platform},${order.price},${order.distanceMiles},${order.durationMinutes},${order.isEstimate},${order.orderCount},${order.dropoffLocation}\n")
            }
        }
    }

    // [NEW] STATUS LOGS EXPORT
    suspend fun exportStatusLogsToStream(db: GigDatabase, outputStream: OutputStream) {
        val logs = db.gigDao().getStatusLogsList()
        // Reusing orderFmt ("yyyy-MM-dd HH:mm:ss") for consistency
        outputStream.writer().use { writer ->
            writer.append("Timestamp,Date,Status\n")
            logs.forEach { log ->
                val dateStr = orderFmt.format(Date(log.timestamp))
                writer.append("${log.timestamp},$dateStr,${log.status}\n")
            }
        }
    }

    // --- NEW: ORDER LOGS IMPORT ---
    suspend fun importOrdersFromStream(db: GigDatabase, inputStream: InputStream): String {
        var count = 0
        val ordersToInsert = mutableListOf<GigOrder>()
        inputStream.bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, line ->
                if (index == 0 || line.isBlank()) return@forEachIndexed
                try {
                    val cols = line.split(",")
                    // We now expect fewer columns (size >= 7)
                    if (cols.size < 7) return@forEachIndexed

                    val timestamp = cols[0].toLongOrNull() ?: System.currentTimeMillis()
                    // cols[1] is Date string
                    val platform = cols[2].trim()
                    val price = cols[3].toDoubleOrNull() ?: 0.0
                    val miles = cols[4].toDoubleOrNull() ?: 0.0
                    val minutes = cols[5].toIntOrNull() ?: 0

                    // REMOVED: val merchant = cols[6]...

                    // SHIFT INDICES DOWN
                    val isEst = cols[6].toBoolean() // Was index 7
                    val orderCount = cols.getOrNull(7)?.toIntOrNull() ?: 1 // Was index 8

                    ordersToInsert.add(GigOrder(
                        timestamp = timestamp,
                        platform = platform,
                        price = price,
                        distanceMiles = miles,
                        durationMinutes = minutes,
                        // merchant argument is gone
                        isEstimate = isEst,
                        orderCount = orderCount
                    ))
                    count++
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
        if (ordersToInsert.isNotEmpty()) {
            db.gigDao().insertOrders(ordersToInsert)
        }
        return "Imported $count orders."
    }
}