package edu.scut.submarinerobotcontroller.tools

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.SystemClock
import java.nio.ByteBuffer
import java.util.ArrayList
import kotlin.math.min

class MyDatabase(
    context: Context,
    name: String,
    factory: SQLiteDatabase.CursorFactory?,
    version: Int
) : SQLiteOpenHelper(context, name, factory, version) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "create table if not exists main(" +
                    "id INTEGER primary key," +
                    "version INTEGER," +
                    "data BLOB)"
        );
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

    fun getData(): Pair<Int, Array<ByteBuffer>> {
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

    fun deleteAll() {
        readableDatabase.delete("main", null, null)
    }

    fun insertData(data: Pair<Int, Array<ByteBuffer>>) {
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