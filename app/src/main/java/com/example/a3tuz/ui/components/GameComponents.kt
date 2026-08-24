package com.example.a3tuz.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import com.example.a3tuz.api.calculateGameScore
import com.example.a3tuz.R
import java.util.Locale

enum class AnimType { BET, WIN }
data class BetAnim(val id: Long, val username: String, val amount: Double, val type: AnimType, val position: Int)

@Composable
fun PlayerCircleItem(
    username: String,
    avatarStr: String,
    isCurrentTurn: Boolean,
    turnTimer: Int,
    isOvertime: Boolean,
    cards: List<String>?,
    isPlaying: Boolean,
    isFolded: Boolean,
    isMe: Boolean,
    modifier: Modifier = Modifier,
    isCheatVisible: Boolean = false,
    cheatLevel: Int = 0,
    onCheatClick: () -> Unit = {},
    balance: Double? = null,
    cardAlignment: Alignment = Alignment.BottomCenter
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val avatarBitmap = remember(avatarStr) {
        if (avatarStr.isEmpty()) null
        else try {
            val bytes = Base64.decode(avatarStr, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }

    Box(modifier = modifier.size(110.dp), contentAlignment = Alignment.Center) {
        // Kartlar və Xal (Mərkəzə doğru itələnmiş mövqe)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = cardAlignment
        ) {
            Box(modifier = Modifier.offset(
                y = when(cardAlignment) {
                    Alignment.BottomCenter -> 10.dp
                    Alignment.TopCenter -> (-10).dp
                    else -> 0.dp
                },
                x = when(cardAlignment) {
                    Alignment.TopEnd, Alignment.BottomEnd -> 5.dp
                    Alignment.TopStart, Alignment.BottomStart -> (-5).dp
                    else -> 0.dp
                }
            )) {
                PlayerCardsContent(cards, isPlaying, isFolded)
            }
        }

        // Oyunçu İkonu və Adı
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.TopEnd) {
                Box(contentAlignment = Alignment.Center) {
                    if (isCurrentTurn && !isFolded) {
                        Box(
                            modifier = Modifier
                                .size(if (isMe) 56.dp else 50.dp)
                                .graphicsLayer {
                                    scaleX = pulseScale
                                    scaleY = pulseScale
                                    alpha = 0.5f
                                }
                                .background(
                                    if (isOvertime) Color.Red.copy(alpha = 0.4f) else Color(0xFFFFD700).copy(alpha = 0.3f),
                                    CircleShape
                                )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(if (isMe) 48.dp else 42.dp)
                            .clip(CircleShape)
                            .background(Brush.verticalGradient(listOf(Color(0xFF424242), Color(0xFF212121))))
                            .border(
                                width = if (isCurrentTurn) 2.dp else 1.dp,
                                brush = if (isCurrentTurn)
                                    Brush.sweepGradient(listOf(Color.Yellow, Color.White, Color.Yellow))
                                else SolidColor(Color.White.copy(alpha = 0.2f)),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (avatarBitmap != null) Image(
                            bitmap = avatarBitmap.asImageBitmap(),
                            null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            colorFilter = if (isFolded) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
                        )
                        else Text(
                            username.take(1).uppercase(),
                            color = if (isFolded) Color.Gray else Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )

                        if (isFolded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "PAS",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier
                                        .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 2.dp)
                                )
                            }
                        }

                        if (isCurrentTurn && !isFolded) Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = CircleShape,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = turnTimer.toString(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
                
                if (isCheatVisible && !isMe) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 10.dp, y = (-10).dp)
                            .size(28.dp)
                            .background(
                                when(cheatLevel) {
                                    1 -> Color(0xFF4CAF50) // Yaşıl (Normal)
                                    2 -> Color.Yellow      // Sarı (Super)
                                    3 -> Color.Cyan        // Mavi (Seka)
                                    else -> Color.Red      // Qırmızı (Sönülü)
                                }, 
                                CircleShape
                            )
                            .border(1.5.dp, Color.White, CircleShape)
                            .clickable { onCheatClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(if (cheatLevel > 0) "🪄" else "👁️", fontSize = 14.sp)
                    }
                }
            }

            Text(
                text = (if (isFolded) "PAS: " else "") + (if (isMe) "MƏN" else username.uppercase()),
                color = if (isCurrentTurn) Color.Yellow else if (isFolded) Color.Gray else Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (balance != null) {
                Text(
                    text = "${balance.toInt()} ₼",
                    color = Color(0xFF4CAF50),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun PlayerCardsContent(cards: List<String>?, isPlaying: Boolean, isFolded: Boolean) {
    if (cards != null) {
        val score = calculateGameScore(cards)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                color = Color(0xFFFFC107).copy(alpha = 0.9f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Text(
                    text = String.format(Locale.getDefault(), "%.1f", score),
                    color = Color.Black,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy((-12).dp)
            ) { cards.forEach { CardImageSmall(it) } }
        }
    } else if (isPlaying) {
        if (isFolded) {
            Surface(
                color = Color.Red.copy(alpha = 0.9f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    " PAS ",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-12).dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                repeat(3) {
                    Image(
                        painter = painterResource(id = R.drawable.card_back),
                        null,
                        modifier = Modifier
                            .size(20.dp, 28.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun CardImageSmall(card: String) {
    val context = LocalContext.current
    val resId = remember(card) {
        try {
            val vS = card.substring(0, card.length - 1)
            val sC = card.last()
            val v = when (vS) {
                "A" -> "14"
                "K" -> "13"
                "Q" -> "12"
                "J" -> "11"
                else -> vS
            }
            val s = when (sC) {
                '♣' -> "c"
                '♦' -> "d"
                '♥' -> "h"
                '♠' -> "s"
                else -> "back"
            }
            context.resources.getIdentifier("card_${v}_$s", "drawable", context.packageName)
        } catch (e: Exception) {
            0
        }
    }
    Image(
        painter = painterResource(id = if (resId != 0) resId else R.drawable.card_back),
        contentDescription = null,
        modifier = Modifier
            .size(20.dp, 28.dp)
            .clip(RoundedCornerShape(2.dp))
    )
}

@Composable
fun FlyingCoin(anim: BetAnim, screenWidth: Float, screenHeight: Float) {
    var startAnim by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnim = true }
    val animDuration = if (anim.type == AnimType.WIN) 4000 else 2000
    val progress by animateFloatAsState(
        if (startAnim) 1f else 0f,
        tween(animDuration, easing = FastOutSlowInEasing),
        label = ""
    )
    val alpha by animateFloatAsState(if (progress > 0.9f) 0f else 1f, tween(200), label = "")

    val angle = 90.0 + (anim.position * 60.0)
    val rad = Math.toRadians(angle)
    val radiusX = screenWidth * 0.38f
    val radiusY = screenHeight * 0.22f
    
    val startX = (radiusX * Math.cos(rad)).toFloat()
    val startY = (radiusY * Math.sin(rad)).toFloat()

    val currentX = if (anim.type == AnimType.BET) startX * (1f - progress) else startX * progress
    val currentY = if (anim.type == AnimType.BET) startY * (1f - progress) else startY * progress

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .offset(x = currentX.dp, y = (currentY - 40).dp)
                .alpha(alpha)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    repeat(3) {
                        Image(
                            painter = painterResource(id = R.drawable.monet),
                            contentDescription = null,
                            modifier = Modifier
                                .width(45.dp)
                                .height(22.dp)
                                .graphicsLayer {
                                    rotationZ = (it * 10 - 10).toFloat()
                                    translationX = (it * 6).dp.toPx()
                                    translationY = (it * -3).dp.toPx()
                                },
                            contentScale = ContentScale.FillBounds
                        )
                    }
                }
                Text(
                    text = "${if (anim.type == AnimType.WIN) "+" else "-"}${
                        String.format(
                            Locale.getDefault(),
                            "%.2f",
                            anim.amount
                        )
                    } ₼",
                    color = if (anim.type == AnimType.WIN) Color(0xFF4CAF50) else Color.Yellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
fun GameActionButton(
    text: String,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = color.copy(alpha = 0.3f)
        ),
        modifier = modifier
            .height(45.dp)
            .padding(horizontal = 1.dp),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(
            text = text,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 9.sp
        )
    }
}
