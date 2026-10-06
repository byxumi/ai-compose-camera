package com.aicompose.camera

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.aicompose.camera.camera.CameraFragment
import com.aicompose.camera.vip.VipManager
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private var currentFragment: Fragment = CameraFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 会员状态（全免费）
        findViewById<TextView>(R.id.vipBadge).text = "★ ${VipManager.vipLevel} · ${VipManager.vipExpire} · 全部功能已解锁"
        findViewById<TextView>(R.id.featureList).text =
            VipManager.unlockedFeatures.joinToString("\n") { "✓ $it" }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, CameraFragment())
                .commit()
        }

        findViewById<MaterialButton>(R.id.btnOpenFeatures).setOnClickListener {
            // 免费版直接展示解锁列表，无任何付费弹窗
            findViewById<TextView>(R.id.featureList).visibility =
                if (findViewById<TextView>(R.id.featureList).visibility == android.view.View.VISIBLE)
                    android.view.View.GONE else android.view.View.VISIBLE
        }
    }
}
