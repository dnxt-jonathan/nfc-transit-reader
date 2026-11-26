package com.jml.transitreader

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jml.transitreader.nfc.TransitCardReader
import com.jml.transitreader.parser.ReadResult
import com.jml.transitreader.ui.ReaderScreen
import com.jml.transitreader.ui.TransitReaderTheme

/**
 * Hosts the Compose UI and drives NFC in reader mode. Reader mode keeps the OS
 * from launching other NFC handlers while this screen is foreground, and lets us
 * talk to the card directly over IsoDep.
 */
class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null

    private var result by mutableStateOf<ReadResult?>(null)
    private var nfcEnabled by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        setContent {
            TransitReaderTheme {
                ReaderScreen(result = result, nfcEnabled = nfcEnabled)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val adapter = nfcAdapter
        if (adapter == null) {
            nfcEnabled = false
            return
        }
        nfcEnabled = adapter.isEnabled
        if (adapter.isEnabled) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
            adapter.enableReaderMode(this, this, flags, null)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    /** Called on a background thread when a tag enters the field. */
    override fun onTagDiscovered(tag: Tag) {
        val read = try {
            TransitCardReader.read(tag)
        } catch (e: Exception) {
            ReadResult.Failure(e.message ?: "Read failed")
        }
        runOnUiThread { result = read }
    }
}
