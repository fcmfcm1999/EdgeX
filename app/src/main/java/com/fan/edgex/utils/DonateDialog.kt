package com.fan.edgex.utils

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.fan.edgex.R
import java.net.URLEncoder

object DonateDialog {

    // Fill in your own Alipay payment QR URL here
    private const val ALIPAY_QR_URL = "https://qr.alipay.com/17o17940hsgauvnmins9h4c"
    
    // Crypto addresses
    private const val ETH_ADDRESS = "0xf309912220eaba0e7ff7448ada60b509a7b82467"
    private const val SOL_ADDRESS = "FANCYuPped3sb2YiHoJe56TRSGbC7MitpNKyHP5HmddK"

    fun show(context: Context) {
        val dp = { dp: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt() }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24f), dp(20f), dp(24f), dp(8f))
        }

        // Title
        TextView(context).apply {
            text = context.getString(R.string.donate_title)
            textSize = 18f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            root.addView(this, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(8f) })
        }

        // Subtitle
        TextView(context).apply {
            text = context.getString(R.string.donate_subtitle)
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            root.addView(this, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(20f) })
        }

        // Buttons row 1 (Alipay + WeChat)
        val buttonRow1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val buttonParams = LinearLayout.LayoutParams(0, dp(44f), 1f).also {
            it.marginEnd = dp(8f)
        }

        // Alipay button
        val alipayBtn = makeButton(context, context.getString(R.string.donate_alipay), Color.parseColor("#1677FF"))
        buttonRow1.addView(alipayBtn, buttonParams)

        // WeChat button
        val wechatParams = LinearLayout.LayoutParams(0, dp(44f), 1f).also {
            it.marginStart = dp(8f)
        }
        val wechatBtn = makeButton(context, context.getString(R.string.donate_wechat), Color.parseColor("#07C160"))
        buttonRow1.addView(wechatBtn, wechatParams)

        root.addView(buttonRow1, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = dp(8f) })

        // Buttons row 2 (Crypto)
        val cryptoBtn = makeButton(context, context.getString(R.string.donate_crypto), Color.parseColor("#F7931A"))
        root.addView(cryptoBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(44f)
        ).also { it.bottomMargin = dp(4f) })

        val shape = GradientDrawable().apply {
            setColor(Color.parseColor("#F5F5F5"))
            cornerRadius = dp(12f).toFloat()
        }
        val bg = InsetDrawable(shape, dp(24f))

        val dialog = AlertDialog.Builder(context)
            .setView(root)
            .create()

        dialog.window?.setBackgroundDrawable(bg)

        alipayBtn.setOnClickListener {
            dialog.dismiss()
            openAlipay(context)
        }
        wechatBtn.setOnClickListener {
            dialog.dismiss()
            showWechatQr(context)
        }
        cryptoBtn.setOnClickListener {
            dialog.dismiss()
            showCryptoAddresses(context)
        }

        dialog.show()
    }

    private fun makeButton(context: Context, label: String, color: Int): TextView {
        val dp = { dp: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt() }
        return TextView(context).apply {
            text = label
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(8f).toFloat()
            }
        }
    }

    private fun openAlipay(context: Context) {
        if (ALIPAY_QR_URL.isEmpty()) {
            android.widget.Toast.makeText(context, "Alipay not configured", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val encoded = URLEncoder.encode(ALIPAY_QR_URL, "utf-8")
            val uri = "alipayqr://platformapi/startapp?saId=10000007&clientVersion=3.7.0.0718&qrcode=$encoded%3F_s%3Dweb-other&_t=${System.currentTimeMillis()}"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        } catch (e: Exception) {
            // Alipay not installed — open browser fallback
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ALIPAY_QR_URL)))
            } catch (e2: Exception) {
                android.widget.Toast.makeText(context, "Cannot open Alipay", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showWechatQr(context: Context) {
        val dp = { dp: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt() }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24f), dp(20f), dp(24f), dp(20f))
        }

        TextView(context).apply {
            text = context.getString(R.string.donate_wechat_scan)
            textSize = 15f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            container.addView(this, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(16f) })
        }

        val qrImageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            // Set the WeChat QR drawable — add a PNG named ic_wechat_qr.png to res/drawable/
            val resId = context.resources.getIdentifier("ic_wechat_qr", "drawable", context.packageName)
            if (resId != 0) {
                setImageResource(resId)
            } else {
                visibility = View.GONE
            }
        }
        container.addView(qrImageView, LinearLayout.LayoutParams(dp(200f), dp(200f)))

        val shape = GradientDrawable().apply {
            setColor(Color.parseColor("#F5F5F5"))
            cornerRadius = dp(12f).toFloat()
        }

        AlertDialog.Builder(context)
            .setView(container)
            .create()
            .also {
                it.window?.setBackgroundDrawable(InsetDrawable(shape, dp(24f)))
                it.show()
            }
    }

    private fun showCryptoAddresses(context: Context) {
        val dp = { dp: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt() }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24f), dp(20f), dp(24f), dp(20f))
        }

        // Title
        TextView(context).apply {
            text = context.getString(R.string.donate_crypto_title)
            textSize = 18f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
            container.addView(this, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(16f) })
        }

        // ETH address
        addCryptoRow(context, container, context.getString(R.string.donate_crypto_eth), ETH_ADDRESS, dp)
        
        // SOL address
        addCryptoRow(context, container, context.getString(R.string.donate_crypto_sol), SOL_ADDRESS, dp)

        val shape = GradientDrawable().apply {
            setColor(Color.parseColor("#F5F5F5"))
            cornerRadius = dp(12f).toFloat()
        }

        AlertDialog.Builder(context)
            .setView(container)
            .create()
            .also {
                it.window?.setBackgroundDrawable(InsetDrawable(shape, dp(24f)))
                it.show()
            }
    }

    private fun addCryptoRow(context: Context, container: LinearLayout, label: String, address: String, dp: (Float) -> Int) {
        // Label
        TextView(context).apply {
            text = label
            textSize = 14f
            setTextColor(Color.parseColor("#333333"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            container.addView(this, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(4f) })
        }

        // Address (clickable to copy)
        TextView(context).apply {
            text = address
            textSize = 12f
            setTextColor(Color.parseColor("#666666"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EEEEEE"))
                cornerRadius = dp(6f).toFloat()
            }
            setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
            setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Crypto Address", address))
                Toast.makeText(context, context.getString(R.string.donate_crypto_copied), Toast.LENGTH_SHORT).show()
            }
            container.addView(this, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dp(12f) })
        }
    }
}
