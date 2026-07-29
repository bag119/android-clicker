package com.creation.city.clicker

import android.view.accessibility.AccessibilityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.accessibilityservice.AccessibilityServiceInfo
import android.widget.*

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnOverlay = findViewById<Button>(R.id.btnOverlay)
        val btnAccess = findViewById<Button>(R.id.btnAccess)
        val btnOpen = findViewById<Button>(R.id.btnOpen)
        val btnSave = findViewById<Button>(R.id.btnSave)
        val edtName = findViewById<EditText>(R.id.edtName)
        val listView = findViewById<ListView>(R.id.listScripts)

        btnOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val i = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(i)
            } else {
                toast("悬浮窗权限已授予")
            }
        }

        btnAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnOpen.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                toast("请先授予悬浮窗权限"); return@setOnClickListener
            }
            if (!isAccessibilityEnabled()) {
                toast("请先开启无障碍服务"); return@setOnClickListener
            }
            startForegroundService(Intent(this, FloatingControlService::class.java))
            toast("控制器已启动，看手机上的悬浮面板")
        }

        btnSave.setOnClickListener {
            val name = edtName.text.toString().trim()
            if (name.isEmpty()) { toast("请输入脚本名"); return@setOnClickListener }
            val i = Intent(this, FloatingControlService::class.java).apply {
                action = FloatingControlService.ACTION_SAVE
                putExtra(FloatingControlService.EXTRA_NAME, name)
            }
            startForegroundService(i)
            toast("已保存：$name")
            refreshList(listView)
        }

        listView.setOnItemClickListener { _, _, pos, _ ->
            val name = listView.getItemAtPosition(pos) as String
            val i = Intent(this, FloatingControlService::class.java).apply {
                action = FloatingControlService.ACTION_LOAD
                putExtra(FloatingControlService.EXTRA_NAME, name)
            }
            startForegroundService(i)
            toast("已载入：$name")
        }

        listView.setOnItemLongClickListener { _, _, pos, _ ->
            val name = listView.getItemAtPosition(pos) as String
            ScriptStore.deleteScript(this, name)
            refreshList(listView)
            toast("已删除：$name")
            true
        }

        refreshList(listView)
    }

    private fun refreshList(lv: ListView) {
        val list = ScriptStore.listScripts(this).toTypedArray()
        lv.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, list)
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val services = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return services.any {
            it.resolveInfo.serviceInfo.packageName == packageName &&
                    it.resolveInfo.serviceInfo.name.endsWith("AutoClickerAccessibilityService")
        }
    }

    private fun toast(s: String) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    }
}
