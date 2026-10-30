package br.org.cesar.blueguide.tools.replay

import br.org.cesar.blueguide.core.positioning.PathLossPositioningEngine
import br.org.cesar.blueguide.core.positioning.PositioningEngine

/** The positioning engines the replay can run, by the name `--engine` takes. An engine being
 *  written goes here, to be compared with the app's on the same recordings. */
object Engines {
    /** The one the app uses. */
    const val APP = "path-loss"

    val BY_NAME: Map<String, () -> PositioningEngine> = linkedMapOf(APP to { PathLossPositioningEngine() })
}
