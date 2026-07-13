package com.github.mytv

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.multidex.MultiDex
import com.github.mytv.models.MyViewModel

class MyTVApplication : Application() {

    lateinit var myViewModel: MyViewModel

    override fun onCreate() {
        super.onCreate()
        // 预加载 ijk 原生库（失败不抛出，运行时按需降级）
        IjkPlayerEngine.loadLibrariesOnce()
        myViewModel = ViewModelProvider(
            ViewModelStore(),
            ViewModelProvider.NewInstanceFactory()
        )[MyViewModel::class.java]
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(base)
    }
}
