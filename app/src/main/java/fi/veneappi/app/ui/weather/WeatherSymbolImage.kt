package fi.veneappi.app.ui.weather

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import fi.veneappi.app.domain.FmiWeatherSymbol

@Composable
fun WeatherSymbolImage(
    symbolCode: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    if (symbolCode == null) {
        Box(modifier = modifier.size(size))
        return
    }
    val url = FmiWeatherSymbol.imageUrl(symbolCode)
    SubcomposeAsyncImage(
        model =
            ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(true)
                .build(),
        contentDescription = null,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
        loading = {
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(size * 0.45f), strokeWidth = 1.5.dp)
            }
        },
        error = { Box(modifier = Modifier.size(size)) },
    )
}
