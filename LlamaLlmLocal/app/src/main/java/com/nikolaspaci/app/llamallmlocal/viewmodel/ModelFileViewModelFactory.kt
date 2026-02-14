package com.nikolaspaci.app.llamallmlocal.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ModelFileViewModelFactory(
    private val context: Context,
    private val sharedPreferences: SharedPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ModelFileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ModelFileViewModel(context, sharedPreferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
