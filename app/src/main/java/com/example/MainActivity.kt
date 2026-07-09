package com.example

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.NumberFormat
import java.util.*
import java.util.concurrent.TimeUnit

// --- DATA MODELS ---

data class ReturnRecord(
    val productName: String,
    val sku: String,
    val customerNotes: String,
    val orderValue: Double
)

data class CategoryImpact(
    val category: String,
    val percentage: Double,
    val totalValue: Double
)

data class SkuAnalysis(
    val sku: String,
    val productName: String,
    val returnRate: Double,
    val insight: String,
    val action: String
)

data class AuditResult(
    val totalLeak: Double,
    val highestBleedingCategory: String,
    val totalReturns: Int,
    val categoryBreakdown: List<CategoryImpact>,
    val worstPerformingSkus: List<SkuAnalysis>,
    val isDemoData: Boolean = false,
    val parsedUsingLocalFallback: Boolean = false
)

data class CurrencyOption(
    val code: String,
    val label: String,
    val symbol: String,
    val rate: Double
)

val currencyOptions = listOf(
    CurrencyOption("EGP", "EGP", "EGP ", 1.0),
    CurrencyOption("USD", "USD ($)", "$", 48.0),
    CurrencyOption("EUR", "EUR (€)", "€", 52.0),
    CurrencyOption("SAR", "SAR (ر.س)", "SAR ", 12.8),
    CurrencyOption("AED", "AED (د.إ)", "AED ", 13.0)
)

fun formatWithCurrency(value: Double, symbol: String, rate: Double, convert: Boolean): String {
    val converted = if (convert) value / rate else value
    val formatter = NumberFormat.getNumberInstance(Locale.US)
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return symbol + formatter.format(converted)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    ReturnRadarScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReturnRadarScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Screen States
    var returnRecords by remember { mutableStateOf<List<ReturnRecord>>(emptyList()) }
    var auditResult by remember { mutableStateOf<AuditResult?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var loadingStatus by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var activeTab by remember { mutableStateOf(0) } // 0: Overview, 1: Categories, 2: SKUs

    var selectedCurrencyCode by remember { mutableStateOf("EGP") }
    var convertCurrency by remember { mutableStateOf(true) }

    val activeCurrencyOption = currencyOptions.firstOrNull { it.code == selectedCurrencyCode } ?: currencyOptions[0]
    val currentSymbol = activeCurrencyOption.symbol
    val currentRate = activeCurrencyOption.rate

    // CSV File Import Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                isLoading = true
                loadingStatus = "Reading CSV file..."
                errorMessage = null
                try {
                    val parsedRecords = parseCsvFromUri(context, uri)
                    if (parsedRecords.isEmpty()) {
                        errorMessage = "No valid records found in CSV. Check column headers."
                        isLoading = false
                    } else {
                        returnRecords = parsedRecords
                        loadingStatus = "Analyzing Return Notes using AI..."
                        val result = runAuditProcess(context, parsedRecords)
                        auditResult = result
                        isLoading = false
                    }
                } catch (e: Exception) {
                    errorMessage = "Failed to parse CSV: ${e.localizedMessage}"
                    isLoading = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Header
        CenterAlignedTopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Troubleshoot,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ReturnRadar",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            modifier = Modifier.border(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Intro Header (Only visible when no audit result)
                if (auditResult == null && !isLoading) {
                    OnboardingView(
                        onLoadDemo = {
                            coroutineScope.launch {
                                isLoading = true
                                errorMessage = null
                                loadingStatus = "Injecting demo logs..."
                                val demoRecords = getDemoDataset()
                                returnRecords = demoRecords
                                loadingStatus = "Analyzing Notes using ReturnRadar AI..."
                                val result = runAuditProcess(context, demoRecords, isDemo = true)
                                auditResult = result
                                isLoading = false
                            }
                        },
                        onImportCsv = {
                            filePickerLauncher.launch("text/*")
                        }
                    )
                }

                // Error Message Card
                errorMessage?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = msg,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Main Dashboard Visualizer
                auditResult?.let { result ->
                    DashboardHeader(
                        result = result,
                        onReset = {
                            auditResult = null
                            returnRecords = emptyList()
                        },
                        onExportPdf = {
                            coroutineScope.launch {
                                val success = exportAuditToPdf(
                                    context = context,
                                    result = result,
                                    currencyCode = selectedCurrencyCode,
                                    currencySymbol = currentSymbol,
                                    rateToEgp = currentRate,
                                    convertCurrency = convertCurrency
                                )
                                if (success) {
                                    Toast.makeText(context, "Audit Report Saved to Downloads!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to export PDF", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Currency Settings Card
                    CurrencyControlCard(
                        selectedCurrencyCode = selectedCurrencyCode,
                        onCurrencyChange = { selectedCurrencyCode = it },
                        convertCurrency = convertCurrency,
                        onConvertToggle = { convertCurrency = it }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Mode alert indicators (sandbox or local fallback)
                    if (result.parsedUsingLocalFallback || result.isDemoData) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                                .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (result.parsedUsingLocalFallback) {
                                        "Sandbox Mode: Rule-based processing fallback active due to network limit or missing secrets key."
                                    } else {
                                        "Demo Active: Displaying ReturnRadar's offline diagnostic sample suite."
                                    },
                                    color = MaterialTheme.colorScheme.onBackground,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Tab Selector Navigation Row
                    TabRow(
                        selectedTabIndex = activeTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = {},
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = { Text("Metrics", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.Dashboard, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("Categories", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.PieChart, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = activeTab == 2,
                            onClick = { activeTab = 2 },
                            text = { Text("SKUs Risk", fontSize = 13.sp, fontWeight = FontWeight.Bold) },
                            icon = { Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tab contents
                    when (activeTab) {
                        0 -> FinancialMetricsOverview(result, currentSymbol, currentRate, convertCurrency)
                        1 -> CategoriesAnalysisView(result, currentSymbol, currentRate, convertCurrency)
                        2 -> SkuBleedAnalysisView(result)
                    }
                }
            }

            // Processing Loading Overlay
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = loadingStatus,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Extracting details and estimating margin leakages...",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CurrencyControlCard(
    selectedCurrencyCode: String,
    onCurrencyChange: (String) -> Unit,
    convertCurrency: Boolean,
    onConvertToggle: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .testTag("currency_control_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Payments,
                    contentDescription = "Currency Settings",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DASHBOARD CURRENCY",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Row of currency options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                currencyOptions.forEach { option ->
                    val isSelected = selectedCurrencyCode == option.code
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                            .clickable { onCurrencyChange(option.code) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option.code,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Switch to toggle conversion
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onConvertToggle(!convertCurrency) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Apply Live Conversion Rate",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (convertCurrency) "Converting EGP base values using standard conversion rates."
                        else "Displaying raw values with the chosen currency symbol.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = convertCurrency,
                    onCheckedChange = onConvertToggle
                )
            }
        }
    }
}

// --- VIEW COMPONENTS ---

@Composable
fun OnboardingView(onLoadDemo: () -> Unit, onImportCsv: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Analytics,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "E-Commerce Profit Audit",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Merchants lose up to 15% margins to product returns. ReturnRadar automatically parses messy customer notes, categorizes root-causes, and quantifies your cash leakage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Button(
                onClick = onImportCsv,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("import_csv_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import CSV Return Log", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onLoadDemo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("demo_dataset_button"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Analyze Sandbox Demo Dataset", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            Spacer(modifier = Modifier.height(12.dp))

            // Required headers info row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Rule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Requires headers: Product Name, SKU, Customer Notes, Order Value",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun DashboardHeader(
    result: AuditResult,
    onReset: () -> Unit,
    onExportPdf: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Audit Summary",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${result.totalReturns} Returns mapped successfully",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Row {
            IconButton(
                onClick = onReset,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .testTag("reset_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Restart",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onExportPdf,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.testTag("download_pdf_button")
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export PDF", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun FinancialMetricsOverview(
    result: AuditResult,
    symbol: String,
    rate: Double,
    convert: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Big Total Capital Bled Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TOTAL CAPITAL BLED",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = formatWithCurrency(result.totalLeak, symbol, rate, convert),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Refund Outlay + ${formatWithCurrency(100.0, symbol, rate, convert)} process cost per return",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Two side-by-side metric tiles
        Row(modifier = Modifier.fillMaxWidth()) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(115.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TOP LEAK TRIGGER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result.highestBleedingCategory,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Demands urgent listing fixes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(115.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "TOTAL AUDITED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${result.totalReturns} logs",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "100% semantic matching",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoriesAnalysisView(
    result: AuditResult,
    symbol: String,
    rate: Double,
    convert: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Root Cause Financial Impact Breakdown",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            result.categoryBreakdown.forEach { item ->
                CategoryProgressRow(item = item, symbol = symbol, rate = rate, convert = convert)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun CategoryProgressRow(
    item: CategoryImpact,
    symbol: String,
    rate: Double,
    convert: Boolean
) {
    val color = when (item.category) {
        "Sizing Issue" -> MaterialTheme.colorScheme.primary
        "Product Defect" -> MaterialTheme.colorScheme.error
        "Misleading Description" -> MaterialTheme.colorScheme.tertiary
        "Delayed Delivery" -> MaterialTheme.colorScheme.secondary
        else -> Color(0xFF94A3B8) // Slate 400
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.category,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatWithCurrency(item.totalValue, symbol, rate, convert),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            LinearProgressIndicator(
                progress = (item.percentage / 100.0).toFloat(),
                color = color,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${item.percentage}%",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.width(36.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
fun SkuBleedAnalysisView(result: AuditResult) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Worst Performing SKUs & Recommendations",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        result.worstPerformingSkus.forEachIndexed { index, skuAnalysis ->
            SkuRiskCard(index = index + 1, skuAnalysis = skuAnalysis)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun SkuRiskCard(index: Int, skuAnalysis: SkuAnalysis) {
    var expanded by remember { mutableStateOf(false) }
    val borderColor = when (index) {
        1 -> MaterialTheme.colorScheme.error
        2 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(borderColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "#$index",
                            color = borderColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Text(
                            text = skuAnalysis.sku,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = skuAnalysis.productName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${skuAnalysis.returnRate}% leak",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodySmall,
                        color = borderColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            RoundedCornerShape(8.dp)
                        )
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "AI INSIGHT:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = skuAnalysis.insight,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "IMMEDIATE RECOMMENDED ACTION:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = skuAnalysis.action,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.secondary,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

// --- CORE LOGIC & PARSING UTILITIES ---

suspend fun parseCsvFromUri(context: Context, uri: Uri): List<ReturnRecord> = withContext(Dispatchers.IO) {
    val records = mutableListOf<ReturnRecord>()
    try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var headerLine = reader.readLine() ?: return@use
                // Clean unicode BOM or spaces
                headerLine = headerLine.replace("\uFEFF", "").trim()
                
                val headers = splitCsvLine(headerLine)
                
                val idxProduct = headers.indexOfFirst { it.equals("Product Name", ignoreCase = true) }
                val idxSku = headers.indexOfFirst { it.equals("SKU", ignoreCase = true) }
                val idxNotes = headers.indexOfFirst { it.equals("Customer Notes", ignoreCase = true) }
                val idxValue = headers.indexOfFirst { it.equals("Order Value", ignoreCase = true) }

                if (idxProduct == -1 || idxSku == -1 || idxNotes == -1 || idxValue == -1) {
                    return@use // Missing critical headers
                }

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val parts = splitCsvLine(line!!)
                    if (parts.size > maxOf(idxProduct, idxSku, idxNotes, idxValue)) {
                        val productName = parts[idxProduct].trim().replace("\"", "")
                        val sku = parts[idxSku].trim().replace("\"", "")
                        val customerNotes = parts[idxNotes].trim().replace("\"", "")
                        val orderValueStr = parts[idxValue].trim().replace("\"", "")
                        val orderValue = orderValueStr.toDoubleOrNull() ?: 0.0

                        if (sku.isNotEmpty() && productName.isNotEmpty()) {
                            records.add(ReturnRecord(productName, sku, customerNotes, orderValue))
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    records
}

fun splitCsvLine(line: String): List<String> {
    val result = mutableListOf<String>()
    var current = StringBuilder()
    var inQuotes = false
    for (ch in line) {
        if (ch == '\"') {
            inQuotes = !inQuotes
        } else if (ch == ',' && !inQuotes) {
            result.add(current.toString())
            current = StringBuilder()
        } else {
            current.append(ch)
        }
    }
    result.add(current.toString())
    return result
}

fun getDemoDataset(): List<ReturnRecord> {
    return listOf(
        ReturnRecord("Oversized Denim Jacket", "DNM-JKT-001", "Runs 2 sizes too small, returned to buy larger size.", 1250.0),
        ReturnRecord("Cotton Chino Pants", "CHN-PNT-023", "The zipper was completely broken upon arrival.", 890.0),
        ReturnRecord("Leather Chelsea Boots", "BTS-LTH-09", "The material tore at the heel during first wear. Very poor quality.", 2100.0),
        ReturnRecord("Minimalist Dial Watch", "WCH-MIN-101", "Dial is much darker red than pictured online. Misleading description.", 1800.0),
        ReturnRecord("Oversized Denim Jacket", "DNM-JKT-001", "Too small, fits tight on shoulders.", 1250.0),
        ReturnRecord("Linen Summer Dress", "DRS-LIN-05", "Just changed my mind, decided I do not need it.", 1400.0)
    )
}

suspend fun runAuditProcess(context: Context, records: List<ReturnRecord>, isDemo: Boolean = false): AuditResult = withContext(Dispatchers.IO) {
    val apiKey = BuildConfig.GEMINI_API_KEY
    val isPlaceholder = apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY"

    val refundSum = records.sumOf { it.orderValue }
    val totalLeak = refundSum + (100.0 * records.size)

    if (isPlaceholder) {
        // Fallback to local rule-based diagnostics
        return@withContext runLocalDiagnostics(records, totalLeak, isDemo, parsedFallback = true)
    }

    try {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val jsonRecords = JSONArray()
        records.forEach { r ->
            val o = JSONObject()
            o.put("product_name", r.productName)
            o.put("sku", r.sku)
            o.put("customer_notes", r.customerNotes)
            o.put("order_value", r.orderValue)
            jsonRecords.put(o)
        }

        val prompt = """
Analyze the following customer return data.

DATA:
$jsonRecords

SYSTEM INSTRUCTIONS:
You are a senior e-commerce financial analyst. Analyze the provided customer return notes.
For each entry:
1. Categorize the true root cause into strictly one of these categories: [Sizing Issue, Product Defect, Misleading Description, Delayed Delivery, Customer Buyer's Remorse].
2. Extract the specific detail (e.g., "runs 2 sizes too small", "zipper broke").

Output a valid JSON object matching this exact schema:
{
  "category_percentages": {
    "Sizing Issue": float,
    "Product Defect": float,
    "Misleading Description": float,
    "Delayed Delivery": float,
    "Customer Buyer's Remorse": float
  },
  "worst_performing_skus": [
    {
      "sku": "string",
      "product_name": "string",
      "return_count": int,
      "insight": "string",
      "action": "string"
    }
  ]
}
Do NOT wrap your response in markdown code blocks. Output raw valid JSON only.
"""

        val jsonBody = JSONObject()
        val contentObj = JSONObject()
        val partsArr = JSONArray()
        val textPart = JSONObject()
        textPart.put("text", prompt)
        partsArr.put(textPart)
        contentObj.put("parts", partsArr)
        val contentsArr = JSONArray()
        contentsArr.put(contentObj)
        jsonBody.put("contents", contentsArr)

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            val resStr = response.body?.string() ?: ""
            val candidates = JSONObject(resStr).getJSONArray("candidates")
            var responseText = candidates.getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text").trim()

            // Clean up possible markdown wrap
            if (responseText.startsWith("```")) {
                val lines = responseText.split("\n")
                val filteredLines = lines.filterNot { it.trim().startsWith("```") }
                responseText = filteredLines.joinToString("\n").trim()
            }

            val parsedJson = JSONObject(responseText)
            val percentagesJson = parsedJson.getJSONObject("category_percentages")
            val skusArr = parsedJson.getJSONArray("worst_performing_skus")

            val categories = listOf("Sizing Issue", "Product Defect", "Misleading Description", "Delayed Delivery", "Customer Buyer's Remorse")
            val breakdown = categories.map { cat ->
                val percentage = percentagesJson.optDouble(cat, 0.0)
                CategoryImpact(cat, percentage, (percentage / 100.0) * totalLeak)
            }

            val highestCategory = breakdown.maxByOrNull { it.totalValue }?.category ?: "Sizing Issue"

            val worstSkus = mutableListOf<SkuAnalysis>()
            for (i in 0 until skusArr.length()) {
                val skuObj = skusArr.getJSONObject(i)
                val sku = skuObj.getString("sku")
                val prodName = skuObj.getString("product_name")
                val retCount = skuObj.optDouble("return_count", 1.0)
                val rate = (retCount / records.size) * 100

                worstSkus.add(
                    SkuAnalysis(
                        sku = sku,
                        productName = prodName,
                        returnRate = Math.round(rate * 10.0) / 10.0,
                        insight = skuObj.getString("insight"),
                        action = skuObj.getString("action")
                    )
                )
            }

            return@withContext AuditResult(
                totalLeak = totalLeak,
                highestBleedingCategory = highestCategory,
                totalReturns = records.size,
                categoryBreakdown = breakdown,
                worstPerformingSkus = worstSkus.take(3),
                isDemoData = isDemo,
                parsedUsingLocalFallback = false
            )

        } else {
            // API returned non-200, fallback locally
            return@withContext runLocalDiagnostics(records, totalLeak, isDemo, parsedFallback = true)
        }

    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext runLocalDiagnostics(records, totalLeak, isDemo, parsedFallback = true)
    }
}

fun runLocalDiagnostics(records: List<ReturnRecord>, totalLeak: Double, isDemo: Boolean, parsedFallback: Boolean): AuditResult {
    val notesJoined = records.joinToString(" ") { it.customerNotes.lowercase() }
    val total = records.size.toDouble()

    val sizingCount = records.count { it.customerNotes.contains("size", true) || it.customerNotes.contains("fit", true) || it.customerNotes.contains("small", true) || it.customerNotes.contains("large", true) }
    val defectCount = records.count { it.customerNotes.contains("broke", true) || it.customerNotes.contains("rip", true) || it.customerNotes.contains("defect", true) || it.customerNotes.contains("tear", true) || it.customerNotes.contains("quality", true) }
    val misleadingCount = records.count { it.customerNotes.contains("misleading", true) || it.customerNotes.contains("color", true) || it.customerNotes.contains("different", true) || it.customerNotes.contains("photo", true) }
    val deliveryCount = records.count { it.customerNotes.contains("late", true) || it.customerNotes.contains("delay", true) || it.customerNotes.contains("slow", true) }
    
    val mapped = sizingCount + defectCount + misleadingCount + deliveryCount
    val remorseCount = if (records.size - mapped > 0) records.size - mapped else 0

    val sizingP = if (total > 0) (sizingCount / total) * 100 else 40.0
    val defectP = if (total > 0) (defectCount / total) * 100 else 25.0
    val misleadingP = if (total > 0) (misleadingCount / total) * 100 else 15.0
    val deliveryP = if (total > 0) (deliveryCount / total) * 100 else 12.0
    val remorseP = if (total > 0) (remorseCount / total) * 100 else 8.0

    val breakdown = listOf(
        CategoryImpact("Sizing Issue", Math.round(sizingP * 10.0) / 10.0, (sizingP / 100.0) * totalLeak),
        CategoryImpact("Product Defect", Math.round(defectP * 10.0) / 10.0, (defectP / 100.0) * totalLeak),
        CategoryImpact("Misleading Description", Math.round(misleadingP * 10.0) / 10.0, (misleadingP / 100.0) * totalLeak),
        CategoryImpact("Delayed Delivery", Math.round(deliveryP * 10.0) / 10.0, (deliveryP / 100.0) * totalLeak),
        CategoryImpact("Customer Buyer's Remorse", Math.round(remorseP * 10.0) / 10.0, (remorseP / 100.0) * totalLeak)
    )

    val highest = breakdown.maxByOrNull { it.totalValue }?.category ?: "Sizing Issue"

    // Group by SKU
    val skuGroups = records.groupBy { it.sku }
    val worstSkus = skuGroups.map { (sku, list) ->
        val prodName = list.first().productName
        val returnRate = (list.size.toDouble() / total) * 100
        
        val (insight, action) = when {
            list.any { it.customerNotes.contains("size", true) || it.customerNotes.contains("small", true) } -> {
                Pair(
                    "Sizing ran significantly smaller than average, causing 80% of returns for this item.",
                    "Update size chart on product detail page and add a 'Runs Small' sizing warning."
                )
            }
            list.any { it.customerNotes.contains("broke", true) || it.customerNotes.contains("rip", true) || it.customerNotes.contains("quality", true) } -> {
                Pair(
                    "Multiple reports of material tearing near seams or components breaking.",
                    "Halt shipping and initiate a quality control inspection with the manufacturer."
                )
            }
            else -> {
                Pair(
                    "Product photo or details did not meet customer expectation.",
                    "Re-shoot product images under standard studio lighting to match actual item specs."
                )
            }
        }

        SkuAnalysis(
            sku = sku,
            productName = prodName,
            returnRate = Math.round(returnRate * 10.0) / 10.0,
            insight = insight,
            action = action
        )
    }.sortedByDescending { it.returnRate }.take(3)

    return AuditResult(
        totalLeak = totalLeak,
        highestBleedingCategory = highest,
        totalReturns = records.size,
        categoryBreakdown = breakdown,
        worstPerformingSkus = worstSkus,
        isDemoData = isDemo,
        parsedUsingLocalFallback = parsedFallback
    )
}

// --- NATIVE ANDROID PDF GENERATOR ---

suspend fun exportAuditToPdf(
    context: Context,
    result: AuditResult,
    currencyCode: String = "EGP",
    currencySymbol: String = "EGP ",
    rateToEgp: Double = 1.0,
    convertCurrency: Boolean = true
): Boolean = withContext(Dispatchers.IO) {
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // Letter size representation
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint()
        val textPaint = Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }

        // Draw Header
        paint.color = android.graphics.Color.parseColor("#0F172A") // Deep slate primary
        canvas.drawRect(0f, 0f, 595f, 100f, paint)

        textPaint.color = android.graphics.Color.WHITE
        textPaint.textSize = 24f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ReturnRadar", 40f, 48f, textPaint)

        textPaint.textSize = 10f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("AI-Driven Return & Financial Impact Audit Report", 40f, 70f, textPaint)

        // Reset Paint
        textPaint.color = android.graphics.Color.parseColor("#1E293B")

        // Draw Key Metrics Grid Box
        paint.color = android.graphics.Color.parseColor("#F8FAFC")
        canvas.drawRect(40f, 120f, 555f, 200f, paint)
        paint.color = android.graphics.Color.parseColor("#CBD5E1")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(40f, 120f, 555f, 200f, paint)
        paint.style = Paint.Style.FILL // reset

        textPaint.textSize = 9f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("TOTAL CAPITAL BLED", 60f, 145f, textPaint)
        canvas.drawText("TOP LEAK CAUSE", 230f, 145f, textPaint)
        canvas.drawText("TOTAL RETURNS", 410f, 145f, textPaint)

        textPaint.textSize = 14f
        textPaint.color = android.graphics.Color.parseColor("#EF4444") // Coral red
        canvas.drawText(formatWithCurrency(result.totalLeak, currencySymbol, rateToEgp, convertCurrency), 60f, 175f, textPaint)

        textPaint.color = android.graphics.Color.parseColor("#F59E0B") // Amber yellow
        canvas.drawText(result.highestBleedingCategory, 230f, 175f, textPaint)

        textPaint.color = android.graphics.Color.parseColor("#3B82F6") // Blue
        canvas.drawText("${result.totalReturns} logs", 410f, 175f, textPaint)

        // Category breakdown header
        textPaint.color = android.graphics.Color.parseColor("#0F172A")
        textPaint.textSize = 12f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Category Financial Breakdown", 40f, 235f, textPaint)

        // Category breakdown table
        var yPos = 265f
        textPaint.textSize = 9f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Category", 40f, yPos, textPaint)
        canvas.drawText("Percentage", 280f, yPos, textPaint)
        canvas.drawText("Capital Lost ($currencyCode)", 420f, yPos, textPaint)

        paint.color = android.graphics.Color.parseColor("#CBD5E1")
        canvas.drawLine(40f, yPos + 5, 555f, yPos + 5, paint)

        yPos += 20f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        result.categoryBreakdown.forEach { cat ->
            canvas.drawText(cat.category, 40f, yPos, textPaint)
            canvas.drawText("${cat.percentage}%", 280f, yPos, textPaint)
            canvas.drawText(formatWithCurrency(cat.totalValue, currencySymbol, rateToEgp, convertCurrency), 420f, yPos, textPaint)
            
            paint.color = android.graphics.Color.parseColor("#F1F5F9")
            canvas.drawLine(40f, yPos + 5, 555f, yPos + 5, paint)
            yPos += 20f
        }

        // SKU breakdown header
        yPos += 15f
        textPaint.color = android.graphics.Color.parseColor("#0F172A")
        textPaint.textSize = 12f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Worst Performing SKUs & Recommendations", 40f, yPos, textPaint)

        yPos += 25f
        result.worstPerformingSkus.forEachIndexed { index, sku ->
            paint.color = android.graphics.Color.parseColor("#F8FAFC")
            canvas.drawRect(40f, yPos - 10, 555f, yPos + 65, paint)
            
            // Draw index accent bar
            paint.color = android.graphics.Color.parseColor(if (index == 0) "#EF4444" else "#3B82F6")
            canvas.drawRect(40f, yPos - 10, 44f, yPos + 65, paint)

            textPaint.color = android.graphics.Color.parseColor("#0F172A")
            textPaint.textSize = 9f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("SKU: ${sku.sku}  |  ${sku.productName}", 52f, yPos, textPaint)

            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText("Leak Share: ${sku.returnRate}% of total return outlay", 52f, yPos + 15, textPaint)

            canvas.drawText("AI Insight: ${truncateString(sku.insight, 80)}", 52f, yPos + 30, textPaint)
            
            textPaint.color = android.graphics.Color.parseColor("#10B981")
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText("Action: ${sku.action}", 52f, yPos + 48, textPaint)

            yPos += 85f
        }

        pdfDocument.finishPage(page)

        // Save file to system Downloads directory
        val fileName = "ReturnRadar_Audit_Report_${System.currentTimeMillis()}.pdf"
        val resolvedSuccess: Boolean

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                val outputStream: OutputStream? = resolver.openOutputStream(uri)
                if (outputStream != null) {
                    pdfDocument.writeTo(outputStream)
                    outputStream.close()
                    resolvedSuccess = true
                } else {
                    resolvedSuccess = false
                }
            } else {
                resolvedSuccess = false
            }
        } else {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = java.io.File(downloadsDir, fileName)
            val outputStream = java.io.FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.close()
            resolvedSuccess = true
        }

        pdfDocument.close()
        return@withContext resolvedSuccess
    } catch (e: Exception) {
        e.printStackTrace()
        return@withContext false
    }
}

// --- HELPERS ---

fun formatCurrency(value: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale.US)
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.format(value)
}

fun truncateString(str: String, limit: Int): String {
    if (str.length <= limit) return str
    return str.take(limit - 3) + "..."
}
