package com.zz.animation

import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val airflowView = findViewById<AirflowPathView>(R.id.airflowView)
        // View 内部已处理生命周期自动启动，这里也可以手动调整参数
//        airflowView.setAnimationDuration(1800L)
        airflowView.setPathColor(Color.parseColor("#8EEFFF"))
         airflowView.startAnimation()
    }
}