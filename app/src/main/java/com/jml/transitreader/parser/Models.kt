package com.jml.transitreader.parser

/** Result of reading a tapped card. */
sealed interface ReadResult {
    data class Clipper(val card: ClipperCard) : ReadResult
    data class Suica(val card: SuicaCard) : ReadResult
    data class Unsupported(val info: CardInfo) : ReadResult
    data class Failure(val message: String) : ReadResult
}

/** Generic low-level information shown for cards we can't fully parse. */
data class CardInfo(
    val uid: String,
    val technologies: List<String>,
    val detail: String?
)

data class ClipperCard(
    val serial: Long,
    val balanceCents: Int,
    val lastTransactionTimeUnix: Long?,
    val trips: List<ClipperTrip>
)

data class ClipperTrip(
    val agencyName: String,
    val fareCents: Int,
    val boardingTimeUnix: Long,
    val alightingTimeUnix: Long?,
    val vehicleId: Int?,
    val fromStation: String?,
    val toStation: String?
)

// --- Japanese FeliCa transit cards (Suica / ICOCA / PASMO and the mutual-use family) ---

data class SuicaCard(
    val idm: String,
    val balanceYen: Int,
    val transactions: List<SuicaTransaction>
)

data class SuicaTransaction(
    val year: Int,
    val month: Int,
    val day: Int,
    val type: String,
    val balanceYen: Int,
    val amountYen: Int?   // positive = top-up, negative = spend; null when unknown
)
