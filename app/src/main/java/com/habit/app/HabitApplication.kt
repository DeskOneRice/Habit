package com.habit.app

import android.app.Application
import com.habit.app.di.AppContainer

class HabitApplication : Application() {
    val container: AppContainer by lazy { AppContainer(applicationContext) }
}
