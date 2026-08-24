package br.com.nejmenn.sample.domain.repository

import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observe(): Flow<Account>

    suspend fun save(account: Account)
}

