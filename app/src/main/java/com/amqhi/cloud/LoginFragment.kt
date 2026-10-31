package com.amqhi.cloud

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.amqhi.cloud.core.Core
import com.amqhi.cloud.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {
    private var _binding: FragmentLoginBinding? = null
    private lateinit var binding: FragmentLoginBinding

    val launchType: Short
        get() = requireArguments().getShort(ARG_LAUNCH_TYPE)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentLoginBinding.inflate(inflater, container, false)
        if(launchType == AuthActivity.LAUNCH_RELOGIN) {
            binding.instanceUrlInput.visibility = View.GONE
            binding.registerButton.visibility = View.GONE
            binding.emailInput.isEnabled = false
            binding.emailInput.alpha = 0.5f
            binding.emailInput.setText(Core.getSelectedUser().email)
        }
        applyTheme()
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.loginButton.setOnClickListener {
            val instanceUrl = binding.instanceUrlInput.text.toString()
            val email = binding.emailInput.text.toString()
            val password = binding.passwordInput.text.toString()
            if(launchType == AuthActivity.LAUNCH_RELOGIN) {
                Core.handleLogin(email = email, password = password)
            }
            else {
                Core.handleLogin(instanceUrl = instanceUrl, email = email, password = password)
            }
        }
        binding.registerButton.setOnClickListener {
            (activity as? AuthActivity)?.showRegister(
                instanceUrl = binding.instanceUrlInput.text.toString()
            )
        }
    }

    private fun applyTheme() {
        val theme = AppTheme.attributes
        val strokeWidth = resources.displayMetrics.density.toInt().coerceAtLeast(1)

        binding.root.setBackgroundColor(theme.backgroundColor)
        binding.icon.setTextColor(theme.primaryColor)
        binding.title.applyAppTitle(theme)

        listOf(binding.instanceUrlInput, binding.emailInput, binding.passwordInput).forEach { input ->
            input.setTextColor(theme.textColorPrimary)
            input.setHintTextColor(theme.textColorHint)
            (input.background?.mutate() as? GradientDrawable)?.setStroke(strokeWidth, theme.dividerColor)
        }

        binding.loginButton.setTextColor(theme.onPrimaryColor)
        (binding.loginButton.background?.mutate() as? GradientDrawable)?.setColor(theme.primaryColor)

        binding.registerButton.setTextColor(theme.primaryColor)
        (binding.registerButton.background?.mutate() as? GradientDrawable)?.setStroke(
            (strokeWidth * 2).coerceAtLeast(2),
            theme.primaryColor
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_LAUNCH_TYPE = "launch_type"

        fun newInstance(launchType: Short) = LoginFragment().apply {
            arguments = Bundle().apply {
                putShort(ARG_LAUNCH_TYPE, launchType)
            }
        }
    }
}
