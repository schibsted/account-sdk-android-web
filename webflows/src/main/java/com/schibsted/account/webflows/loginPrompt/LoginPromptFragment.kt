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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.LoginPromptDialog)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                isCancelable = false
            }

        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = LoginPromptBinding.inflate(inflater, container, false)
        initializeButtons()

        return binding.root
    }

    private fun initializeButtons() {
        binding.loginPromptButton.setOnClickListener {
            // placeholder TODO: add call to handleAuthenticationResponse
            dismiss()
        }
        binding.loginPromptSkip.setOnClickListener {
            dismiss()
        }

        binding.loginPromptPrivacy.setOnClickListener {
            val uri = Uri.parse(getString(R.string.login_prompt_privacy_url))
            if (Util.isCustomTabsSupported(this.requireContext())) {
                CustomTabsIntent.Builder().build().launchUrl(this.requireContext(), uri)
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
