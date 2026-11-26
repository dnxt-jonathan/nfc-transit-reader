package com.jml.transitreader.parser

import android.nfc.tech.NfcF
import com.jml.transitreader.nfc.FelicaProtocol
import com.jml.transitreader.nfc.toHex

/**
 * Parses Japanese FeliCa transit cards — Suica, ICOCA, PASMO, and the rest of the
 * nationally interoperable IC-card family, which all share the same layout.
 *
 * Transit data lives under FeliCa system code 0x0003. The ride/purchase history
 * is service 0x090F: up to 20 blocks of 16 bytes, newest first, each freely
 * readable (Read Without Encryption).
 *
 * History block (16 bytes), relevant fields:
 *   [1]      process type (fare, charge, purchase, …)
 *   [4..5]   date: bit15-9 year (+2000), bit8-5 month, bit4-0 day
 *   [10..11] balance AFTER this transaction, little-endian, yen
 *
 * The spend/top-up amount of a transaction is the balance delta against the next
 * (older) block. Station codes are also present but need a large lookup table,
 * so they're omitted here.
 */
object SuicaParser {

    private const val SYSTEM_TRANSIT = 0x0003
    private const val SERVICE_HISTORY = 0x090F
    private const val MAX_BLOCKS = 20

    fun tryParse(nfcF: NfcF, tagId: ByteArray?): SuicaCard? {
        val felica = FelicaProtocol(nfcF)
        val idm = felica.polling(SYSTEM_TRANSIT) ?: tagId ?: return null

        val blocks = mutableListOf<ByteArray>()
        for (i in 0 until MAX_BLOCKS) {
            val block = felica.readBlock(idm, SERVICE_HISTORY, i) ?: break
            if (block.all { it.toInt() == 0 }) break
            blocks.add(block)
        }
        if (blocks.isEmpty()) return null

        val balance = readBalance(blocks[0])

        val txns = mutableListOf<SuicaTransaction>()
        for (i in blocks.indices) {
            val bal = readBalance(blocks[i])
            val olderBal = if (i + 1 < blocks.size) readBalance(blocks[i + 1]) else null
            val amount = if (olderBal != null) bal - olderBal else null
            parseBlock(blocks[i], bal, amount)?.let { txns.add(it) }
        }

        return SuicaCard(idm.toHex(), balance, txns)
    }

    private fun readBalance(b: ByteArray): Int =
        (b[10].toInt() and 0xFF) or ((b[11].toInt() and 0xFF) shl 8)

    private fun parseBlock(b: ByteArray, balance: Int, amount: Int?): SuicaTransaction? {
        val dateRaw = ((b[4].toInt() and 0xFF) shl 8) or (b[5].toInt() and 0xFF)
        if (dateRaw == 0) return null
        val year = 2000 + (dateRaw shr 9)
        val month = (dateRaw shr 5) and 0x0F
        val day = dateRaw and 0x1F
        val type = processType(b[1].toInt() and 0xFF)
        return SuicaTransaction(year, month, day, type, balance, amount)
    }

    private fun processType(proc: Int): String = when (proc) {
        0x01, 0x02, 0x03 -> "Fare"
        0x04 -> "Ticket"
        0x05 -> "Fare adjustment"
        0x06 -> "Top-up / Charge"
        0x07 -> "New card"
        0x08 -> "Deduction"
        0x0D, 0x0F -> "Bus / Tram"
        0x14 -> "Auto-charge"
        0x46, 0xC6 -> "Purchase"
        0x49 -> "Charge (cash)"
        else -> "Txn 0x%02X".format(proc)
    }
}
