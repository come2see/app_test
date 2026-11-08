package tech.illusion.space24

import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import tech.illusion.space24.content.SpatialStage
import tech.illusion.space24.content.cyberColorScheme

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        DefaultWindowContainer {
            PicoTheme(colorScheme = cyberColorScheme()) {
                SpatialStage()
            }
        }
    }
