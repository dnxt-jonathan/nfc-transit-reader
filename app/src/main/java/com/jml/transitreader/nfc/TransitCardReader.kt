package com.jml.transitreader.nfc

import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.NfcF
import com.jml.transitreader.parser.CardInfo
import com.jml.transitreader.parser.ClipperParser
import com.jml.transitreader.parser.ReadResult
import com.jml.transitreader.parser.SuicaParser

/**
 * Entry point for reading a tapped tag: try each supported parser, and fall back
 * to a generic info view describing what the card is.
 */
object TransitCardReader {

    fun read(tag: Tag): ReadResult {
        val uid = tag.id?.toHex() ?: "(unknown)"
        val techs = tag.techList.map { it.substringAfterLast('.') }

        // FeliCa path: Japanese IC cards (Suica / ICOCA / PASMO …) show up as NfcF.
        val nfcF = NfcF.get(tag)
        if (nfcF != null) {
            return try {
                nfcF.connect()
                val suica = try {
                    SuicaParser.tryParse(nfcF, tag.id)
                } catch (_: Exception) {
                    null
                }
                suica?.let { return ReadResult.Suica(it) }
                ReadResult.Unsupported(
                    CardInfo(
                        uid, techs,
                        "FeliCa card detected, but no readable transit history was found. " +
                            "This app reads the Suica / ICOCA / PASMO family (system 0x0003)."
                    )
                )
            } catch (e: Exception) {
                ReadResult.Failure(e.message ?: "Couldn't read the FeliCa card. Hold it steady and try again.")
            } finally {
                try {
                    nfcF.close()
                } catch (_: Exception) {
                }
            }
        }

        val isoDep = IsoDep.get(tag)
            ?: return ReadResult.Unsupported(
                CardInfo(
                    uid = uid,
                    technologies = techs,
                    detail = "This isn't a DESFire or FeliCa card. Transit parsing here targets " +
                        "Clipper (DESFire) and Suica/ICOCA/PASMO (FeliCa). MIFARE Classic/Ultralight " +
                        "cards are encrypted and need keys this app doesn't carry."
                )
            )

        return try {
            isoDep.connect()
            isoDep.timeout = 5000
            val proto = DesfireProtocol(isoDep)

            val clipper = try {
                ClipperParser.tryParse(proto)
            } catch (_: Exception) {
                null
            }
            if (clipper != null) {
                ReadResult.Clipper(clipper)
            } else {
                val apps = try {
                    proto.getApplicationIds()
                } catch (_: Exception) {
                    emptyList()
                }
                val detail = if (apps.isEmpty()) {
                    "DESFire card detected, but no supported transit application was found."
                } else {
                    "DESFire card. Applications: " +
                        apps.joinToString(", ") { "0x%06X".format(it) } +
                        ". None are supported yet — this card's data may also be key-protected."
                }
                ReadResult.Unsupported(CardInfo(uid, techs, detail))
            }
        } catch (e: Exception) {
            ReadResult.Failure(e.message ?: "Couldn't read the card. Hold it steady and try again.")
        } finally {
            try {
                isoDep.close()
            } catch (_: Exception) {
            }
        }
    }
}
