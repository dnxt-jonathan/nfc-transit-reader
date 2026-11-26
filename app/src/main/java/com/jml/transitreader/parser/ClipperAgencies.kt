package com.jml.transitreader.parser

/**
 * Clipper agency IDs and a small set of station/zone names.
 *
 * Agency IDs are well known. Station maps are large and agency-specific; a
 * representative subset is included here (BART and Caltrain) and the rest fall
 * back to null so the UI simply omits the station. Extend these maps to add
 * more station names.
 */
object ClipperAgencies {

    private val AGENCIES = mapOf(
        0x0001 to "AC Transit",
        0x0004 to "BART",
        0x0006 to "Caltrain",
        0x0008 to "County Connection",
        0x000B to "Golden Gate Transit",
        0x000F to "SamTrans",
        0x0011 to "VTA",
        0x0012 to "Muni",
        0x0019 to "Golden Gate Ferry",
        0x001B to "SF Bay Ferry"
    )

    // Caltrain fare zones.
    private val CALTRAIN_ZONES = mapOf(
        1 to "Zone 1", 2 to "Zone 2", 3 to "Zone 3",
        4 to "Zone 4", 5 to "Zone 5", 6 to "Zone 6"
    )

    // A subset of BART station codes. Extend as needed.
    private val BART_STATIONS = mapOf(
        0x01 to "Lake Merritt", 0x02 to "Fruitvale", 0x03 to "Coliseum",
        0x04 to "San Leandro", 0x05 to "Bay Fair", 0x06 to "Hayward",
        0x07 to "South Hayward", 0x08 to "Union City", 0x09 to "Fremont",
        0x0C to "Richmond", 0x0F to "Downtown Berkeley",
        0x11 to "MacArthur", 0x12 to "19th St Oakland",
        0x13 to "12th St Oakland", 0x14 to "West Oakland",
        0x15 to "Embarcadero", 0x16 to "Montgomery St",
        0x17 to "Powell St", 0x18 to "Civic Center",
        0x19 to "16th St Mission", 0x1A to "24th St Mission",
        0x1B to "Glen Park", 0x1C to "Balboa Park", 0x1D to "Daly City",
        0x1E to "Colma", 0x1F to "South San Francisco",
        0x20 to "San Bruno", 0x21 to "Millbrae",
        0x22 to "SFO", 0x2B to "Pleasanton", 0x2C to "Dublin/Pleasanton"
    )

    fun agencyName(id: Int): String = AGENCIES[id] ?: "Agency 0x%04X".format(id)

    fun station(agencyId: Int, code: Int): String? {
        if (code == 0) return null
        return when (agencyId) {
            0x0004 -> BART_STATIONS[code]
            0x0006 -> CALTRAIN_ZONES[code]
            else -> null
        }
    }
}
