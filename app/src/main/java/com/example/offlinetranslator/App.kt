package com.example.offlinetranslator

import android.app.Application
import androidx.room.Room
import com.example.offlinetranslator.data.AppDatabase
import com.example.offlinetranslator.di.AppContainer

class App : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "offline_translator.db")
            .fallbackToDestructiveMigration()
            .build()
        container = AppContainer(this, db.historyDao())
        // 启动期可延迟预加载引擎（这里立即触发以便扫描模型）
        // 注意：模型加载是 IO 密集型，放到 IO 线程。
        Thread {
            container.preload()
        }.start()
    }
}
