package org.futo.voiceinput

import android.app.Service
import android.content.Intent
import android.os.IBinder

class DummyService : Service() {
    override fun onBind(p0: Intent): IBinder? {
        return null
    }
}
