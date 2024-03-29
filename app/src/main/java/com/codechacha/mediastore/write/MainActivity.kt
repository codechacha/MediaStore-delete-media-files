package com.codechacha.mediastore.write

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.FileOutputStream


class MainActivity : AppCompatActivity() {
    companion object {
        const val TAG = "MainActivity"
        private const val READ_EXTERNAL_STORAGE_REQUEST = 0x1045
        private const val DELETE_PERMISSION_REQUEST = 0x1033

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        requestPermission()

        addBtn.setOnClickListener {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                writeImageToMediaStore()
            }
        }
        addInAndroid10Btn.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                writeImageToMediaStoreInAndroid10()
            }
        }
        queryBtn.setOnClickListener {
            queryMediaStore()
        }
        removeBtn.setOnClickListener {
            removeMediaFile("my_image.jpg")
        }
    }

    private fun removeMediaFile(fileName: String) {
        GlobalScope.launch {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME
            )
            var displayName: String? = null
            var contentUri: Uri? = null
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val displayNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    displayName = cursor.getString(displayNameColumn)
                    if (displayName != fileName) {
                        continue
                    }
                    contentUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    contentUri?.let{
                        contentResolver.delete(contentUri!!, null, null)
                        Log.d(TAG, "Removed $displayName from MediaStore: $contentUri")
                    }
                } catch (e: RecoverableSecurityException) {
                    Log.d(TAG, "got RecoverableSecurityException, ")
                    val intentSender = e.userAction.actionIntent.intentSender
                    intentSender?.let {
                        startIntentSenderForResult(
                            intentSender,
                            DELETE_PERMISSION_REQUEST,
                            null,
                            0,
                            0,
                            0,
                            null
                        )
                    }
                }
            } else {
                contentUri?.let{
                    contentResolver.delete(contentUri!!, null, null)
                    Log.d(TAG, "Removed $displayName from MediaStore: $contentUri")
                }
            }
        }
    }

    private fun writeImageToMediaStore() {
        GlobalScope.launch {
            // copy a raw file to data folder
            val inputStream = resources.openRawResource(R.raw.my_image)
            val filePath = "$filesDir/my_image.jpg"
            val outputStream = FileOutputStream(filePath)
            while (true) {
                val data = inputStream.read()
                if (data == -1) {
                    break
                }
                outputStream.write(data)
            }
            inputStream.close()
            outputStream.close()

            val values= ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "my_image.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpg")
                put(MediaStore.Images.Media.DATA, filePath)
            }

            val item = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
            Log.d(TAG, "Inserted a file to Content provider")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeImageToMediaStoreInAndroid10() {
        GlobalScope.launch {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "my_image.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpg")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val collection = MediaStore.Images.Media
                .getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val item = contentResolver.insert(collection, values)!!

            contentResolver.openFileDescriptor(item, "w", null).use {
                FileOutputStream(it!!.fileDescriptor).use { outputStream ->
                    val imageInputStream = resources.openRawResource(R.raw.my_image)
                    while (true) {
                        val data = imageInputStream.read()
                        if (data == -1) {
                            break
                        }
                        outputStream.write(data)
                    }
                    imageInputStream.close()
                    outputStream.close()
                }
            }

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(item, values, null, null)
            Log.d(TAG, "Done inserting a file to Content provider")
        }
    }

    private fun queryMyImages() {
        GlobalScope.launch {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME
            )

            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val displayNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(displayNameColumn)
                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    if (displayName.startsWith("my_image")) {
                        Log.d(TAG, "Found id: $id, display_name: $displayName," +
                                " content_uri: $contentUri")
                    }
                }
            }
        }
    }

    private fun queryMediaStore() {
        if (haveStoragePermission()) {
            queryMyImages()
        } else {
            requestPermission()
        }
    }

    private fun goToSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }.also { intent ->
            startActivity(intent)
        }
    }

    private fun haveStoragePermission() =
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestPermission() {
        if (!haveStoragePermission()) {
            val permissions = arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            ActivityCompat.requestPermissions(this, permissions, READ_EXTERNAL_STORAGE_REQUEST)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        when (requestCode) {
            READ_EXTERNAL_STORAGE_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    queryMediaStore()
                } else {
                    val showRationale =
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            this,
                            Manifest.permission.READ_EXTERNAL_STORAGE
                        )

                    if (!showRationale) {
                        goToSettings()
                    }
                }
                return
            }
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK && requestCode == DELETE_PERMISSION_REQUEST) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                removeMediaFile("my_image.jpg")
            }
        }
    }
}
