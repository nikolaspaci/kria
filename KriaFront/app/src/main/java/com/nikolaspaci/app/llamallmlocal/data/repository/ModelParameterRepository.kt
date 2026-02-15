package com.nikolaspaci.app.llamallmlocal.data.repository

import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameter
import com.nikolaspaci.app.llamallmlocal.data.database.ModelParameterDao

class ModelParameterRepository(private val modelParameterDao: ModelParameterDao) {

    suspend fun getById(id: Long): ModelParameter? {
        return modelParameterDao.getModelParameterById(id)
    }

    suspend fun insert(modelParameter: ModelParameter): Long {
        return modelParameterDao.insert(modelParameter)
    }

    suspend fun update(modelParameter: ModelParameter) {
        modelParameterDao.update(modelParameter)
    }

    suspend fun duplicate(source: ModelParameter): Long {
        val copy = source.copy(id = 0)
        return modelParameterDao.insert(copy)
    }
}
