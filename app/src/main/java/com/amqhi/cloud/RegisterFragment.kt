/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.amqhi.cloud

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.amqhi.cloud.core.Core
import com.amqhi.cloud.databinding.FragmentRegisterBinding

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    val launchType: Short
        get() = requireArguments().getShort(ARG_LAUNCH_TYPE)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        applyTheme()
        return binding.root
    }

    private fun applyTheme() {
        val theme = AppTheme.attributes
        val strokeWidth = resources.displayMetrics.density.toInt().coerceAtLeast(1)

        binding.root.setBackgroundColor(theme.backgroundColor)
        binding.icon.setTextColor(theme.primaryColor)
        binding.title.applyAppTitle(theme)

        listOf(
            binding.instanceUrlInput,
            binding.emailInput,
            binding.nameInput,
            binding.passwordInput,
            binding.passwordConfirmInput
        ).forEach { input ->
            input.setTextColor(theme.textColorPrimary)
            input.setHintTextColor(theme.textColorHint)
            (input.background?.mutate() as? GradientDrawable)?.setStroke(strokeWidth, theme.dividerColor)
        }

        binding.registerButton.setTextColor(theme.onPrimaryColor)
        (binding.registerButton.background?.mutate() as? GradientDrawable)?.setColor(theme.primaryColor)

        binding.loginButton.setTextColor(theme.primaryColor)
        (binding.loginButton.background?.mutate() as? GradientDrawable)?.setStroke(
            (strokeWidth * 2).coerceAtLeast(2),
            theme.primaryColor
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        arguments?.getString(ARG_INSTANCE_URL)
            ?.takeIf { it.isNotBlank() }
            ?.let { binding.instanceUrlInput.setText(it) }

        binding.registerButton.setOnClickListener {
            val instanceUrl = binding.instanceUrlInput.text.toString()
            val email = binding.emailInput.text.toString()
            val name = binding.nameInput.text.toString()
            val password = binding.passwordInput.text.toString()
            val passwordConfirm = binding.passwordConfirmInput.text.toString()
            if (password != passwordConfirm) {
                Toast.makeText(requireContext(), "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Core.handleRegister(
                instanceUrl = instanceUrl,
                email = email,
                name = name,
                password = password
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_INSTANCE_URL = "instance_url"
        private const val ARG_LAUNCH_TYPE = "launch_type"

        fun newInstance(instanceUrl: String, launchType: Short) = RegisterFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_INSTANCE_URL, instanceUrl)
                putShort(ARG_LAUNCH_TYPE, launchType)
            }
        }
    }
}
