package com.add.pepers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider

@Composable
internal fun DrawerContent(
    modifier: Modifier,
    shops: List<ShopRecord>,
    selectedShopId: Long?,
    userName: String,
    userImagePath: String,
    onClose: () -> Unit,
    onSelectShop: (Long) -> Unit,
    onAddShop: () -> Unit,
    onUserProfile: () -> Unit,
    onStatistics: () -> Unit,
    onAbout: () -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    onDeleteShop: () -> Unit
) {
    val currentShop = shops.firstOrNull { it.id == selectedShopId }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier
                .fillMaxHeight()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ================= رأس القائمة =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "القائمة",
                        tint = Purple,
                        modifier = Modifier.size(26.dp)
                    )
                    Text(
                        text = "القائمة",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Purple
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF4EFF8))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "إغلاق",
                        tint = Purple,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(bottom = 10.dp),
                color = Color(0xFFE7E1EA)
            )

            // ================= بطاقة المستخدم والمحل الحالي =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFFF0E7F7))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    if (userImagePath.isNotBlank()) {
                        LocalProfileImage(
                            path = userImagePath,
                            contentDescription = "الصورة الشخصية",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Purple,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = if (userName.isBlank()) "مستخدم غير مسجل" else userName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Purple,
                        maxLines = 1
                    )
                    Text(
                        text = currentShop?.name?.takeIf { it.isNotBlank() } ?: "لم يتم اختيار محل",
                        fontSize = 13.sp,
                        color = Color(0xFF77727B),
                        maxLines = 1
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // ================= عناصر الحساب =================
            DrawerFilledButton(
                text = "ملفي الشخصي",
                icon = Icons.Default.Person,
                containerColor = Purple,
                onClick = onUserProfile
            )

            Spacer(Modifier.height(7.dp))

            DrawerFilledButton(
                text = "الإحصائيات",
                icon = Icons.Default.Person,
                containerColor = Blue,
                onClick = onStatistics
            )

            Spacer(Modifier.height(7.dp))

            DrawerOutlinedButton(
                text = "من نحن",
                icon = "●",
                onClick = onAbout
            )

            Spacer(Modifier.height(7.dp))

            DrawerOutlinedButton(
                text = "دليل الاستخدام",
                icon = "؟",
                onClick = onHelp
            )

            Spacer(Modifier.height(7.dp))

            DrawerOutlinedButton(
                text = "إعدادات التسجيل",
                icon = "⚙",
                onClick = onSettings
            )

            Spacer(Modifier.height(12.dp))

            // ================= قسم المحلات =================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Purple)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "المحلات",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4D4652)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "🏪",
                    fontSize = 15.sp
                )
            }

            Spacer(Modifier.height(7.dp))

            if (shops.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF8F7F9))
                        .border(1.dp, Color(0xFFE5E1E8), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "لا توجد محلات مضافة",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(shops, key = { it.id }) { shop ->
                        val selected = shop.id == selectedShopId

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) Color(0xFFF0E7F7) else Color(0xFFFAFAFA)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) Purple else Color(0xFFE2DEE5),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectShop(shop.id) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selected) "✓" else "○",
                                color = if (selected) Purple else Color.Gray,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                text = shop.name,
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) Purple else Color(0xFF4D4652),
                                maxLines = 1
                            )
                            if (selected) {
                                Text(
                                    text = "الحالي",
                                    fontSize = 10.sp,
                                    color = Purple,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onAddShop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Purple),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Purple
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "إضافة محل جديد",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (selectedShopId != null) {
                Spacer(Modifier.height(4.dp))

                androidx.compose.material3.TextButton(
                    onClick = onDeleteShop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Red,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "حذف المحل الحالي",
                        color = Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            HorizontalDivider(
                color = Color(0xFFE7E1EA),
                modifier = Modifier.padding(top = 6.dp, bottom = 7.dp)
            )

            Text(
                text = "جميع الحقوق محفوظة © 2026",
                fontSize = 9.sp,
                color = Color(0xFF99949C),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DrawerFilledButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = containerColor),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DrawerOutlinedButton(
    text: String,
    icon: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF9B979D)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Purple),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = Purple,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = icon,
                color = Purple,
                fontSize = if (icon == "؟") 20.sp else 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
