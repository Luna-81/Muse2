package com.blue.hush.ui.state

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 权限检查工具。
 *
 * 注意：请求权限必须由 Activity 做（registerForActivityResult）。
 * 这个类只负责"检查"和"列出需要的权限"。
 */
class PermissionManager(private val context: Context) {

    fun hasBluetoothPermission(): Boolean =
        requiredBluetoothPermissions().all(::hasPermission)

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun requiredBluetoothPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun requiredRequestPermissions(): Array<String> = buildList {
        addAll(requiredBluetoothPermissions())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.toTypedArray()
}