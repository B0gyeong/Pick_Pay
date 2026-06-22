package com.ssafy.payclient.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.ssafy.payclient.R
import com.ssafy.payclient.data.local.TokenManager
import com.ssafy.payclient.data.model.IndividualOrderReceiptResponseDTO
import com.ssafy.payclient.data.model.OrderItemDTO
import com.ssafy.payclient.data.network.RetrofitClient
import com.ssafy.payclient.databinding.FragmentReceiptBinding
import kotlinx.coroutines.launch

class ReceiptFragment : Fragment() {

    private var _binding: FragmentReceiptBinding? = null
    private val binding get() = _binding!!

    private val orderId: String by lazy { requireArguments().getString(ARG_ORDER_ID).orEmpty() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentReceiptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        binding.btnRetry.setOnClickListener { loadReceipt() }
        loadReceipt()
    }

    private fun setupToolbar() {
        binding.toolbarReceipt.setNavigationOnClickListener {
            findNavController().popBackStack(R.id.fragment_home, false)
        }
    }

    private fun loadReceipt() {
        if (orderId.isBlank()) {
            showError("Order id is missing.")
            return
        }

        showLoading()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val tokenManager = TokenManager(requireContext().applicationContext)
                val apiService = RetrofitClient.getIndividualOrderApiService(tokenManager)
                val response = apiService.getReceipt(orderId)

                if (response.isSuccessful) {
                    val receipt = response.body()
                    if (receipt == null) {
                        showError("Receipt response is empty.")
                    } else {
                        renderReceipt(receipt)
                    }
                } else {
                    showError("Failed to load receipt. (${response.code()})")
                }
            } catch (e: Exception) {
                showError("Receipt load error: ${e.message}")
            }
        }
    }

    private fun showLoading() {
        binding.progressReceipt.visibility = View.VISIBLE
        binding.layoutReceiptContent.visibility = View.GONE
        binding.layoutReceiptError.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.progressReceipt.visibility = View.GONE
        binding.layoutReceiptContent.visibility = View.GONE
        binding.layoutReceiptError.visibility = View.VISIBLE
        binding.tvReceiptError.text = message
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    private fun renderReceipt(receipt: IndividualOrderReceiptResponseDTO) {
        binding.progressReceipt.visibility = View.GONE
        binding.layoutReceiptError.visibility = View.GONE
        binding.layoutReceiptContent.visibility = View.VISIBLE

        binding.tvReceiptOrderId.text = receipt.orderId
        binding.tvReceiptStatus.text = receipt.status
        binding.tvReceiptDate.text = receipt.createdAt
        binding.tvReceiptTotal.text = "${receipt.totalPrice} KRW"

        binding.layoutReceiptItems.removeAllViews()
        receipt.items.forEach { item ->
            binding.layoutReceiptItems.addView(createItemView(item))
        }
    }

    private fun createItemView(item: OrderItemDTO): View {
        val itemView = layoutInflater.inflate(
            R.layout.item_receipt_menu,
            binding.layoutReceiptItems,
            false
        )
        itemView.findViewById<TextView>(R.id.tv_receipt_menu_name).text =
            item.menuName ?: "Menu"
        itemView.findViewById<TextView>(R.id.tv_receipt_menu_quantity).text =
            "x${item.quantity}"
        itemView.findViewById<TextView>(R.id.tv_receipt_menu_price).text =
            "${item.price} KRW"
        return itemView
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
    }
}
