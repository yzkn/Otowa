package jp.gr.java_conf.ya.otowa

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

open class AppDBController(context: Context) {
    private val sqLiteDatabase: SQLiteDatabase?

    init {
        val appAssetsSQLite = AppAssetsSQLite(context)
        sqLiteDatabase = appAssetsSQLite.readableDatabase
    }

    fun searchCity(lat: Double, lng: Double): String? {
        val selectQql: String = " SELECT * , ( abs ( ? - 緯度 ) + abs ( ? - 経度 ) ) as d FROM city ORDER BY d ASC LIMIT 1 ; "
        val cursor: Cursor? =
            sqLiteDatabase?.rawQuery(selectQql, arrayOf(lat.toString(), lng.toString()))
                ?: return null

        var cityName: String = ""
        try {
            if (cursor?.moveToNext() == true) {
                cityName = cursor.getString(cursor.getColumnIndex("市区町村名")) ?: ""
            }
        } finally {
            cursor?.close()
        }
        return cityName
    }

    fun close() {
        sqLiteDatabase?.close()
    }
}