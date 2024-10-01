package com.schibsted.account.webflows.loginPrompt

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.schibsted.account.webflows.R
import com.schibsted.account.webflows.tracking.SchibstedAccountTracker
import com.schibsted.account.webflows.tracking.SchibstedAccountTrackingEvent
import com.schibsted.account.webflows.util.Util
import kotlinx.coroutines.launch

internal class LoginPromptFragment : BottomSheetDialogFragment() {
    private lateinit var view: View
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
        view =
            LayoutInflater.from(requireContext()).inflate(
                R.layout.login_prompt,
                container,
                false,
            )
        return view
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        initializeButtons()
        SchibstedAccountTracker.track(SchibstedAccountTrackingEvent.LoginPromptCreated)
    }

    override fun onStart() {
        super.onStart()
        SchibstedAccountTracker.track(SchibstedAccountTrackingEvent.LoginPromptView)
    }

    override fun onStop() {
        super.onStop()
        SchibstedAccountTracker.track(SchibstedAccountTrackingEvent.LoginPromptLeave)
    }

    override fun onDestroyView() {
        SchibstedAccountTracker.track(SchibstedAccountTrackingEvent.LoginPromptDestroyed)
        super.onDestroyView()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        SchibstedAccountTracker.track(SchibstedAccountTrackingEvent.LoginPromptClickOutside)
    }

    private fun initializeButtons() {
        view.findViewById<Button>(R.id.loginPromptAuth).setOnClickListener {
            dismiss()
            startActivity(loginPromptConfig.authIntent)
            SchibstedAccountTracker.track(SchibstedAccountTrackingEvent.LoginPromptClickToLogin)
        }
        view.findViewById<Button>(R.id.loginPromptSkip)
            .setOnClickListener {
                SchibstedAccountTracker.track(SchibstedAccountTrackingEvent.LoginPromptClickToContinueWithoutLogin)
                dismiss()
            }
        view.findViewById<Button>(R.id.loginPromptPrivacy).setOnClickListener {
            var loginPromptContext = this.requireContext()
            val uri = Uri.parse(getString(R.string.login_prompt_privacy_url))
            if (Util.isCustomTabsSupported(loginPromptContext)) {
                CustomTabsIntent.Builder().build().launchUrl(loginPromptContext, uri)
            } else {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        uri,
                    ).addCategory(Intent.CATEGORY_BROWSABLE),
                )
            }
        }
    }

    companion object {
        const val ARG_CONFIG = "LOGIN_PROMPT_CONFIG_ARG"
    }
}
