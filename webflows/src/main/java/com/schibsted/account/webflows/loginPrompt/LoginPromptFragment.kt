package com.schibsted.account.webflows.loginPrompt

import android.content.DialogInterface
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
import com.schibsted.account.webflows.tracking.SchibstedAccountTracker
import com.schibsted.account.webflows.tracking.SchibstedAccountTrackingEvent.*
import com.schibsted.account.webflows.util.Util
import kotlinx.coroutines.launch

internal class LoginPromptFragment : BottomSheetDialogFragment() {
    private var _binding: LoginPromptBinding? = null
    private val binding get() = _binding!!

    private lateinit var loginPromptConfig: LoginPromptConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        loginPromptConfig = requireArguments().getParcelable(ARG_CONFIG)!!
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
        SchibstedAccountTracker.track(LoginPromptCreated)
    }

    override fun onStart() {
        super.onStart()
        SchibstedAccountTracker.track(LoginPromptView)
    }

    override fun onStop() {
        super.onStop()
        SchibstedAccountTracker.track(LoginPromptLeave)
    }

    override fun onDestroyView() {
        _binding = null
        SchibstedAccountTracker.track(LoginPromptDestroyed)
        super.onDestroyView()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        SchibstedAccountTracker.track(LoginPromptClickOutside)
    }

    private fun initializeButtons() {
        binding.loginPromptAuth.setOnClickListener {
            startActivity(loginPromptConfig.authIntent)
            SchibstedAccountTracker.track(LoginPromptClickToLogin)
            dismissAllowingStateLoss()
        }
        binding.loginPromptSkip.setOnClickListener {
            SchibstedAccountTracker.track(LoginPromptClickToContinueWithoutLogin)
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

    companion object {
        const val ARG_CONFIG = "LOGIN_PROMPT_CONFIG_ARG"
    }

}
