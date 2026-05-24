package com.fan.edgex.ui.compose.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fan.edgex.ui.compose.EdgeXRoute
import com.fan.edgex.ui.compose.components.EdgeXTopBar
import com.fan.edgex.ui.compose.theme.LocalEdgeXColors

@Composable
fun EdgeXPlaceholderScreen(
    route: EdgeXRoute,
    onBack: () -> Unit,
    onOpenLegacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalEdgeXColors.current
    Column(modifier = modifier.fillMaxSize()) {
        EdgeXTopBar(title = route.label, onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = route.label,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 36.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "此页面将在后续独立提交中迁移到新 UI。",
                color = colors.onSurfaceDim,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(22.dp))
            Button(
                onClick = onOpenLegacy,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent,
                ),
            ) {
                Text("打开旧版页面")
            }
        }
    }
}
