package fi.veneappi.app

import android.app.Application
import androidx.room.Room
import fi.veneappi.app.billing.BillingManager
import fi.veneappi.app.billing.CombinedRoutePremiumAccess
import fi.veneappi.app.billing.PremiumAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import fi.veneappi.app.data.ais.DigitrafficAisRepository
import fi.veneappi.app.data.harbors.OverpassHarborClient
import fi.veneappi.app.data.lightning.CompositeLightningRepository
import fi.veneappi.app.data.lightning.FmiLightningRepository
import fi.veneappi.app.data.lightning.SmhiLightningRepository
import fi.veneappi.app.data.marine.MarineTextRepository
import fi.veneappi.app.data.radar.CompositeRadarRepository
import fi.veneappi.app.data.radar.FmiForecastRadarRepository
import fi.veneappi.app.data.radar.StormRadarPrefetcher
import fi.veneappi.app.ui.map.MapHttp
import fi.veneappi.app.ui.map.MapTileWarmup
import fi.veneappi.app.data.radar.FmiRadarRepository
import fi.veneappi.app.data.radar.MetNorwayRadarRepository
import fi.veneappi.app.data.radar.SmhiRadarRepository
import fi.veneappi.app.data.net.NetworkConnectivityMonitor
import fi.veneappi.app.data.net.WeatherHttpClient
import fi.veneappi.app.data.net.WeatherRepository
import fi.veneappi.app.data.offline.OfflineAreaPackDownloader
import fi.veneappi.app.data.prefs.UserPreferencesRepository
import fi.veneappi.app.data.room.VeneappiDatabase
import fi.veneappi.app.review.PlayInAppReviewCoordinator
import kotlinx.serialization.json.Json
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

class VeneappiApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this, null, WellKnownTileServer.MapLibre)
        appContainer = AppContainer(this)
        appContainer.billingManager.startConnection()
        appContainer.warmStartupMapCaches()
    }

    override fun onTerminate() {
        appContainer.billingManager.endConnection()
        super.onTerminate()
    }
}

class AppContainer(
    application: Application,
) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val database: VeneappiDatabase =
        Room.databaseBuilder(application, VeneappiDatabase::class.java, "veneappi.db")
            .fallbackToDestructiveMigration()
            .build()

    val weatherRepository =
        WeatherRepository(
            http = WeatherHttpClient(okHttpClient = null, json = json),
            cacheDao = database.forecastCacheDao(),
            json = json,
        )

    val marineTextRepository = MarineTextRepository(json = json)

    val fmiRadarRepository = FmiRadarRepository()

    val metNorwayRadarRepository = MetNorwayRadarRepository(json = json)

    val smhiRadarRepository = SmhiRadarRepository()

    val fmiForecastRadarRepository =
        FmiForecastRadarRepository(appContext = application.applicationContext)

    val compositeRadarRepository =
        CompositeRadarRepository(
            fmiRadarRepository = fmiRadarRepository,
            metNorwayRadarRepository = metNorwayRadarRepository,
            smhiRadarRepository = smhiRadarRepository,
            fmiForecastRadarRepository = fmiForecastRadarRepository,
        )

    val fmiLightningRepository = FmiLightningRepository()

    val smhiLightningRepository = SmhiLightningRepository()

    val compositeLightningRepository =
        CompositeLightningRepository(
            fmi = fmiLightningRepository,
            smhi = smhiLightningRepository,
        )

    val stormRadarPrefetcher =
        StormRadarPrefetcher(
            radarRepository = compositeRadarRepository,
            lightningRepository = compositeLightningRepository,
        )

    val mapTileWarmup = MapTileWarmup(MapHttp.install(application))

    val userPreferencesRepository = UserPreferencesRepository(application)

    val playInAppReviewCoordinator =
        PlayInAppReviewCoordinator(
            applicationId = application.packageName,
            userPreferencesRepository = userPreferencesRepository,
            scope = applicationScope,
        )

    val networkConnectivityMonitor = NetworkConnectivityMonitor(application)

    val offlineAreaPackDownloader =
        OfflineAreaPackDownloader(
            weatherRepository = weatherRepository,
            mapTileWarmup = mapTileWarmup,
            marineTextRepository = marineTextRepository,
            packDao = database.offlineAreaPackDao(),
        )

    val overpassHarborClient = OverpassHarborClient(jsonParser = json)

    val digitrafficAisRepository = DigitrafficAisRepository(json = json)

    val billingManager = BillingManager(application)

    val premiumAccess: PremiumAccess =
        CombinedRoutePremiumAccess(
            billingManager = billingManager,
            userPreferencesRepository = userPreferencesRepository,
            appScope = appScope,
        )

    /** Helsinki default — matches [VeneappiUiState] before GPS; overlaps splash (~5 s). */
    fun warmStartupMapCaches(
        lat: Double = 60.1453,
        lon: Double = 24.9884,
    ) {
        applicationScope.launch {
            runCatching { mapTileWarmup.warm(lat, lon) }
            stormRadarPrefetcher.schedule(applicationScope, lat, lon)
        }
    }
}
