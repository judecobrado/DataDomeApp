package com.example.datadomeapp.utils

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.isVisible
import com.example.datadomeapp.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar

class InternetAlertHelper(private val activity: Activity) {

    private var alertView: View? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isAlertShowing = false
    private var retryAction: (() -> Unit)? = null

    interface InternetAlertListener {
        fun onInternetConnected()
        fun onInternetDisconnected()
    }

    private var listener: InternetAlertListener? = null

    fun setListener(listener: InternetAlertListener) {
        this.listener = listener
    }

    fun setRetryAction(action: () -> Unit) {
        this.retryAction = action
    }

    fun initialize() {
        setupNetworkMonitoring()
        createAlertView()
    }

    private fun createAlertView() {
        // Inflate the floating no internet layout
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        alertView = LayoutInflater.from(activity).inflate(R.layout.layout_floating_no_internet, rootView, false)

        // Set up retry button
        alertView?.findViewById<Button>(R.id.btnRetry)?.setOnClickListener {
            retryAction?.invoke()
            checkInternetAndHide()
        }

        // Add to activity root and hide initially
        rootView.addView(alertView)
        alertView?.visibility = View.GONE
    }

    private fun setupNetworkMonitoring() {
        connectivityManager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                activity.runOnUiThread {
                    hideAlert()
                    listener?.onInternetConnected()
                    showConnectedSnackbar()
                }
            }

            override fun onLost(network: Network) {
                activity.runOnUiThread {
                    showAlert()
                    listener?.onInternetDisconnected()
                }
            }
        }

        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    fun showAlert() {
        if (!isAlertShowing) {
            alertView?.visibility = View.VISIBLE
            isAlertShowing = true
        }
    }

    fun hideAlert() {
        if (isAlertShowing) {
            alertView?.visibility = View.GONE
            isAlertShowing = false
        }
    }

    private fun showConnectedSnackbar() {
        val snackbar = Snackbar.make(
            activity.findViewById(android.R.id.content),
            "Internet connection restored",
            Snackbar.LENGTH_SHORT
        )
        snackbar.setBackgroundTint(activity.getColor(R.color.green_success)) // Add this color to your colors.xml
        snackbar.show()
    }

    fun checkInternetAndHide(): Boolean {
        return if (isInternetAvailable()) {
            hideAlert()
            true
        } else {
            showAlert()
            false
        }
    }

    fun isInternetAvailable(): Boolean {
        val connectivityManager = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

    fun destroy() {
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
        }
        alertView?.let {
            val parent = it.parent as? ViewGroup
            parent?.removeView(it)
        }
    }
}