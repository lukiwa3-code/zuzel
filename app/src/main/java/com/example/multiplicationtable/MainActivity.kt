package com.example.multiplicationtable

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SOURCE_URL = "https://sportowefakty.wp.pl/zuzel"
private const val NEWS_SECTION_SELECTOR = "[data-st-area=news-list]"
private const val NEWS_LINK_SELECTOR = "a.teaser__title[href]"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ZuzelNewsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ZuzelNewsApp()
                }
            }
        }
    }
}

data class NewsItem(
    val title: String,
    val description: String,
    val url: String
)

sealed interface NewsUiState {
    data object Loading : NewsUiState

    data class Success(
        val news: List<NewsItem>,
        val updatedAt: String
    ) : NewsUiState

    data class Error(
        val message: String
    ) : NewsUiState
}

@Composable
fun ZuzelNewsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFFF97316),
            secondary = Color(0xFF111827),
            background = Color(0xFFF3F4F6),
            surface = Color.White,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = Color(0xFF111827),
            onSurface = Color(0xFF111827)
        ),
        content = content
    )
}

@Composable
fun ZuzelNewsApp() {
    val context = LocalContext.current

    var refreshCounter by remember {
        mutableIntStateOf(0)
    }

    var state by remember {
        mutableStateOf<NewsUiState>(NewsUiState.Loading)
    }

    LaunchedEffect(refreshCounter) {
        state = NewsUiState.Loading

        state = try {
            val news = fetchZuzelNews()

            if (news.isEmpty()) {
                NewsUiState.Error(
                    message = "Nie znaleziono newsów w sekcji news-list."
                )
            } else {
                NewsUiState.Success(
                    news = news,
                    updatedAt = currentTimeText()
                )
            }
        } catch (exception: Exception) {
            NewsUiState.Error(
                message = exception.message ?: "Nie udało się pobrać wiadomości."
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Header()

        Toolbar(
            onRefreshClick = {
                refreshCounter++
            },
            onSourceClick = {
                openUrl(context, SOURCE_URL)
            }
        )

        when (val currentState = state) {
            NewsUiState.Loading -> LoadingScreen()

            is NewsUiState.Error -> ErrorScreen(
                message = currentState.message,
                onRetryClick = {
                    refreshCounter++
                }
            )

            is NewsUiState.Success -> NewsListScreen(
                news = currentState.news,
                updatedAt = currentState.updatedAt,
                onNewsClick = { item ->
                    openUrl(context, item.url)
                }
            )
        }
    }
}

@Composable
fun Header() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF111827))
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 22.dp
            )
    ) {
        Text(
            text = "Żużel News",
            color = Color.White,
            fontSize = 31.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Wiadomości z WP SportoweFakty",
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 15.sp
        )
    }
}

@Composable
fun Toolbar(
    onRefreshClick: () -> Unit,
    onSourceClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = onRefreshClick,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF97316),
                contentColor = Color.White
            )
        ) {
            Text("Odśwież")
        }

        Button(
            onClick = onSourceClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF111827),
                contentColor = Color.White
            )
        ) {
            Text("WP")
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = Color(0xFFF97316)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Pobieranie newsów...",
                color = Color(0xFF4B5563),
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onRetryClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Coś poszło nie tak",
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111827)
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = message,
            fontSize = 15.sp,
            color = Color(0xFF6B7280)
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = onRetryClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF97316),
                contentColor = Color.White
            )
        ) {
            Text("Spróbuj ponownie")
        }
    }
}

@Composable
fun NewsListScreen(
    news: List<NewsItem>,
    updatedAt: String,
    onNewsClick: (NewsItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 14.dp,
            end = 14.dp,
            top = 12.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Znaleziono newsy: ${news.size} • Odświeżono: $updatedAt",
                color = Color(0xFF6B7280),
                fontSize = 13.sp,
                modifier = Modifier.padding(
                    start = 2.dp,
                    end = 2.dp,
                    bottom = 2.dp
                )
            )
        }

        items(
            items = news,
            key = { item -> item.url }
        ) { item ->
            NewsCard(
                item = item,
                onClick = {
                    onNewsClick(item)
                }
            )
        }
    }
}

@Composable
fun NewsCard(
    item: NewsItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(15.dp)
        ) {
            Text(
                text = "ŻUŻEL",
                color = Color(0xFFC2410C),
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.title,
                color = Color(0xFF111827),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 23.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            if (item.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = item.description,
                    color = Color(0xFF4B5563),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Czytaj artykuł",
                color = Color(0xFFF97316),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

suspend fun fetchZuzelNews(): List<NewsItem> = withContext(Dispatchers.IO) {
    val document = Jsoup
        .connect(SOURCE_URL)
        .userAgent(
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        )
        .referrer("https://www.google.com/")
        .timeout(15_000)
        .get()

    val primaryLinks = document.select(
        "[data-st-area=news-list] a.teaser__title[href]"
    )

    val fallbackLinks = if (primaryLinks.isEmpty()) {
        document.select(
            "[data-st-area=news-list] a[href], .teaser a.teaser__title[href]"
        )
    } else {
        primaryLinks
    }

    val found = linkedMapOf<String, NewsItem>()

    fallbackLinks.forEach { link ->
        val rawTitle = link.text().ifBlank {
            link.attr("title")
        }

        val title = rawTitle.cleanText()
        val absoluteUrl = link.absUrl("href").cleanWpUrl()

        if (title.length < 8 || absoluteUrl.isBlank()) {
            return@forEach
        }

        if (!isValidZuzelArticleUrl(absoluteUrl)) {
            return@forEach
        }

        if (!found.containsKey(absoluteUrl)) {
            found[absoluteUrl] = NewsItem(
                title = title,
                description = extractDescriptionFromTeaser(link, title),
                url = absoluteUrl
            )
        }
    }

    if (found.isEmpty()) {
        throw IllegalStateException(
            "Nie znaleziono newsów w selektorze: [data-st-area=news-list] a.teaser__title[href]"
        )
    }

    found.values.take(40)
}

fun isValidZuzelArticleUrl(url: String): Boolean {
    val uri = runCatching {
        Uri.parse(url)
    }.getOrNull() ?: return false

    val host = uri.host ?: return false
    val path = uri.path ?: return false

    val isCorrectHost = host == "sportowefakty.wp.pl"
    val isZuzelArticle = path.startsWith("/zuzel/") && path != "/zuzel/"
    val isNotMediaOnly =
        !path.contains("/wideo/") &&
            !path.contains("/galeria/") &&
            !path.contains("/tabela/") &&
            !path.contains("/terminarz/") &&
            !path.contains("/wyniki/")

    return isCorrectHost && isZuzelArticle && isNotMediaOnly
}

fun extractDescriptionFromTeaser(
    link: Element,
    title: String
): String {
    val teaser = link.closest(".teaser") ?: return ""

    val selectors = listOf(
        ".teaser__lead",
        ".teaser__description",
        ".teaser__summary",
        ".teaser__text"
    )

    selectors.forEach { selector ->
        val text = teaser.selectFirst(selector)?.text()?.cleanText().orEmpty()

        if (text.length >= 25 && text != title) {
            return text.limitLength(180)
        }
    }

    val fallback = teaser
        .text()
        .cleanText()
        .replace(title, "")
        .replace("Żużel", "")
        .replace("Czytaj", "")
        .cleanText()

    return if (fallback.length >= 25) {
        fallback.limitLength(180)
    } else {
        ""
    }
}

fun String.cleanText(): String {
    return this
        .replace("\n", " ")
        .replace("\t", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

fun String.cleanWpUrl(): String {
    return this
        .substringBefore("?")
        .substringBefore("#")
        .trim()
}

fun String.limitLength(maxLength: Int): String {
    return if (this.length <= maxLength) {
        this
    } else {
        this.take(maxLength).trim() + "..."
    }
}

fun currentTimeText(): String {
    return SimpleDateFormat(
        "HH:mm",
        Locale.getDefault()
    ).format(Date())
}

fun openUrl(
    context: Context,
    url: String
) {
    try {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()

        customTabsIntent.launchUrl(
            context,
            Uri.parse(url)
        )
    } catch (exception: Exception) {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url)
        )

        context.startActivity(intent)
    }
}
