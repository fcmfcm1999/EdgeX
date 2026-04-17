package com.fan.edgex.utils

import android.app.AlertDialog
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
import com.fan.edgex.R
import java.net.URLEncoder

object DonateDialog {

    // Fill in your own Alipay payment QR URL here
    private const val ALIPAY_QR_URL = "https://qr.alipay.com/17o17940hsgauvnmins9h4c"

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

        // Buttons row
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val buttonParams = LinearLayout.LayoutParams(0, dp(44f), 1f).also {
            it.marginEnd = dp(8f)
        }

        // Alipay button
        val alipayBtn = makeButton(context, context.getString(R.string.donate_alipay), Color.parseColor("#1677FF"))
        buttonRow.addView(alipayBtn, buttonParams)

        // WeChat button
        val wechatParams = LinearLayout.LayoutParams(0, dp(44f), 1f).also {
            it.marginStart = dp(8f)
        }
        val wechatBtn = makeButton(context, context.getString(R.string.donate_wechat), Color.parseColor("#07C160"))
        buttonRow.addView(wechatBtn, wechatParams)

        root.addView(buttonRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
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
}
