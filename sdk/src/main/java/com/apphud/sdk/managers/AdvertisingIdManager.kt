package com.apphud.sdk.managers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import java.io.IOException
import java.util.concurrent.LinkedBlockingQueue

object AdvertisingIdManager {
    @Throws(Exception::class)
    fun getAdvertisingIdInfo(context: Context): AdInfo {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Cannot be called from the main thread" }
        try {
            val pm = context.packageManager
            pm.getPackageInfo("com.android.vending", 0)
        } catch (e: Exception) {
            throw e
        }
        val connection = AdvertisingConnection()
        val intent = Intent("com.google.android.gms.ads.identifier.service.START")
        intent.setPackage("com.google.android.gms")
        if (context.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            return try {
                val adInterface = AdvertisingInterface(connection.binder)
                AdInfo(adInterface.id, adInterface.isLimitAdTrackingEnabled(true))
            } catch (exception: Exception) {
                throw exception
            } finally {
                context.unbindService(connection)
            }
        }
        throw IOException("Google Play connection failed")
    }

    class AdInfo internal constructor(val id: String?, val isLimitAdTrackingEnabled: Boolean)

    private class AdvertisingConnection : ServiceConnection {
        var retrieved = false
        private val queue = LinkedBlockingQueue<IBinder>(1)

        override fun onServiceConnected(
            name: ComponentName,
            service: IBinder,
        ) {
            try {
                queue.put(service)
            } catch (localInterruptedException: InterruptedException) {
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {}

        @get:Throws(InterruptedException::class)
        val binder: IBinder
            get() {
                check(!retrieved)
                retrieved = true
                return queue.take() as IBinder
            }
    }

    private class AdvertisingInterface(private val binder: IBinder) : IInterface {
        override fun asBinder(): IBinder {
            return binder
        }

        @get:Throws(RemoteException::class)
        val id: String?
            get() {
                val data = Parcel.obtain()
                val reply = Parcel.obtain()
                val id: String? =
                    try {
                        data.writeInterfaceToken("com.google.android.gms.ads.identifier.internal.IAdvertisingIdService")
                        binder.transact(1, data, reply, 0)
                        reply.readException()
                        reply.readString()
                    } finally {
                        reply.recycle()
                        data.recycle()
                    }
                return id
            }

        @Throws(RemoteException::class)
        fun isLimitAdTrackingEnabled(paramBoolean: Boolean): Boolean {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            val limitAdTracking: Boolean =
                try {
                    data.writeInterfaceToken("com.google.android.gms.ads.identifier.internal.IAdvertisingIdService")
                    data.writeInt(if (paramBoolean) 1 else 0)
                    binder.transact(2, data, reply, 0)
                    reply.readException()
                    0 != reply.readInt()
                } finally {
                    reply.recycle()
                    data.recycle()
                }
            return limitAdTracking
        }
    }
}
