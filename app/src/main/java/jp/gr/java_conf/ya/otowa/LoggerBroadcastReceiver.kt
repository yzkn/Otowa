package jp.gr.java_conf.ya.otowa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class LoggerBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val targetIntent = Intent(context, LoggerService::class.java)
        //TODO
        Toast.makeText(
            context,
            context.getString(R.string.tweet),
            Toast.LENGTH_LONG
        ).show()

        //TODO REMOVE
        // context.stopService(targetIntent)
    }
}