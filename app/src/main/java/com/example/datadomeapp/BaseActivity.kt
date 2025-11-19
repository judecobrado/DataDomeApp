package com.example.datadomeapp

import androidx.appcompat.app.AppCompatActivity
import android.widget.ProgressBar
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import com.example.datadomeapp.R
import com.google.android.material.snackbar.Snackbar

open class BaseActivity : AppCompatActivity() {

    private var noInternetView: View? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    // I-override ito sa bawat activity
    protected open fun getLoadingProgressBar(): ProgressBar? = null

    fun showLoading() {
        getLoadingProgressBar()?.visibility = View.VISIBLE
    }

    fun hideLoading() {
        getLoadingProgressBar()?.visibility = View.GONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupInternetMonitoring()
    }

    private fun setupInternetMonitoring() {
        createNoInternetView()
        setupNetworkCallback()
    }

    private fun createNoInternetView() {
        val rootView = findViewById<ViewGroup>(android.R.id.content)
        noInternetView = LayoutInflater.from(this).inflate(R.layout.layout_global_no_internet, rootView, false)

        noInternetView?.findViewById<Button>(R.id.btnRetryGlobal)?.setOnClickListener {
            checkInternetConnection()
        }

        rootView.addView(noInternetView)
        noInternetView?.visibility = View.GONE
    }

    private fun setupNetworkCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    hideNoInternet()
                    onInternetConnected()
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread {
                    showNoInternet()
                    onInternetDisconnected()
                }
            }
        }

        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
    }

    // Pwede i-override ng child classes kung kailangan ng specific actions
    protected open fun onInternetConnected() {
        // Default implementation - empty
    }

    protected open fun onInternetDisconnected() {
        // Default implementation - empty
    }

    fun showNoInternet() {
        noInternetView?.visibility = View.VISIBLE
    }

    fun hideNoInternet() {
        noInternetView?.visibility = View.GONE
    }

    fun checkInternetConnection(): Boolean {
        return isInternetAvailable().also { hasInternet ->
            if (hasInternet) hideNoInternet() else showNoInternet()
        }
    }

    fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
        }
    }
}