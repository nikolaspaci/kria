package com.nikolaspaci.app.llamallmlocal.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nikolaspaci.app.llamallmlocal.data.database.AppDatabase
import com.nikolaspaci.app.llamallmlocal.data.repository.ChatRepository
import com.nikolaspaci.app.llamallmlocal.data.repository.ModelParameterRepository
import com.nikolaspaci.app.llamallmlocal.data.repository.ModelRepository
import com.nikolaspaci.app.llamallmlocal.engine.DefaultModelParameterProvider
import com.nikolaspaci.app.llamallmlocal.util.HardwareCapabilities
import com.nikolaspaci.app.llamallmlocal.util.OptimalConfigurationService

class ViewModelFactory(private val context: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val db = AppDatabase.getDatabase(context)
        val chatRepository = ChatRepository(db.chatDao())
        val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)


        return when {
            modelClass.isAssignableFrom(HistoryViewModel::class.java) -> {
                HistoryViewModel(chatRepository, sharedPreferences) as T
            }
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                val modelParameterRepository = ModelParameterRepository(db.modelParameterDao())
                val modelRepository = ModelRepository(db.modelDao())
                val hardwareCapabilities = HardwareCapabilities()
                val optimalConfigurationService = OptimalConfigurationService(context, hardwareCapabilities)
                val parameterProvider = DefaultModelParameterProvider(modelRepository, modelParameterRepository, chatRepository, optimalConfigurationService)
                HomeViewModel(chatRepository, parameterProvider) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
