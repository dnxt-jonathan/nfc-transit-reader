package com.jml.transitreader.parser

import com.jml.transitreader.nfc.DesfireProtocol

/**
 * Parses San Francisco Bay Area Clipper cards (MIFARE DESFire).
 *
 * Byte layout verified against the open-source Flipper Zero `clipper.c` parser:
 *   Application: 0x9011F2 (plastic card) or 0x9111F2 (mobile/phone credential)
 *   File 0x02 (e-cash):  balance at offset 0x12, signed 16-bit big-endian, cents
 *                        last-transaction timestamp at offset 0x04, u32 big-endian
 *   File 0x06:           history index — record numbers, 0xFF terminates
 *   File 0x08:           identity — card serial number
 *   File 0x0E (14):      ride history — 32-byte records
 *
 * Trip record (32 bytes):
 *   0x00 marker (must be 0x10)   0x02 agency id (u16)   0x06 fare (s16, cents)
 *   0x0A vehicle id (u16)        0x0C boarding (u32)     0x10 alighting (u32)
 *   0x14 from zone/station (u16) 0x16 to zone/station (u16)
 *
 * Timestamps are seconds since 1900-01-01 GMT; subtract 2208988800 for Unix time.
 *
 * All of these files are freely readable, so no authentication is attempted.
 */
object ClipperParser {

    private val APP_IDS = intArrayOf(0x9011F2, 0x9111F2)

    private const val FILE_ECASH = 0x02
    private const val FILE_HISTORY_INDEX = 0x06
    private const val FILE_IDENTITY = 0x08
    private const val FILE_TRIPS = 0x0E

    private const val RECORD_LEN = 0x20
    private const val TRIP_MARKER = 0x10
    private const val EPOCH_1900_TO_1970 = 2208988800L

    /** @return a ClipperCard if this is a Clipper card we could read, else null. */
    fun tryParse(proto: DesfireProtocol): ClipperCard? {
        var selected = false
        for (appId in APP_IDS) {
            try {
                proto.selectApplication(appId)
                selected = true
                break
            } catch (_: Exception) {
                // Not this application; try the next.
            }
        }
        if (!selected) return null

        val ecash = try {
            proto.readData(FILE_ECASH)
        } catch (_: Exception) {
            return null // Selected a Clipper AID but couldn't read balance — treat as not-Clipper.
        }
        if (ecash.size < 0x14) return null

        val balanceCents = readS16BE(ecash, 0x12)
        val lastTxnRaw = readU32BE(ecash, 0x04)
        val lastTxnUnix = if (lastTxnRaw > 0) lastTxnRaw - EPOCH_1900_TO_1970 else null

        val serial = try {
            val id = proto.readData(FILE_IDENTITY)
            readUIntBE(id, 0, minOf(4, id.size))
        } catch (_: Exception) {
            -1L
        }

        val trips = parseTrips(proto)

        return ClipperCard(serial, balanceCents, lastTxnUnix, trips)
    }

    private fun parseTrips(proto: DesfireProtocol): List<ClipperTrip> {
        val tripData = try {
            proto.readData(FILE_TRIPS)
        } catch (_: Exception) {
            return emptyList()
        }
        val recordCount = tripData.size / RECORD_LEN
        if (recordCount == 0) return emptyList()

        // The history index lists record numbers in chronological order; if we
        // can read it we honor that order, otherwise we parse every slot.
        val order: List<Int> = try {
            val index = proto.readData(FILE_HISTORY_INDEX)
            val list = mutableListOf<Int>()
            for (b in index) {
                val n = b.toInt() and 0xFF
                if (n == 0xFF) break
                if (n < recordCount) list.add(n)
            }
            if (list.isEmpty()) (0 until recordCount).toList() else list
        } catch (_: Exception) {
            (0 until recordCount).toList()
        }

        val trips = mutableListOf<ClipperTrip>()
        for (n in order) {
            val offset = n * RECORD_LEN
            if (offset + RECORD_LEN > tripData.size) continue
            parseTrip(tripData, offset)?.let { trips.add(it) }
        }
        return trips.sortedByDescending { it.boardingTimeUnix }
    }

    private fun parseTrip(data: ByteArray, off: Int): ClipperTrip? {
        if ((data[off].toInt() and 0xFF) != TRIP_MARKER) return null
        val agencyId = readU16BE(data, off + 0x02)
        if (agencyId == 0) return null

        val fare = readS16BE(data, off + 0x06)
        val vehicle = readU16BE(data, off + 0x0A)
        val boarding = readU32BE(data, off + 0x0C) - EPOCH_1900_TO_1970
        val alightRaw = readU32BE(data, off + 0x10)
        val alighting = if (alightRaw > 0) alightRaw - EPOCH_1900_TO_1970 else null
        val fromCode = readU16BE(data, off + 0x14)
        val toCode = readU16BE(data, off + 0x16)

        return ClipperTrip(
            agencyName = ClipperAgencies.agencyName(agencyId),
            fareCents = fare,
            boardingTimeUnix = boarding,
            alightingTimeUnix = alighting,
            vehicleId = if (vehicle > 0) vehicle else null,
            fromStation = ClipperAgencies.station(agencyId, fromCode),
            toStation = ClipperAgencies.station(agencyId, toCode)
        )
    }

    // --- big-endian readers ---

    private fun readU16BE(d: ByteArray, o: Int): Int =
        ((d[o].toInt() and 0xFF) shl 8) or (d[o + 1].toInt() and 0xFF)

    private fun readS16BE(d: ByteArray, o: Int): Int =
        readU16BE(d, o).toShort().toInt()

    private fun readU32BE(d: ByteArray, o: Int): Long =
        ((d[o].toLong() and 0xFF) shl 24) or
            ((d[o + 1].toLong() and 0xFF) shl 16) or
            ((d[o + 2].toLong() and 0xFF) shl 8) or
            (d[o + 3].toLong() and 0xFF)

    private fun readUIntBE(d: ByteArray, o: Int, len: Int): Long {
        var v = 0L
        for (i in 0 until len) v = (v shl 8) or (d[o + i].toLong() and 0xFF)
        return v
    }
}
