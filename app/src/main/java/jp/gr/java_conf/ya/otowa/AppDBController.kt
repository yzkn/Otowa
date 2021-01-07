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

    fun searchCity(lat: Double, lng: Double): String? {

        var cityName: String = ""
        try {
            val selectQql: String =
                " SELECT * , ( abs ( ? - 緯度 ) + abs ( ? - 経度 ) ) as d FROM city ORDER BY d ASC LIMIT 1 ; "
            cursor =
                sqLiteDatabase?.rawQuery(selectQql, arrayOf(lat.toString(), lng.toString()))
                    ?: return null

            if (cursor?.moveToNext() == true) {
                cityName = cursor.getString(cursor.getColumnIndex("市区町村名")) ?: ""
            }
        }catch (e: Exception){
        } finally {
            if (::cursor.isInitialized) {
                cursor?.close()
            }
        }
        return cityName
    }

    fun close() {
        sqLiteDatabase?.close()
    }
}