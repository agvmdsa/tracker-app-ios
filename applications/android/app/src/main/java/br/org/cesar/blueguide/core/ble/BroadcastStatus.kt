package br.org.cesar.blueguide.core.ble

sealed interface BroadcastStatus {

    /** The radio has been asked to advertise, but nothing is on the air yet. */
    data object Starting : BroadcastStatus

    /** The advertisement is live and nearby observers can see it. */
    data object Advertising : BroadcastStatus

    /** Advertising could not start, or stopped, because of [cause]. */
    data class Failed(val cause: Throwable) : BroadcastStatus
}
