REFERENCE_RSSI = -59.0
PATH_LOSS_EXPONENT = 2.0


def distance_meters(rssi: float) -> float:
    return 10.0 ** ((REFERENCE_RSSI - rssi) / (10.0 * PATH_LOSS_EXPONENT))
