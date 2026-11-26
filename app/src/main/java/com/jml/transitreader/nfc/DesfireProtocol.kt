package com.jml.transitreader.nfc

import android.nfc.tech.IsoDep
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * Minimal MIFARE DESFire command layer, speaking native DESFire commands wrapped
 * in ISO 7816-4 APDUs (CLA = 0x90). This is the "wrapped" framing that Android's
 * IsoDep transceive() handles reliably.
 *
 * In this framing the response ends with two status bytes: SW1 = 0x91 and
 * SW2 = the DESFire status code. 0x00 = success, 0xAF = additional frame follows.
 *
 * Only unauthenticated reads are implemented — enough to read the freely
 * readable files on transit cards such as Clipper. No crypto / authentication
 * is performed, so files protected by a key cannot (and should not) be read.
 */
class DesfireProtocol(private val isoDep: IsoDep) {

    companion object {
        private const val CMD_SELECT_APPLICATION = 0x5A
        private const val CMD_READ_DATA = 0xBD
        private const val CMD_READ_RECORDS = 0xBB
        private const val CMD_GET_APPLICATION_IDS = 0x6A
        private const val CMD_ADDITIONAL_FRAME = 0xAF

        private const val STATUS_OK = 0x00
        private const val STATUS_ADDITIONAL_FRAME = 0xAF
    }

    /** Build a wrapped (ISO 7816) APDU for a native DESFire command. */
    private fun wrap(command: Int, params: ByteArray): ByteArray {
        if (params.isEmpty()) {
            // CLA INS P1 P2 Le   (no command data)
            return byteArrayOf(0x90.toByte(), command.toByte(), 0x00, 0x00, 0x00)
        }
        val out = ByteArrayOutputStream()
        out.write(0x90)            // CLA
        out.write(command)         // INS
        out.write(0x00)            // P1
        out.write(0x00)            // P2
        out.write(params.size)     // Lc
        out.write(params)          // command data
        out.write(0x00)            // Le
        return out.toByteArray()
    }

    /** @return Pair(status, responseDataWithoutStatusBytes) */
    private fun transceiveRaw(command: Int, params: ByteArray): Pair<Int, ByteArray> {
        val response = isoDep.transceive(wrap(command, params))
        if (response.size < 2) {
            throw IOException("Short response from card")
        }
        val status = response[response.size - 1].toInt() and 0xFF
        val data = response.copyOfRange(0, response.size - 2)
        return Pair(status, data)
    }

    /**
     * Run a command and keep requesting additional frames until the card signals
     * completion, concatenating all returned data.
     */
    private fun transceiveFull(command: Int, params: ByteArray): ByteArray {
        var (status, data) = transceiveRaw(command, params)
        val out = ByteArrayOutputStream()
        out.write(data)
        while (status == STATUS_ADDITIONAL_FRAME) {
            val (s, d) = transceiveRaw(CMD_ADDITIONAL_FRAME, ByteArray(0))
            status = s
            out.write(d)
        }
        if (status != STATUS_OK) {
            throw IOException("DESFire command 0x%02X failed with status 0x%02X".format(command, status))
        }
        return out.toByteArray()
    }

    /** Select an application by its 3-byte AID (e.g. 0x9011F2). */
    fun selectApplication(appId: Int) {
        val aid = byteArrayOf(
            (appId and 0xFF).toByte(),          // AID is sent least-significant byte first
            ((appId shr 8) and 0xFF).toByte(),
            ((appId shr 16) and 0xFF).toByte()
        )
        val (status, _) = transceiveRaw(CMD_SELECT_APPLICATION, aid)
        if (status != STATUS_OK) {
            throw IOException("SelectApplication(0x%06X) failed: 0x%02X".format(appId, status))
        }
    }

    /** Read a Standard or Backup data file in full (offset 0, length = whole file). */
    fun readData(fileNo: Int): ByteArray {
        val params = byteArrayOf(
            fileNo.toByte(),
            0x00, 0x00, 0x00,   // offset = 0
            0x00, 0x00, 0x00    // length = 0 => read the entire file
        )
        return transceiveFull(CMD_READ_DATA, params)
    }

    /** Read all records from a linear/cyclic record file. */
    fun readRecords(fileNo: Int): ByteArray {
        val params = byteArrayOf(
            fileNo.toByte(),
            0x00, 0x00, 0x00,   // record offset = 0 (most recent)
            0x00, 0x00, 0x00    // count = 0 => all records
        )
        return transceiveFull(CMD_READ_RECORDS, params)
    }

    /** List application IDs present on the card (used for the generic info view). */
    fun getApplicationIds(): List<Int> {
        val raw = transceiveFull(CMD_GET_APPLICATION_IDS, ByteArray(0))
        val ids = mutableListOf<Int>()
        var i = 0
        while (i + 3 <= raw.size) {
            val id = (raw[i].toInt() and 0xFF) or
                ((raw[i + 1].toInt() and 0xFF) shl 8) or
                ((raw[i + 2].toInt() and 0xFF) shl 16)
            ids.add(id)
            i += 3
        }
        return ids
    }
}
