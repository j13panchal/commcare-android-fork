package org.commcare.gis

import android.os.Bundle
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.maps.MapboxMap
import io.ona.kujaku.KujakuLibrary
import kotlinx.android.synthetic.main.activity_entity_kujaku_map.*
import kotlinx.coroutines.*
import org.commcare.activities.CommCareActivity
import org.commcare.dalvik.BuildConfig
import org.commcare.dalvik.R

abstract class BaseKujakuActivity : CommCareActivity<BaseKujakuActivity>() {

    val jobs = ArrayList<Job>()
    lateinit var map: MapboxMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        KujakuLibrary.init(this)
        Mapbox.getInstance(this, BuildConfig.MAPBOX_SDK_API_KEY)
        setContentView(R.layout.activity_entity_kujaku_map)
        mapView.onCreate(savedInstanceState)
        initMap()
    }

    private fun initMap() {
        mapView.showCurrentLocationBtn(true)
        mapView.focusOnUserLocation(true)
        mapView.getMapAsync { mapBoxMap ->
            map = mapBoxMap
            onMapLoaded()
        }
    }

    abstract fun onMapLoaded()

    override fun shouldShowBreadcrumbBar(): Boolean {
        return false
    }


    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        jobs.map { job -> job.cancel("Activity Destroyed") }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}