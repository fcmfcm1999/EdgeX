package com.fan.edgex.license

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import com.fan.edgex.IKeystoreVerifier

class KeystoreVerifierService : Service() {

    private val stub = object : IKeystoreVerifier.Stub() {
        override fun sign(challenge: ByteArray?): ByteArray? {
            if (Binder.getCallingUid() != Process.SYSTEM_UID) return null
            if (challenge == null || challenge.size < 16) return null
            return runCatching { DeviceKeystore.sign(challenge) }.getOrNull()
        }
    }

    override fun onBind(intent: Intent?): IBinder = stub
}
