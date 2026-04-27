package com.example.multiplicationtable

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val ZUZEL_URL = "https://sportowefakty.wp.pl/zuzel"
private const val POLONIA_URL = "https://sportowefakty.wp.pl/zuzel/pronergy-polonia-pila"

private const val GENERAL_NEWS_SELECTOR =
    "[data-st-area=\"news-list\"] a.teaser__title[href]"

private const val POLONIA_NEWS_SELECTOR =
    "[data-st-area=\"category-news-list\"] a.teaser__title[href], " +
        "[data-st-area=\"news-list\"] a.teaser__title[href], " +
        ".teaser a.teaser__title[href]"

private const val SCHEDULE_DAY_SELECTOR = ".layout-box"
private const val SCHEDULE_TABLE_SELECTOR = ".event-table"
private const val SCHEDULE_GAME_SELECTOR = "a.game__link[href]"

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

data class ScheduleDateOption(
    val offset: Int,
    val label: String,
    val dateText: String,
    val url: String
)

data class ScheduleDay(
    val date: String,
    val competitions: List<CompetitionGroup>
)

data class CompetitionGroup(
    val name: String,
    val tableUrl: String,
    val games: List<ScheduleGame>
)

data class ScheduleGame(
    val time: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeScore: String,
    val awayScore: String,
    val url: String,
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
    val scheduleDays: List<ScheduleDay>,
    val news: List<NewsItem>,
    val poloniaNews: List<NewsItem>,
    val scheduleUrl: String,
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

    var selectedDayOffset by remember {
        mutableIntStateOf(0)
    }

    var refreshCounter by remember {
        mutableIntStateOf(0)
    }

    var state by remember {
        mutableStateOf<UiState>(UiState.Loading)
    }

    LaunchedEffect(refreshCounter, selectedDayOffset) {
        state = UiState.Loading

        state = try {
            UiState.Success(
                fetchAppData(
                    selectedDayOffset = selectedDayOffset
                )
            )
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

        val fallbackScheduleUrl = scheduleUrlForOffset(selectedDayOffset)

        when (val currentState = state) {
            UiState.Loading -> {
                Toolbar(
                    selectedTab = selectedTab,
                    sourceUrl = when (selectedTab) {
                        AppTab.Matches -> fallbackScheduleUrl
                        AppTab.News -> ZUZEL_URL
                        AppTab.Polonia -> POLONIA_URL
                    },
                    onRefreshClick = {
                        refreshCounter++
                    },
                    onSourceClick = { url ->
                        openUrl(context, url)
                    }
                )

                LoadingScreen()
            }

            is UiState.Error -> {
                Toolbar(
                    selectedTab = selectedTab,
                    sourceUrl = when (selectedTab) {
                        AppTab.Matches -> fallbackScheduleUrl
                        AppTab.News -> ZUZEL_URL
                        AppTab.Polonia -> POLONIA_URL
                    },
                    onRefreshClick = {
                        refreshCounter++
                    },
                    onSourceClick = { url ->
                        openUrl(context, url)
                    }
                )

                ErrorScreen(
                    message = currentState.message,
                    onRetryClick = {
                        refreshCounter++
                    }
                )
            }

            is UiState.Success -> {
                val sourceUrl = when (selectedTab) {
                    AppTab.Matches -> currentState.data.scheduleUrl
                    AppTab.News -> ZUZEL_URL
                    AppTab.Polonia -> POLONIA_URL
                }

                Toolbar(
                    selectedTab = selectedTab,
                    sourceUrl = sourceUrl,
                    onRefreshClick = {
                        refreshCounter++
                    },
                    onSourceClick = { url ->
                        openUrl(context, url)
                    }
                )

                when (selectedTab) {
                    AppTab.Matches -> ScheduleScreen(
                        days = currentState.data.scheduleDays,
                        updatedAt = currentState.data.updatedAt,
                        selectedDayOffset = selectedDayOffset,
                        onDaySelected = { offset ->
                            selectedDayOffset = offset
                        },
                        onGameClick = { game ->
                            openUrl(context, game.url)
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
            text = "Terminarz, newsy i Polonia Piła",
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
        AppTab.values().forEach { tab ->
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
    sourceUrl: String,
    onRefreshClick: () -> Unit,
    onSourceClick: (String) -> Unit
) {
    val sourceText = when (selectedTab) {
        AppTab.Matches -> "Terminarz WP"
        AppTab.News -> "WP"
        AppTab.Polonia -> "Polonia WP"
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
            onClick = {
                onSourceClick(sourceUrl)
            },
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
fun ScheduleScreen(
    days: List<ScheduleDay>,
    updatedAt: String,
    selectedDayOffset: Int,
    onDaySelected: (Int) -> Unit,
    onGameClick: (ScheduleGame) -> Unit
) {
    val dateOptions = remember {
        scheduleDateOptions()
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
            DaySelector(
                options = dateOptions,
                selectedOffset = selectedDayOffset,
                onDaySelected = onDaySelected
            )
        }

        item {
            val gameCount = days.sumOf { day ->
                day.competitions.sumOf { competition ->
                    competition.games.size
                }
            }

            Text(
                text = "Mecze: $gameCount • Odświeżono: $updatedAt",
                color = Color(0xFF6B7280),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }

        if (days.isEmpty()) {
            item {
                EmptyScheduleCard(
                    selectedDayOffset = selectedDayOffset
                )
            }
        } else {
            days.forEach { day ->
                item(
                    key = "date_${day.date}"
                ) {
                    DateHeader(date = day.date)
                }

                day.competitions.forEach { competition ->
                    item(
                        key = "competition_${day.date}_${competition.name}_${competition.games.firstOrNull()?.url.orEmpty()}"
                    ) {
                        CompetitionCard(
                            competition = competition,
                            onGameClick = onGameClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DaySelector(
    options: List<ScheduleDateOption>,
    selectedOffset: Int,
    onDaySelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val selected = option.offset == selectedOffset

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (selected) Color(0xFFF97316) else Color.White
                    )
                    .clickable {
                        onDaySelected(option.offset)
                    }
                    .padding(
                        horizontal = 14.dp,
                        vertical = 10.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = option.label,
                    color = if (selected) Color.White else Color(0xFF111827),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = option.dateText,
                    color = if (selected) Color.White.copy(alpha = 0.9f) else Color(0xFF6B7280),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EmptyScheduleCard(
    selectedDayOffset: Int
) {
    val option = scheduleDateOptionForOffset(selectedDayOffset)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Brak meczów",
                color = Color(0xFF111827),
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Nie znaleziono spotkań dla dnia: ${option.dateText}.",
                color = Color(0xFF6B7280),
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun DateHeader(date: String) {
    Text(
        text = date.ifBlank { "Terminarz" },
        color = Color(0xFF111827),
        fontSize = 22.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = 6.dp,
                bottom = 2.dp
            )
    )
}

@Composable
fun CompetitionCard(
    competition: CompetitionGroup,
    onGameClick: (ScheduleGame) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = competition.name.ifBlank { "Żużel" },
                color = Color(0xFF111827),
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            competition.games.forEachIndexed { index, game ->
                GameRow(
                    game = game,
                    onClick = {
                        onGameClick(game)
                    }
                )

                if (index != competition.games.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun GameRow(
    game: ScheduleGame,
    onClick: () -> Unit
) {
    val hasTwoTeams = game.homeTeam.isNotBlank() && game.awayTeam.isNotBlank()
    val hasScores = game.homeScore.isNotBlank() || game.awayScore.isNotBlank()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(Color(0xFFF9FAFB))
            .clickable(onClick = onClick)
            .padding(11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = game.time.ifBlank { "—" },
            color = Color(0xFF111827),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(54.dp)
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            if (hasTwoTeams) {
                Text(
                    text = game.homeTeam,
                    color = Color(0xFF111827),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = game.awayTeam,
                    color = Color(0xFF111827),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = game.homeTeam.ifBlank {
                        game.rawText
                            .replace(game.time, "")
                            .cleanText()
                            .ifBlank { "Szczegóły wydarzenia" }
                    },
                    color = Color(0xFF111827),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (hasScores) {
            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = game.homeScore.ifBlank { "-" },
                    color = Color(0xFF111827),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(modifier = Modifier.height(5.dp))

                Text(
                    text = game.awayScore.ifBlank { "-" },
                    color = Color(0xFF111827),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
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

suspend fun fetchAppData(
    selectedDayOffset: Int
): AppData = coroutineScope {
    val scheduleUrl = scheduleUrlForOffset(selectedDayOffset)

    val scheduleDeferred = async(Dispatchers.IO) {
        downloadDocument(scheduleUrl)
    }

    val homeDeferred = async(Dispatchers.IO) {
        downloadDocument(ZUZEL_URL)
    }

    val poloniaDeferred = async(Dispatchers.IO) {
        downloadDocument(POLONIA_URL)
    }

    val scheduleDocument = scheduleDeferred.await()
    val homeDocument = homeDeferred.await()
    val poloniaDocument = poloniaDeferred.await()

    AppData(
        scheduleDays = parseScheduleDays(scheduleDocument),
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
        scheduleUrl = scheduleUrl,
        updatedAt = currentTimeText()
    )
}

fun scheduleDateOptions(): List<ScheduleDateOption> {
    return (0..6).map { offset ->
        scheduleDateOptionForOffset(offset)
    }
}

fun scheduleDateOptionForOffset(offset: Int): ScheduleDateOption {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, offset)

    val label = when (offset) {
        0 -> "Dziś"
        1 -> "Jutro"
        else -> "+$offset"
    }

    val dateText = SimpleDateFormat(
        "dd.MM",
        Locale.getDefault()
    ).format(calendar.time)

    return ScheduleDateOption(
        offset = offset,
        label = label,
        dateText = dateText,
        url = scheduleUrlForOffset(offset)
    )
}

fun scheduleUrlForOffset(offset: Int): String {
    val calendar = Calendar.getInstance()
    calendar.add(Calendar.DAY_OF_YEAR, offset)

    val datePath = SimpleDateFormat(
        "yyyy/MM/dd",
        Locale.US
    ).format(calendar.time)

    return "https://sportowefakty.wp.pl/zuzel/terminarz/$datePath"
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

fun parseScheduleDays(document: Document): List<ScheduleDay> {
    val dayBoxes = document.select(SCHEDULE_DAY_SELECTOR)
        .filter { box ->
            box.select(SCHEDULE_TABLE_SELECTOR).isNotEmpty()
        }

    val days = mutableListOf<ScheduleDay>()

    dayBoxes.forEach { box ->
        val date = box
            .selectFirst(".layout-box-title")
            ?.text()
            ?.cleanText()
            .orEmpty()

        val competitions = box
            .select(SCHEDULE_TABLE_SELECTOR)
            .flatMap { table ->
                parseCompetitionGroups(table)
            }

        if (competitions.isNotEmpty()) {
            days.add(
                ScheduleDay(
                    date = date.ifBlank { "Terminarz" },
                    competitions = competitions
                )
            )
        }
    }

    if (days.isNotEmpty()) {
        return days
    }

    val fallbackCompetitions = document
        .select(SCHEDULE_TABLE_SELECTOR)
        .flatMap { table ->
            parseCompetitionGroups(table)
        }

    return if (fallbackCompetitions.isNotEmpty()) {
        listOf(
            ScheduleDay(
                date = "Terminarz",
                competitions = fallbackCompetitions
            )
        )
    } else {
        emptyList()
    }
}

fun parseCompetitionGroups(table: Element): List<CompetitionGroup> {
    val content = table.selectFirst(".event-table__content") ?: table
    val groups = mutableListOf<CompetitionGroup>()

    var currentName = "Żużel"
    var currentTableUrl = ""
    var currentGames = linkedMapOf<String, ScheduleGame>()

    fun flushCurrentGroup() {
        if (currentGames.isNotEmpty()) {
            groups.add(
                CompetitionGroup(
                    name = currentName.ifBlank { "Żużel" },
                    tableUrl = currentTableUrl,
                    games = currentGames.values.toList()
                )
            )
        }

        currentGames = linkedMapOf()
    }

    content.children().forEach { child ->
        val subheader = when {
            child.hasClass("event-table-subheader") -> child
            child.selectFirst("> .event-table-subheader") != null -> child.selectFirst("> .event-table-subheader")
            else -> null
        }

        if (subheader != null) {
            flushCurrentGroup()

            currentName = extractCompetitionName(subheader)
            currentTableUrl = subheader
                .selectFirst(".event-table-subheader__link[href]")
                ?.absUrl("href")
                ?.cleanUrl()
                .orEmpty()

            return@forEach
        }

        val gameElements = child.select(SCHEDULE_GAME_SELECTOR)

        gameElements.forEach { gameElement ->
            val game = parseScheduleGame(gameElement)

            if (game != null && !currentGames.containsKey(game.url)) {
                currentGames[game.url] = game
            }
        }
    }

    flushCurrentGroup()

    if (groups.isNotEmpty()) {
        return groups
    }

    val fallbackGames = linkedMapOf<String, ScheduleGame>()

    table.select(SCHEDULE_GAME_SELECTOR).forEach { gameElement ->
        val game = parseScheduleGame(gameElement)

        if (game != null && !fallbackGames.containsKey(game.url)) {
            fallbackGames[game.url] = game
        }
    }

    return if (fallbackGames.isNotEmpty()) {
        listOf(
            CompetitionGroup(
                name = extractCompetitionName(table),
                tableUrl = "",
                games = fallbackGames.values.toList()
            )
        )
    } else {
        emptyList()
    }
}

fun extractCompetitionName(element: Element): String {
    val directTitle = element
        .selectFirst(".event-table-subheader__title")
        ?.text()
        ?.cleanText()

    if (!directTitle.isNullOrBlank()) {
        return directTitle
    }

    val clone = element.clone()
    clone.select(".event-table-subheader__link").remove()

    return clone
        .text()
        .replace("Tabela", "")
        .cleanText()
        .ifBlank { "Żużel" }
}

fun parseScheduleGame(gameElement: Element): ScheduleGame? {
    val url = gameElement
        .absUrl("href")
        .ifBlank {
            absoluteUrl(ZUZEL_URL, gameElement.attr("href"))
        }
        .cleanUrl()

    if (url.isBlank()) {
        return null
    }

    val time = gameElement
        .selectFirst(".game__time")
        ?.text()
        ?.cleanText()
        .orEmpty()

    val teamNames = gameElement
        .select(".game__team")
        .map { team ->
            extractTeamName(team)
        }
        .filter { teamName ->
            teamName.isNotBlank()
        }

    val scores = extractScoresFromGame(gameElement)

    val rawText = gameElement
        .text()
        .cleanText()

    if (teamNames.isEmpty() && rawText.isBlank()) {
        return null
    }

    return ScheduleGame(
        time = time,
        homeTeam = teamNames.getOrNull(0).orEmpty(),
        awayTeam = teamNames.getOrNull(1).orEmpty(),
        homeScore = scores.getOrNull(0).orEmpty(),
        awayScore = scores.getOrNull(1).orEmpty(),
        url = url,
        rawText = rawText
    )
}

fun extractTeamName(teamElement: Element): String {
    val clone = teamElement.clone()

    clone
        .select(".game__score, .game__team-score, .game__result, .game__points, [class*=score], [class*=result]")
        .remove()

    return clone
        .text()
        .cleanText()
}

fun extractScoresFromGame(gameElement: Element): List<String> {
    val scoreText = gameElement
        .select(".game__score, .game__team-score, .game__result, .game__points, [class*=score], [class*=result]")
        .eachText()
        .joinToString(" ")
        .cleanText()

    if (scoreText.isBlank()) {
        return emptyList()
    }

    return scoreText
        .split(Regex("\\s+"))
        .map { token ->
            token.trim()
        }
        .filter { token ->
            token == "-" || token.matches(Regex("\\d{1,3}"))
        }
        .takeLast(2)
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
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url)
        )

        context.startActivity(intent)
    } catch (exception: Exception) {
        // Brak przeglądarki lub niepoprawny adres — celowo bez crasha.
    }
}
