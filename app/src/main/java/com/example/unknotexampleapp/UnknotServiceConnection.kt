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
import android.os.RemoteException
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import org.unknot.android_sdk.ForwardLocation
import org.unknot.android_sdk.GroundTruthAlignmentStatus
import org.unknot.android_sdk.GroundTruthCalibrationSnapshot
import org.unknot.android_sdk.GtPoint
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

    private var bindingRequested = false
    private var bindingContext: Context? = null
    private var lifecycleObserver: LifecycleObserver? = null
    private var bindingLifecycle: Lifecycle? = null

    fun bind(context: Context) {
        if (bindingRequested) return
        bindingRequested = true
        bindingContext = context
        try {
            context.bindService(Intent(context, UnknotService::class.java), this, Context.BIND_AUTO_CREATE)
        } catch (e: RuntimeException) {
            bindingRequested = false
            bindingContext = null
            throw e
        }
    }

    fun unbind(context: Context) {
        if (!bindingRequested) return
        val boundContext = bindingContext ?: context
        bindingRequested = false
        bindingContext = null
        unregisterServiceCallback()
        try {
            boundContext.unbindService(this)
        } finally {
            clearConnection()
        }
    }

    fun registerBindingOnLifecycle(appContext: Application, lifecycle: Lifecycle) {
        if (lifecycleObserver != null) {
            Log.w("UnknotServiceConnection", "lifecycle observer already registered")
            return
        }
        check(lifecycle.currentState != Lifecycle.State.DESTROYED) {
            "Cannot bind to a destroyed lifecycle"
        }

        lifecycleObserver = object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                bind(appContext)
            }

            override fun onStop(owner: LifecycleOwner) {
                unbind(appContext)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                unregisterBindingOnLifecycle(owner.lifecycle)
            }
        }.also {
            bindingLifecycle = lifecycle
            lifecycle.addObserver(it)
        }
    }

    fun unregisterBindingOnLifecycle(lifecycle: Lifecycle) {
        lifecycleObserver?.let {
            if (bindingLifecycle !== lifecycle) {
                Log.w("UnknotServiceConnection", "different lifecycle registered")
                return
            }
            bindingLifecycle = null
            lifecycleObserver = null
            lifecycle.removeObserver(it)
            bindingContext?.let(::unbind)
        } ?: Log.w("UnknotServiceConnection", "lifecycle observer not registered")
    }

    @Deprecated("Use [registerBindingOnLifecycle] instead.")
    fun autoBind(activity: Activity) = autoBind(activity.application)

    @Deprecated("Use [registerBindingOnLifecycle] instead.")
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

        override fun receiveLocation(location: ForwardLocation?) {
            if (location != null) {
                callback.onLocation(location)
            }
        }

        /* internal functions, do not use */
        override fun videoUploadProgress(status: Int, bytesUploaded: Long, bytesTotal: Long) { }
        override fun receiveGroundTruth(gtpoints: List<GtPoint>) { }
        override fun receiveGroundTruthAlignmentStatus(status: GroundTruthAlignmentStatus?) { }
        override fun receiveGroundTruthCalibrationSnapshot(snapshot: GroundTruthCalibrationSnapshot?) { }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        remoteService = IUnknotService.Stub.asInterface(service).also {
            it.registerCallback(serviceCallback)

            callback.onUpdateServiceState(it.serviceState)
        }
        bound = true
        callback.onBound()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        // Android retains the binding and may reconnect it. The old binder is no longer usable.
        clearConnection()
    }

    private fun unregisterServiceCallback() {
        try {
            remoteService?.unregisterCallback(serviceCallback)
        } catch (e: RemoteException) {
            Log.w("UnknotServiceConnection", "unable to unregister callback", e)
        }
    }

    private fun clearConnection() {
        if (!bound && remoteService == null) return
        remoteService = null
        bound = false
        callback.onUnbound()
    }

    fun getServiceState(): ServiceState? =
        remoteService?.serviceState
}
