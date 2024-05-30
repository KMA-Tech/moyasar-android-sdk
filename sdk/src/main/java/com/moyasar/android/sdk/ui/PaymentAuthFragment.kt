package com.moyasar.android.sdk.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.annotation.RequiresApi
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.moyasar.android.sdk.MoyasarPaymentActivity
import com.moyasar.android.sdk.PaymentResult
import com.moyasar.android.sdk.R
import com.moyasar.android.sdk.data.GsonHolder
import com.moyasar.android.sdk.data.PaymentAuthFragmentViewModel
import com.moyasar.android.sdk.payment.models.Payment

@SuppressLint("SetJavaScriptEnabled")
class PaymentAuthFragment : Fragment() {

    private val viewModel: PaymentAuthFragmentViewModel by viewModels()

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private val authUrl: String? by lazy {
        viewModel.payment.getCardTransactionUrl()
    }

    private val webViewClient by lazy {

        object : WebViewClient() {
            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                return shouldOverrideUrlLoading(request?.url)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return shouldOverrideUrlLoading(if (url != null) Uri.parse(url) else null)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                progressBar.visibility = View.GONE
            }

            @RequiresApi(Build.VERSION_CODES.M)
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                onReceivedError(error?.description?.toString())
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                onReceivedError(description)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)

        val view = inflater.inflate(R.layout.fragment_payment_auth, container, false)

        webView = view.findViewById(R.id.webView)
        webView.settings.domStorageEnabled = true
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = webViewClient

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        progressBar = view.findViewById(R.id.circularProgressIndicator)
    }

    override fun onStart() {
        super.onStart()

        val url = authUrl

        if (url.isNullOrBlank()) {
            onPaymentResult(viewModel.getPaymentResult(AuthResult.Failed("Missing Payment 3DS Auth URL.")))
            return
        }

        webView.loadUrl(url)
    }

    private fun shouldOverrideUrlLoading(url: Uri?): Boolean {
        if (url?.host == RETURN_HOST) {
            val id = url.getQueryParameter("id") ?: ""
            val status = url.getQueryParameter("status") ?: ""
            val message = url.getQueryParameter("message") ?: ""

            onPaymentResult(viewModel.getPaymentResult(AuthResult.Completed(id, status, message)))

            return true;
        }

        return false;
    }

    private fun onReceivedError(error: String?) {
        onPaymentResult(viewModel.getPaymentResult(AuthResult.Failed(error)))
    }

    private fun onPaymentResult(result: PaymentResult) {
        (activity as? MoyasarPaymentActivity)?.onPaymentResult(result)
    }

    sealed class AuthResult {
        data class Completed(val id: String, val status: String, val message: String) : AuthResult()

        data class Failed(val error: String? = null) : AuthResult()

        data object Canceled : AuthResult()
    }

    companion object {
        const val RETURN_HOST = "sdk.moyasar.com";
        const val RETURN_URL = "https://${RETURN_HOST}/payment/return"

        internal const val ARG_PAYMENT = "arg-payment"

        fun newInstance(payment: Payment): PaymentAuthFragment {
            return PaymentAuthFragment().apply {
                arguments = bundleOf(ARG_PAYMENT to GsonHolder.gson.toJson(payment))
            }
        }

    }
}