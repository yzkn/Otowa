package jp.gr.java_conf.ya.otowa

import android.content.Context
import com.abnerescocio.assetssqlite.lib.AssetsSQLite

class AppAssetsSQLite(context: Context): AssetsSQLite(context, DATABASE_NAME) {
    companion object {
        const val DATABASE_NAME = "database.db"
    }
}