package com.ali.carsystem;

import android.content.Context;

import org.osmdroid.tileprovider.IRegisterReceiver;
import org.osmdroid.tileprovider.MapTileProviderArray;
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.util.TileSystem;

/**
 * Empty, offline-only placeholder used while the Syria MapsForge package is being
 * copied and validated. Unlike MapView's default MapTileProviderBasic, this provider
 * deliberately does not create MapTileApproximater worker threads. NavigationActivity
 * replaces it with the real MapsForgeTileProvider as soon as the map files are ready.
 */
final class EmptyOfflineTileProvider extends MapTileProviderArray {
    EmptyOfflineTileProvider(Context context) {
        super(TileSourceFactory.DEFAULT_TILE_SOURCE,
                new SimpleRegisterReceiver(context),
                new MapTileModuleProviderBase[0]);
        setUseDataConnection(false);
    }

    @Override public int getMinimumZoomLevel() {
        return 0;
    }

    @Override public int getMaximumZoomLevel() {
        return TileSystem.getMaximumZoomLevel();
    }
}
