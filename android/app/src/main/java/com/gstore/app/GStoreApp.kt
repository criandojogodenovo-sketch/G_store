package com.gstore.app

import android.app.Application
import com.gstore.app.data.local.SessionStore
import com.gstore.app.data.repo.GStoreRepository

class GStoreApp : Application() {

    lateinit var sessionStore: SessionStore
        private set

    lateinit var repository: GStoreRepository
        private set

    override fun onCreate() {
        super.onCreate()
        sessionStore = SessionStore(this)
        repository = GStoreRepository.get(this, sessionStore)
    }
}
