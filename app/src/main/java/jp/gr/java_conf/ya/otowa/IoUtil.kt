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
    private val saveFile: File
    private var isDebugMode = false
    private var packageNameString = ""

    private var stringBuffer: StringBuffer? = null

    fun clearExternalPrivateTextFile() {
        saveExternalPrivateTextFile("", false)
        stringBuffer!!.setLength(0)
    }

    fun saveExternalPrivateTextFile(logString: String?, mode: Boolean) {
        if (isDebugMode) {
            Log.v(packageNameString, "saveExternalPrivateTextFile()")
        }
        if (checkIfExternalStorageIsWritable()) {
            try {
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

    fun listExternalPrivateTextFiles(): List<File>{
        if (checkIfExternalStorageIsReadable()) {
            try {
                val files = baseDirectory?.listFiles()
                if (files != null) {
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

    fun readExternalPrivateTextFile(filename: String): String? {
        stringBuffer = StringBuffer()

        if (stringBuffer != null) {
            if (checkIfExternalStorageIsReadable()) {
                try {
                    val readFile = File(baseDirectory, filename)
                    FileInputStream(readFile).use { fileInputStream ->
                        InputStreamReader(
                            fileInputStream,
                            StandardCharsets.UTF_8
                        ).use { inputStreamReader ->
                            BufferedReader(inputStreamReader).use { reader ->
                                var lineBuffer = ""
                                while (reader.readLine().also { lineBuffer = it } != null) {
                                    stringBuffer!!.append(lineBuffer)
                                    stringBuffer!!.append(System.getProperty("line.separator"))
                                }
                            }
                        }
                    }
                } catch (e: java.lang.Exception) {
                    if (isDebugMode) {
                        Log.e(packageNameString, "readExternalPrivateTextFile() $e")
                    }
                }
            }

            return stringBuffer.toString()
        }
        return ""
    }

    private fun checkIfExternalStorageIsWritable(): Boolean {
        return try {
            val state = Environment.getExternalStorageState()
            Environment.MEDIA_MOUNTED == state
        }catch (e: java.lang.Exception){
            false
        }
    }

    private fun checkIfExternalStorageIsReadable(): Boolean {
        return try {
            val state = Environment.getExternalStorageState()
            Environment.MEDIA_MOUNTED == state || Environment.MEDIA_MOUNTED_READ_ONLY == state
        }catch (e: java.lang.Exception){
            false
        }
    }

    init {
        packageNameString = context.packageName.toString()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        isDebugMode = sharedPreferences.getBoolean("pref_is_debug_mode", false)

        baseDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (isDebugMode) {
            Log.v(packageNameString, "init() baseDirectory:$baseDirectory")
        }

        // 起動時の現在時刻
        val date = Date()
        val sdfFyyyyMMddHHmmss = SimpleDateFormat("yyyyMMddHHmmss", Locale.JAPAN)
        val dateString = sdfFyyyyMMddHHmmss.format(date)
        val filename = "Route$dateString.csv"

        saveFile = File(baseDirectory, filename)
        if (isDebugMode) {
            Log.v(packageNameString, "init() filename:$filename")
        }
    }
}