package com.example.unknotexampleapp

import android.app.Activity
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import org.unknot.android_sdk.ForwardLocation
import org.unknot.android_sdk.IUnknotService
import org.unknot.android_sdk.IUnknotServiceCallback
import org.unknot.android_sdk.ServiceState
import org.unknot.android_sdk.UnknotService

interface UnknotServiceCallback {
    fun onUpdateServiceState(state: ServiceState)
    fun onBound()
    fun onUnbound()
    fun onBatchUpdate(count: Int, total: Int)
    fun onLocation(location: ForwardLocation)
}

class UnknotServiceConnection(
    private val callback: UnknotServiceCallback
) : ServiceConnection {
    private var remoteService: IUnknotService? = null
    var bound = false
        private set

    fun bind(context: Context) {
        Intent(context, UnknotService::class.java).also {
            context.bindService(it, this, Context.BIND_AUTO_CREATE)
        }
        bound = true
    }

    fun unbind(context: Context) {
        if (bound) {
            context.unbindService(this)
            bound = false
        }
    }

    fun autoBind(activity: Activity) = autoBind(activity.application)

    fun autoBind(application: Application) {
        application.registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            }

            override fun onActivityStarted(activity: Activity) {
                bind(activity)
            }

            override fun onActivityResumed(activity: Activity) {
            }

            override fun onActivityPaused(activity: Activity) {
            }

            override fun onActivityStopped(activity: Activity) {
                unbind(activity)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
            }

            override fun onActivityDestroyed(activity: Activity) {
            }

        })
    }

    private val serviceCallback = object : IUnknotServiceCallback.Stub() {
        override fun fromService(state: ServiceState?) {
            state?.let { callback.onUpdateServiceState(it) }
        }

        override fun dbCountUpdate(count: Int, total: Int) {
            callback.onBatchUpdate(count, total)
        }

        override fun videoUploadProgress(status: Int, bytesUploaded: Long, bytesTotal: Long) {
        }

        override fun receiveLocation(location: ForwardLocation?) {
            if (location != null) {
                callback.onLocation(location)
            }
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        remoteService = IUnknotService.Stub.asInterface(service).also {
            it.registerCallback(serviceCallback)

            callback.onUpdateServiceState(it.serviceState)
        }
        //bound = true
        callback.onBound()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        remoteService = null
        //bound = false
        callback.onUnbound()
    }
}