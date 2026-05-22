package fi.veneappi.app.ui.map;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngQuad;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.layers.RasterLayer;
import org.maplibre.android.style.sources.ImageSource;

/** Java helper: MapLibre [ImageSource] from Kotlin without constructor visibility issues. */
public final class StormGeoRadarOverlay {
    private StormGeoRadarOverlay() {}

    public static boolean updateGeoImageUri(Style style, String sourceId, String imageUrl) {
        if (!(style.getSource(sourceId) instanceof ImageSource imageSource)) {
            return false;
        }
        try {
            if (isLocalFileUri(imageUrl)) {
                imageSource.setImage(decodeLocalImage(imageUrl));
            } else {
                imageSource.setUri(imageUrl);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void addGeoImage(
            Style style,
            String sourceId,
            String layerId,
            String imageUrl,
            double northLat,
            double westLon,
            double southLat,
            double eastLon,
            float opacity,
            String aboveLayerId)
            throws MalformedURLException, IOException {
        LatLngQuad quad =
                new LatLngQuad(
                        new LatLng(northLat, westLon),
                        new LatLng(northLat, eastLon),
                        new LatLng(southLat, eastLon),
                        new LatLng(southLat, westLon));
        ImageSource imageSource;
        if (isLocalFileUri(imageUrl)) {
            imageSource = new ImageSource(sourceId, quad, decodeLocalImage(imageUrl));
        } else {
            imageSource = new ImageSource(sourceId, quad, new URL(imageUrl));
        }
        style.addSource(imageSource);
        RasterLayer layer =
                new RasterLayer(layerId, sourceId)
                        .withProperties(
                                PropertyFactory.rasterOpacity(opacity),
                                PropertyFactory.rasterFadeDuration(200f));
        if (aboveLayerId != null && style.getLayer(aboveLayerId) != null) {
            style.addLayerAbove(layer, aboveLayerId);
        } else {
            style.addLayer(layer);
        }
    }

    private static boolean isLocalFileUri(String imageUrl) {
        return imageUrl != null && imageUrl.startsWith("file:");
    }

    private static Bitmap decodeLocalImage(String imageUrl) throws IOException {
        File file = new File(URI.create(imageUrl));
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap == null) {
            throw new IOException("Failed to decode storm radar image: " + file.getAbsolutePath());
        }
        return bitmap;
    }
}
