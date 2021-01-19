package jp.gr.java_conf.ya.otowa

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.lang.Exception

open class AppDBController(context: Context) {

    lateinit var cursor: Cursor

    private val sqLiteDatabase: SQLiteDatabase?

    init {
        val appAssetsSQLite = AppAssetsSQLite(context)
        sqLiteDatabase = appAssetsSQLite.writableDatabase
    }

    fun searchCity(lat: Double, lng: Double): Pair<String, String>? {
        var cityName = ""
        var townName = ""
        try {
            val selectQql =
                " SELECT * , ( abs ( ? - 緯度 ) + abs ( ? - 経度 ) ) as d FROM town ORDER BY d ASC LIMIT 1 ; "
            cursor =
                sqLiteDatabase?.rawQuery(selectQql, arrayOf(lat.toString(), lng.toString()))
                    ?: return null

            if (cursor.moveToNext()) {
                cityName = cursor.getString(cursor.getColumnIndex("市区町村名")) ?: ""
                townName = cursor.getString(cursor.getColumnIndex("大字町丁目名")) ?: ""
            }
        }catch (e: Exception){
        } finally {
            if (::cursor.isInitialized) {
                cursor.close()
            }
        }
        return Pair(cityName, townName)
    }

    fun close() {
        sqLiteDatabase?.close()
    }
}