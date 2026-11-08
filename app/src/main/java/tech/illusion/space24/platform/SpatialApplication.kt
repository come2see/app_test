package tech.illusion.space24.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import tech.illusion.space24.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
