package jp.gr.java_conf.ya.otowa

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.preference.PreferenceManager
import java.io.*
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*


class IoUtil(context: Context) {
    private var baseDirectory: File?
    private var isDebugMode = false
    private var isDebugModeLoop = false
    private var packageNameString = ""

    fun saveExternalPrivateTextFile(logString: String?, mode: Boolean) {
        if (isDebugMode && isDebugModeLoop) {
            Log.v(packageNameString, "saveExternalPrivateTextFile()")
        }
        if (checkIfExternalStorageIsWritable()) {
            try {
                // 現在時刻から時単位のファイル名を作成
                val date = Date()
                val sdfFyyyyMMddHH = SimpleDateFormat("yyyyMMddHH", Locale.JAPAN)
                val dateString = sdfFyyyyMMddHH.format(date)
                val filename = "Route$dateString.csv"
                val saveFile = File(baseDirectory, filename)
                if (isDebugMode && isDebugModeLoop) {
                    Log.v(packageNameString, "saveExternalPrivateTextFile() filename:$filename")
                }

                FileOutputStream(saveFile, mode).use { fileOutputStream ->
                    OutputStreamWriter(
                        fileOutputStream,
                        StandardCharsets.UTF_8
                    ).use { outputStreamWriter ->
                        BufferedWriter(outputStreamWriter).use { bw ->
                            bw.write(logString)
                            bw.flush()
                        }
                    }
                }
            } catch (e: Exception) {
                if (isDebugMode) {
                    Log.e(packageNameString, "saveExternalPrivateTextFile() $e")
                }
            }
        }
    }

    fun listExternalPrivateTextFiles(): List<File> {
        if (checkIfExternalStorageIsReadable()) {
            try {
                val files = baseDirectory?.listFiles()
                if (files != null) {
                    Arrays.sort(files)
                    return files.toList()
                }
            } catch (e: Exception) {
                if (isDebugMode) {
                    Log.e(packageNameString, "saveExternalPrivateTextFile() $e")
                }
            }
        }
        return arrayListOf()
    }

    fun readExternalPrivateTextFile(filename: String): List<String> {
        val rowList = mutableListOf<String>()

        if (checkIfExternalStorageIsReadable()) {
            try {
                val readFile = File(baseDirectory, filename)
                FileInputStream(readFile).bufferedReader().use {
                    var row: String?
                    while (true) {
                        row = it.readLine()
                        if (row != null) {
                            rowList.add(row)
                        } else {
                            break
                        }
                    }
                }

                return rowList
            } catch (e: java.lang.Exception) {
                if (isDebugMode) {
                    Log.e(packageNameString, "readExternalPrivateTextFile() $e")
                }
            }
        }

        return mutableListOf()
    }

    private fun checkIfExternalStorageIsWritable(): Boolean {
        return try {
            val state = Environment.getExternalStorageState()
            Environment.MEDIA_MOUNTED == state
        } catch (e: java.lang.Exception) {
            false
        }
    }

    private fun checkIfExternalStorageIsReadable(): Boolean {
        return try {
            val state = Environment.getExternalStorageState()
            Environment.MEDIA_MOUNTED == state || Environment.MEDIA_MOUNTED_READ_ONLY == state
        } catch (e: java.lang.Exception) {
            false
        }
    }

    init {
        packageNameString = context.packageName.toString()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        isDebugMode = sharedPreferences.getBoolean("pref_is_debug_mode", false)
        isDebugModeLoop = sharedPreferences.getBoolean("pref_is_debug_mode_loop", false)

        baseDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (isDebugMode) {
            Log.v(packageNameString, "init() baseDirectory:$baseDirectory")
        }
    }
}