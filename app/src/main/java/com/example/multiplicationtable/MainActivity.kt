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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ZUZEL_URL = "https://sportowefakty.wp.pl/zuzel"
private const val POLONIA_URL = "https://sportowefakty.wp.pl/zuzel/pronergy-polonia-pila"

private const val MATCHES_SELECTOR =
    "[data-st-area=\"Wyniki-pasek\"] a.livescore-item[href], " +
        "[data-source=\"header-livescore\"] a.livescore-item[href]"

private const val GENERAL_NEWS_SELECTOR =
    "[data-st-area=\"news-list\"] a.teaser__title[href]"

private const val POLONIA_NEWS_SELECTOR =
    "[data-st-area=\"category-news-list\"] a.teaser__title[href], " +
        "[data-st-area=\"news-list\"] a.teaser__title[href], " +
        ".teaser a.teaser__title[href]"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            ZuzelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ZuzelApp()
                }
            }
        }
    }
}

enum class AppTab(
    val title: String
) {
    Matches("Mecze"),
    News("Newsy"),
    Polonia("Polonia")
}

data class MatchTeam(
    val name: String,
    val score: String
)

data class MatchItem(
    val status: String,
    val url: String,
    val teams: List<MatchTeam>,
    val rawText: String
)

data class NewsItem(
    val title: String,
    val category: String,
    val date: String,
    val description: String,
    val imageUrl: String,
    val url: String
)

data class AppData(
    val matches: List<MatchItem>,
    val news: List<NewsItem>,
    val poloniaNews: List<NewsItem>,
    val updatedAt: String
)

sealed interface UiState {
    data object Loading : UiState

    data class Success(
        val data: AppData
    ) : UiState

    data class Error(
        val message: String
    ) : UiState
}

@Composable
fun ZuzelTheme(content: @Composable () -> Unit) {
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
fun ZuzelApp() {
    val context = LocalContext.current

    var selectedTab by remember {
        mutableStateOf(AppTab.Matches)
    }

    var refreshCounter by remember {
        mutableIntStateOf(0)
    }

    var state by remember {
        mutableStateOf<UiState>(UiState.Loading)
    }

    LaunchedEffect(refreshCounter) {
        state = UiState.Loading

        state = try {
            UiState.Success(fetchAppData())
        } catch (exception: Exception) {
            UiState.Error(
                message = exception.message ?: "Nie udało się pobrać danych."
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Header()

        AppTabs(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                selectedTab = tab
            }
        )

        Toolbar(
            selectedTab = selectedTab,
            onRefreshClick = {
                refreshCounter++
            },
            onSourceClick = {
                val url = when (selectedTab) {
                    AppTab.Polonia -> POLONIA_URL
                    AppTab.Matches,
                    AppTab.News -> ZUZEL_URL
                }

                openUrl(context, url)
            }
        )

        when (val currentState = state) {
            UiState.Loading -> LoadingScreen()

            is UiState.Error -> ErrorScreen(
                message = currentState.message,
                onRetryClick = {
                    refreshCounter++
                }
            )

            is UiState.Success -> {
                when (selectedTab) {
                    AppTab.Matches -> MatchesScreen(
                        matches = currentState.data.matches,
                        updatedAt = currentState.data.updatedAt,
                        onMatchClick = { match ->
                            openUrl(context, match.url)
                        }
                    )

                    AppTab.News -> NewsScreen(
                        news = currentState.data.news,
                        title = "Newsy",
                        updatedAt = currentState.data.updatedAt,
                        onNewsClick = { newsItem ->
                            openUrl(context, newsItem.url)
                        }
                    )

                    AppTab.Polonia -> NewsScreen(
                        news = currentState.data.poloniaNews,
                        title = "Polonia",
                        updatedAt = currentState.data.updatedAt,
                        onNewsClick = { newsItem ->
                            openUrl(context, newsItem.url)
                        }
                    )
                }
            }
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
                bottom = 20.dp
            )
    ) {
        Text(
            text = "Żużel App",
            color = Color.White,
            fontSize = 31.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Mecze, newsy i Polonia Piła",
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 15.sp
        )
    }
}

@Composable
fun AppTabs(
    selectedTab: AppTab,
    onTabSelected: (AppTab) -> Unit
) {
    TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = Color(0xFF111827),
        contentColor = Color.White
    ) {
        AppTab.entries.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = {
                    onTabSelected(tab)
                },
                text = {
                    Text(
                        text = tab.title,
                        fontWeight = FontWeight.Bold
                    )
                },
                selectedContentColor = Color.White,
                unselectedContentColor = Color(0xFFD1D5DB)
            )
        }
    }
}

@Composable
fun Toolbar(
    selectedTab: AppTab,
    onRefreshClick: () -> Unit,
    onSourceClick: () -> Unit
) {
    val sourceText = when (selectedTab) {
        AppTab.Polonia -> "Polonia WP"
        AppTab.Matches,
        AppTab.News -> "WP"
    }

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
            Text(sourceText)
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
                text = "Pobieranie danych...",
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
fun MatchesScreen(
    matches: List<MatchItem>,
    updatedAt: String,
    onMatchClick: (MatchItem) -> Unit
) {
    if (matches.isEmpty()) {
        EmptyScreen(
            message = "Nie znaleziono meczów."
        )
        return
    }

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
                text = "Mecze: ${matches.size} • Odświeżono: $updatedAt",
                color = Color(0xFF6B7280),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        items(
            items = matches,
            key = { item -> item.url }
        ) { item ->
            MatchCard(
                item = item,
                onClick = {
                    onMatchClick(item)
                }
            )
        }
    }
}

@Composable
fun MatchCard(
    item: MatchItem,
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
            StatusBadge(status = item.status)

            Spacer(modifier = Modifier.height(8.dp))

            if (item.teams.size >= 2) {
                item.teams.forEach { team ->
                    TeamRow(team = team)
                }
            } else {
                Text(
                    text = item.rawText.ifBlank {
                        "Kliknij, aby zobaczyć szczegóły meczu."
                    },
                    color = Color(0xFF374151),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Szczegóły meczu",
                color = Color(0xFFF97316),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val upper = status.uppercase(Locale.getDefault())

    val backgroundColor = when {
        upper.contains("ZAKOŃCZONY") -> Color(0xFFE5E7EB)
        upper.contains("DZIŚ") -> Color(0xFFDCFCE7)
        upper.contains("JUTRO") -> Color(0xFFDBEAFE)
        else -> Color(0xFFFFEDD5)
    }

    val textColor = when {
        upper.contains("ZAKOŃCZONY") -> Color(0xFF374151)
        upper.contains("DZIŚ") -> Color(0xFF166534)
        upper.contains("JUTRO") -> Color(0xFF1D4ED8)
        else -> Color(0xFFC2410C)
    }

    Text(
        text = status.ifBlank { "Mecz" },
        color = textColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(backgroundColor)
            .padding(
                horizontal = 10.dp,
                vertical = 6.dp
            )
    )
}

@Composable
fun TeamRow(team: MatchTeam) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = team.name,
            color = Color(0xFF111827),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = team.score,
            color = Color(0xFF111827),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

@Composable
fun NewsScreen(
    news: List<NewsItem>,
    title: String,
    updatedAt: String,
    onNewsClick: (NewsItem) -> Unit
) {
    if (news.isEmpty()) {
        EmptyScreen(
            message = "Nie znaleziono newsów w zakładce $title."
        )
        return
    }

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
                text = "$title: ${news.size} • Odświeżono: $updatedAt",
                color = Color(0xFF6B7280),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        items(
            items = news,
            key = { item -> item.url }
        ) { item ->
            NewsCard(
                item = item,
                tag = title.uppercase(Locale.getDefault()),
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
    tag: String,
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
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (item.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(116.dp)
                        .height(82.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color(0xFFE5E7EB))
                )

                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = tag,
                    color = Color(0xFFC2410C),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFFFEDD5))
                        .padding(
                            horizontal = 8.dp,
                            vertical = 4.dp
                        )
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (item.category.isNotBlank()) {
                    Text(
                        text = item.category,
                        color = Color(0xFF178A00),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = if (item.date.isBlank()) "Data: brak" else item.date,
                    color = Color(0xFF6B7280),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(7.dp))

                Text(
                    text = item.title,
                    color = Color(0xFF111827),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 21.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(7.dp))

                    Text(
                        text = item.description,
                        color = Color(0xFF4B5563),
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Czytaj",
                    color = Color(0xFFF97316),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EmptyScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Brak danych",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                fontSize = 15.sp,
                color = Color(0xFF6B7280)
            )
        }
    }
}

suspend fun fetchAppData(): AppData = coroutineScope {
    val homeDeferred = async(Dispatchers.IO) {
        downloadDocument(ZUZEL_URL)
    }

    val poloniaDeferred = async(Dispatchers.IO) {
        downloadDocument(POLONIA_URL)
    }

    val homeDocument = homeDeferred.await()
    val poloniaDocument = poloniaDeferred.await()

    AppData(
        matches = parseMatches(homeDocument),
        news = parseNews(
            document = homeDocument,
            baseUrl = ZUZEL_URL,
            selector = GENERAL_NEWS_SELECTOR
        ),
        poloniaNews = parseNews(
            document = poloniaDocument,
            baseUrl = POLONIA_URL,
            selector = POLONIA_NEWS_SELECTOR
        ),
        updatedAt = currentTimeText()
    )
}

fun downloadDocument(url: String): Document {
    return Jsoup
        .connect(url)
        .userAgent(
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        )
        .referrer("https://www.google.com/")
        .timeout(15_000)
        .get()
}

fun parseMatches(document: Document): List<MatchItem> {
    val items = document.select(MATCHES_SELECTOR)
    val found = linkedMapOf<String, MatchItem>()

    items.forEach { item ->
        val url = item.absUrl("href").ifBlank {
            absoluteUrl(ZUZEL_URL, item.attr("href"))
        }.cleanUrl()

        if (url.isBlank()) {
            return@forEach
        }

        val headerText = item
            .selectFirst(".item-header")
            ?.text()
            .orEmpty()
            .cleanText()

        val status = headerText
            .replace("Żużel", "")
            .cleanText()
            .ifBlank { "Mecz" }

        val bodyText = item
            .selectFirst(".item-body")
            ?.text()
            ?.cleanText()
            ?: item
                .text()
                .replace(headerText, "")
                .cleanText()

        if (bodyText.isBlank()) {
            return@forEach
        }

        val teams = parseTeamsFromScoreText(bodyText)

        if (!found.containsKey(url)) {
            found[url] = MatchItem(
                status = status,
                url = url,
                teams = teams,
                rawText = if (teams.size >= 2) "" else bodyText
            )
        }
    }

    return found.values.take(20)
}

fun parseTeamsFromScoreText(text: String): List<MatchTeam> {
    val normalized = text
        .cleanText()
        .replace(
            Regex("([\\p{L}])([0-9]{1,3})(?=\\s|[\\p{Lu}0-9])"),
            "$1 $2"
        )
        .replace(
            Regex("([0-9]{1,3})([\\p{Lu}])"),
            "$1 $2"
        )

    val regex = Regex(
        "([\\p{L}0-9'\".\\- ]{3,}?)\\s+(\\d{1,3})(?=\\s+[\\p{Lu}0-9]|$)"
    )

    return regex
        .findAll(normalized)
        .mapNotNull { match ->
            val name = match.groupValues.getOrNull(1).orEmpty().cleanText()
            val score = match.groupValues.getOrNull(2).orEmpty().cleanText()

            if (name.length >= 3 && score.isNotBlank()) {
                MatchTeam(
                    name = name,
                    score = score
                )
            } else {
                null
            }
        }
        .take(2)
        .toList()
}

fun parseNews(
    document: Document,
    baseUrl: String,
    selector: String
): List<NewsItem> {
    val anchors = document.select(selector)
    val found = linkedMapOf<String, NewsItem>()

    anchors.forEach { anchor ->
        val rawHref = anchor.attr("href")

        val title = anchor
            .text()
            .ifBlank { anchor.attr("title") }
            .cleanText()

        if (rawHref.isBlank() || title.length < 8) {
            return@forEach
        }

        val url = anchor
            .absUrl("href")
            .ifBlank { absoluteUrl(baseUrl, rawHref) }
            .cleanUrl()

        if (!isValidZuzelArticleUrl(url)) {
            return@forEach
        }

        val teaser = anchor.closest(".teaser")
            ?: anchor.closest("[data-st-area=\"category-news-list\"]")
            ?: anchor.closest("[data-st-area=\"news-list\"]")
            ?: anchor.parent()

        if (!found.containsKey(url)) {
            found[url] = NewsItem(
                title = title,
                category = extractCategoryFromTeaser(teaser),
                date = extractDateFromTeaser(teaser),
                description = extractDescriptionFromTeaser(teaser, title),
                imageUrl = extractImageFromTeaser(teaser, baseUrl),
                url = url
            )
        }
    }

    return found.values.take(40)
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

fun extractCategoryFromTeaser(teaser: Element?): String {
    if (teaser == null) {
        return "Żużel"
    }

    return teaser
        .selectFirst(".caption__category")
        ?.text()
        ?.cleanText()
        .orEmpty()
        .ifBlank { "Żużel" }
}

fun extractDateFromTeaser(teaser: Element?): String {
    if (teaser == null) {
        return ""
    }

    val timeElement = teaser.selectFirst(".caption__time") ?: return ""

    val clone = timeElement.clone()
    clone.select(".caption__divider").remove()

    return clone
        .text()
        .cleanText()
}

fun extractImageFromTeaser(
    teaser: Element?,
    baseUrl: String
): String {
    if (teaser == null) {
        return ""
    }

    val image = teaser.selectFirst(".teaser__img img, img.img__photo, img")
        ?: return ""

    val srcset = image.attr("srcset")
        .ifBlank { image.attr("data-srcset") }
        .cleanText()

    val srcFromSrcset = srcset
        .split(",")
        .firstOrNull()
        ?.trim()
        ?.split(" ")
        ?.firstOrNull()
        ?.trim()
        .orEmpty()

    val rawSource = listOf(
        srcFromSrcset,
        image.attr("src"),
        image.attr("data-src"),
        image.attr("data-original"),
        image.attr("data-lazy-src")
    ).firstOrNull { source ->
        source.isNotBlank()
    }.orEmpty()

    if (rawSource.isBlank()) {
        return ""
    }

    return absoluteUrl(baseUrl, rawSource)
}

fun extractDescriptionFromTeaser(
    teaser: Element?,
    title: String
): String {
    if (teaser == null) {
        return ""
    }

    val selectors = listOf(
        ".teaser__lead",
        ".teaser__description",
        ".teaser__summary",
        ".teaser__text"
    )

    selectors.forEach { selector ->
        val text = teaser
            .selectFirst(selector)
            ?.text()
            ?.cleanText()
            .orEmpty()

        if (text.length >= 25 && text != title) {
            return text.limitLength(180)
        }
    }

    val category = extractCategoryFromTeaser(teaser)
    val date = extractDateFromTeaser(teaser)

    val fallback = teaser
        .text()
        .cleanText()
        .replace(title, "")
        .replace(category, "")
        .replace(date, "")
        .replace("Żużel", "")
        .replace("Czytaj", "")
        .cleanText()

    return if (fallback.length >= 25) {
        fallback.limitLength(180)
    } else {
        ""
    }
}

fun absoluteUrl(
    baseUrl: String,
    rawUrl: String
): String {
    return runCatching {
        URI(baseUrl).resolve(rawUrl).toString()
    }.getOrDefault(rawUrl)
}

fun String.cleanText(): String {
    return this
        .replace("\n", " ")
        .replace("\t", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}

fun String.cleanUrl(): String {
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
