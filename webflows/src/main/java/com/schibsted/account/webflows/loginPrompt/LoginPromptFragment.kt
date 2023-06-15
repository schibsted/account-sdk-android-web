package com.schibsted.account.webflows.loginPrompt

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.schibsted.account.webflows.R
import com.schibsted.account.webflows.databinding.LoginPromptBinding
import com.schibsted.account.webflows.util.Util
import kotlinx.coroutines.launch

class LoginPromptFragment : BottomSheetDialogFragment() {
    private var _binding: LoginPromptBinding? = null
    private val binding get() = _binding!!
    lateinit var loginPromptConfig: LoginPromptConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.LoginPromptDialog)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                isCancelable = loginPromptConfig.isCancelable
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        super.onCreateView(inflater, container, savedInstanceState)
        _binding = LoginPromptBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initializeButtons()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun initializeButtons() {
        binding.loginPromptAuth.setOnClickListener {
            startActivity(loginPromptConfig.client.getAuthenticationIntent(this.requireContext()))
        }
        binding.loginPromptSkip.setOnClickListener {
            dismiss()
        }

        binding.loginPromptPrivacy.setOnClickListener {
            var loginPromptContext = this.requireContext()
            val uri = Uri.parse(getString(R.string.login_prompt_privacy_url))
            if (Util.isCustomTabsSupported(loginPromptContext)) {
                CustomTabsIntent.Builder().build().launchUrl(loginPromptContext, uri)
            } else {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        uri
                    ).addCategory(Intent.CATEGORY_BROWSABLE)
                )
            }
        }
    }
}
