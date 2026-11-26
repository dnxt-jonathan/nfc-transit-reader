package com.jml.transitreader.nfc

import android.nfc.tech.NfcF
import java.io.ByteArrayOutputStream

/**
 * Minimal FeliCa command layer over NfcF, implementing the two commands needed
 * to read Japanese transit cards (Suica / ICOCA / PASMO and the mutual-use
 * family): Polling (0x00) and Read Without Encryption (0x06).
 *
 * FeliCa packets sent via NfcF.transceive() start with a 1-byte length that
 * covers the whole packet including itself.
 */
class FelicaProtocol(private val nfcF: NfcF) {

    /**
     * Poll for a given system code and return that system's 8-byte IDm, or null.
     * Transit cards live under system code 0x0003.
     */
    fun polling(systemCode: Int): ByteArray? {
        val cmd = byteArrayOf(
            0x06,                                   // length
            0x00,                                   // command: Polling
            ((systemCode shr 8) and 0xFF).toByte(), // system code (big-endian)
            (systemCode and 0xFF).toByte(),
            0x00,                                   // request code: none
            0x00                                    // time slot
        )
        val resp = try {
            nfcF.transceive(cmd)
        } catch (_: Exception) {
            return null
        }
        // response: len(1) 0x01 IDm(8) PMm(8) ...
        if (resp.size < 10 || (resp[1].toInt() and 0xFF) != 0x01) return null
        return resp.copyOfRange(2, 10)
    }

    /**
     * Read one 16-byte block from a service using Read Without Encryption.
     * Returns the block, or null on any FeliCa error status.
     */
    fun readBlock(idm: ByteArray, serviceCode: Int, blockNumber: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        out.write(0x00)                              // length placeholder
        out.write(0x06)                              // command: Read Without Encryption
        out.write(idm)                               // 8-byte IDm
        out.write(0x01)                              // number of services
        out.write(serviceCode and 0xFF)             // service code (little-endian)
        out.write((serviceCode shr 8) and 0xFF)
        out.write(0x01)                              // number of blocks
        out.write(0x80)                              // block element: 2-byte, service index 0
        out.write(blockNumber and 0xFF)
        val packet = out.toByteArray()
        packet[0] = packet.size.toByte()             // fill in real length

        val resp = try {
            nfcF.transceive(packet)
        } catch (_: Exception) {
            return null
        }
        // response: len 0x07 IDm(8) status1 status2 numBlocks data(16*n)
        if (resp.size < 13) return null
        if ((resp[1].toInt() and 0xFF) != 0x07) return null
        val status1 = resp[10].toInt() and 0xFF
        val status2 = resp[11].toInt() and 0xFF
        if (status1 != 0x00 || status2 != 0x00) return null
        val numBlocks = resp[12].toInt() and 0xFF
        if (numBlocks < 1 || resp.size < 13 + 16) return null
        return resp.copyOfRange(13, 13 + 16)
    }
}
