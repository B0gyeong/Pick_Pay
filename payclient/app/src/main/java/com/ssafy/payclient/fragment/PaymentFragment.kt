package com.ssafy.payclient.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.ssafy.payclient.MainActivity
import com.ssafy.payclient.R
import com.ssafy.payclient.data.local.PersonalCartStore
import com.ssafy.payclient.data.local.TokenManager
import com.ssafy.payclient.data.model.PaymentCompleteRequest
import com.ssafy.payclient.data.network.RetrofitClient
import com.ssafy.payclient.databinding.FragmentPaymentBinding
import com.tosspayments.paymentsdk.PaymentWidget
import com.tosspayments.paymentsdk.model.PaymentCallback
import com.tosspayments.paymentsdk.model.TossPaymentResult
import com.tosspayments.paymentsdk.view.PaymentMethod
import kotlinx.coroutines.launch

class PaymentFragment : Fragment() {

    private var _binding: FragmentPaymentBinding? = null
    private val binding get() = _binding!!

    private val orderId: String by lazy { requireArguments().getString(ARG_ORDER_ID).orEmpty() }
    private val orderName: String by lazy { requireArguments().getString(ARG_ORDER_NAME).orEmpty() }
    private val totalPrice: Long by lazy { requireArguments().getLong(ARG_TOTAL_PRICE) }

    private lateinit var paymentWidget: PaymentWidget
    private var isPaymentCompleting = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPaymentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupPaymentInfo()
        setupClickListeners()
        setupPaymentWidget()
    }

    private fun setupToolbar() {
        binding.toolbarPayment.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun setupPaymentInfo() {
        binding.tvPaymentAmount.text = "$totalPrice KRW"
        binding.btnPayment.isEnabled = false
    }

    private fun setupClickListeners() {
        binding.btnPayment.setOnClickListener {
            requestPayment()
        }
    }

    private fun setupPaymentWidget() {
        paymentWidget = (requireActivity() as MainActivity).paymentWidget

        try {
            paymentWidget.renderPaymentMethods(
                binding.paymentMethod,
                totalPrice
            )
            binding.btnPayment.isEnabled = true
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Failed to render payment methods: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun requestPayment() {
        if (isPaymentCompleting) return

        try {
            binding.btnPayment.isEnabled = false
            binding.tvPaymentDescription.text = "Opening the payment window..."
            paymentWidget.requestPayment(
                PaymentMethod.PaymentInfo(
                    orderId = orderId,
                    orderName = orderName.ifBlank { "Individual order" }
                ),
                object : PaymentCallback {
                    override fun onPaymentSuccess(success: TossPaymentResult.Success) {
                        completePayment(success)
                    }

                    override fun onPaymentFailed(fail: TossPaymentResult.Fail) {
                        Toast.makeText(
                            requireContext(),
                            fail.errorMessage ?: "Payment failed.",
                            Toast.LENGTH_SHORT
                        ).show()
                        resetPaymentButton()
                    }
                }
            )
        } catch (e: Exception) {
            Toast.makeText(
                requireContext(),
                "Payment request error: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
            resetPaymentButton()
        }
    }

    private fun completePayment(success: TossPaymentResult.Success) {
        completePayment(
            paymentKey = success.paymentKey,
            approvedOrderId = success.orderId,
            amount = success.amount.toLong()
        )
    }

    private fun completePayment(paymentKey: String, approvedOrderId: String, amount: Long) {
        if (isPaymentCompleting) return
        isPaymentCompleting = true
        binding.btnPayment.isEnabled = false
        binding.progressPayment.visibility = View.VISIBLE
        binding.tvPaymentDescription.text = "Approving your payment securely..."

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val tokenManager = TokenManager(requireContext().applicationContext)
                val apiService = RetrofitClient.getPaymentApiService(tokenManager)
                val request = PaymentCompleteRequest(
                    paymentKey = paymentKey,
                    orderId = approvedOrderId,
                    amount = amount
                )
                val response = apiService.completePayment(request)

                if (response.isSuccessful) {
                    val body = response.body()
                    val receiptOrderId = body?.orderId ?: approvedOrderId
                    PersonalCartStore.clear()
                    Toast.makeText(
                        requireContext(),
                        body?.message ?: "Payment completed.",
                        Toast.LENGTH_SHORT
                    ).show()
                    navigateToReceipt(receiptOrderId)
                } else {
                    Toast.makeText(
                        requireContext(),
                        "Payment approval failed. (${response.code()})",
                        Toast.LENGTH_SHORT
                    ).show()
                    resetPaymentButton()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    "Payment approval error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                resetPaymentButton()
            }
        }
    }

    private fun resetPaymentButton() {
        isPaymentCompleting = false
        _binding?.btnPayment?.isEnabled = true
        _binding?.progressPayment?.visibility = View.GONE
        _binding?.tvPaymentDescription?.text = "Select a payment method, then press Pay."
    }

    private fun navigateToReceipt(receiptOrderId: String) {
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.fragment_home, false)
            .build()
        findNavController().navigate(
            R.id.fragment_receipt,
            Bundle().apply {
                putString(ReceiptFragment.ARG_ORDER_ID, receiptOrderId)
            },
            navOptions
        )
    }

    override fun onResume() {
        super.onResume()
        activity?.findViewById<View>(R.id.bottom_navigation)?.visibility = View.GONE
    }

    override fun onStop() {
        super.onStop()
        activity?.findViewById<View>(R.id.bottom_navigation)?.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_ORDER_ID = "ORDER_ID"
        const val ARG_TOTAL_PRICE = "TOTAL_PRICE"
        const val ARG_ORDER_NAME = "ORDER_NAME"
    }
}
