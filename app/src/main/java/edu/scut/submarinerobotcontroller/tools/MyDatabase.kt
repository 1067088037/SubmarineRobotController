package edu.scut.submarinerobotcontroller.tools

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import edu.scut.submarinerobotcontroller.Connector
import edu.scut.submarinerobotcontroller.Constant
import java.nio.ByteBuffer
import java.util.ArrayList
import kotlin.math.min

class MyDatabase(
    context: Context,
    name: String,
    factory: SQLiteDatabase.CursorFactory?,
    version: Int
) : SQLiteOpenHelper(context, name, factory, version) {

    companion object {
        private var instance: MyDatabase? = null

        fun getInstance(
            context: Context,
            name: String,
            factory: SQLiteDatabase.CursorFactory?,
            version: Int
        ): MyDatabase {
            if (instance != null) return instance!!
            instance = MyDatabase(context, name, factory, version)
            return instance!!
        }

        fun getInstance(): MyDatabase {
            return instance!!
        }
    }

    data class TimePowerDataList(
        var dataList: ArrayList<TimePowerData>,
        var id: Int,
        var description: String
    ) {
        companion object {
            fun input(string: String, description0: String): TimePowerDataList {
                val res = ArrayList<TimePowerData>(0)
                var after = string
                while (after.indexOf(';') != -1) {
                    val before = after.substringBefore(';')
                    after = after.substringAfter(';')
                    res.add(TimePowerData.input(before))
                }
                return TimePowerDataList(res, -1, description0)
            }
        }

        fun output(): String {
            var res = ""
            dataList.forEach {
                res += it.output()
                res += ';'
            }
            return res
        }
    }

    data class TimePowerData(var time: Int, var powerList: Array<Double>) {

        companion object {
            fun input(string: String): TimePowerData {
                var after = string
                var time0 = 0
                val powers = arrayListOf<Double>()
                for (i in 1..7) {
                    val before = after.substringBefore(',')
                    after = after.substringAfter(',')
                    if (i == 1) {
                        time0 = before.toInt()
                    } else {
                        powers.add(before.toDouble())
                    }
                }
                return TimePowerData(time0, powers.toTypedArray())
            }
        }

        fun output(): String {
            var res = time.toString()
            powerList.forEach {
                res += ','
                res += String.format("%.2f", it)
            }
            return res
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as TimePowerData

            if (time != other.time) return false
            if (!powerList.contentEquals(other.powerList)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = time
            result = 31 * result + powerList.contentHashCode()
            return result
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("create table if not exists main(id INTEGER primary key, version INTEGER, data BLOB)")
        db.execSQL("create table if not exists power(id INTEGER primary key, description TEXT, value TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

    fun getAllData(): Array<TimePowerDataList> {
        val dataList = ArrayList<TimePowerDataList>()
        val cursor = readableDatabase.query("power", null, null, null, null, null, null)
        while (cursor.moveToNext()) {
            val id = cursor.getInt(cursor.getColumnIndex("id"))
            val description = cursor.getString(cursor.getColumnIndex("description"))
            dataList.add(TimePowerDataList(arrayListOf(), id, description))
        }
        cursor.close()
        return dataList.toTypedArray()
    }

    fun getTargetData(): TimePowerDataList {
        if (Connector.autoRunningId == -1) {
            val cursor1 = readableDatabase.query("power", null, null, null, null, null, null)
            if (cursor1.count != 0) {
                cursor1.moveToLast()
                val lastId = cursor1.getInt(cursor1.getColumnIndex("id"))
                cursor1.close()
                Connector.autoRunningId = lastId
            } else {
                Connector.autoRunningId = -1
            }
        }

//        val args = arrayOf(Constant.SelectedDataId.toString())
        val args = arrayOf(Connector.autoRunningId.toString())
        val cursor = readableDatabase.query("power", null, "id=?", args, null, null, null)
        cursor.moveToFirst()
        val description = cursor.getString(cursor.getColumnIndex("description"))
        val str = cursor.getString(cursor.getColumnIndex("value"))
        debug("获取数据 = $str")
        val res = TimePowerDataList.input(str, description)
        cursor.close()
        return res
    }

    fun insertData(data: TimePowerDataList) {
        val string = data.output()
        val contentValues = ContentValues()
        contentValues.put("description", data.description)
        contentValues.put("value", string)
        debug("写入数据 = $string")
        writableDatabase.insert("power", null, contentValues)

        val cursor = readableDatabase.query("power", null, null, null, null, null, null)
        cursor.moveToLast()
        val id = cursor.getInt(cursor.getColumnIndex("id"))
        cursor.close()
        command("写入结束 id = $id")
        debug("写入结束 id = $id")
    }

    fun getTFData(): Pair<Int, Array<ByteBuffer>> {
        val buffers = ArrayList<ByteBuffer>()
        val cursor = readableDatabase.query("main", null, null, null, null, null, null)
        var minVersionCode = Int.MAX_VALUE
        while (cursor.moveToNext()) {
            val version = cursor.getInt(cursor.getColumnIndex("version"))
            minVersionCode = min(minVersionCode, version)
            val data = cursor.getBlob(cursor.getColumnIndex("data"))
            val buffer = ByteBuffer.wrap(data)
            buffers.add(buffer)
        }
        cursor.close()
        return Pair(minVersionCode, buffers.toTypedArray())
    }

    fun deleteTFAll() {
        readableDatabase.delete("main", null, null)
    }

    fun insertTFData(data: Pair<Int, Array<ByteBuffer>>) {
        data.second.forEach {
            val contentValues = ContentValues()
            contentValues.put("version", data.first)
            val bytes = ByteArray(it.capacity())
            it.rewind()
            it.get(bytes)
            contentValues.put("data", bytes)
            writableDatabase.insert("main", null, contentValues)
        }
    }

}