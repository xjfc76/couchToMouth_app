package com.couchtommouth.bridge.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.couchtommouth.bridge.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.*

/**
 * Manages Bluetooth thermal printer connections and printing.
 * Supports standard ESC/POS printers including:
 * - TP510UB (58mm, no drawer)
 * - M335B (58/80mm, with drawer)
 */
class PrinterManager(private val context: Context) {

    companion object {
        private const val TAG = "PrinterManager"
        // Standard SPP UUID for Bluetooth serial communication
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager? = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val config = AppConfig(context)

    // ESC/POS Commands
    private object ESC {
        val INIT = byteArrayOf(0x1B, 0x40)                    // Initialize printer
        val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)        // Left align
        val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)      // Center align
        val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)       // Right align
        val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)           // Bold on
        val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)          // Bold off
        val DOUBLE_HEIGHT_ON = byteArrayOf(0x1B, 0x21, 0x10)  // Double height
        val DOUBLE_WIDTH_ON = byteArrayOf(0x1B, 0x21, 0x20)   // Double width
        val DOUBLE_ON = byteArrayOf(0x1B, 0x21, 0x30)         // Double height & width
        val NORMAL = byteArrayOf(0x1B, 0x21, 0x00)            // Normal text
        val UNDERLINE_ON = byteArrayOf(0x1B, 0x2D, 0x01)      // Underline on
        val UNDERLINE_OFF = byteArrayOf(0x1B, 0x2D, 0x00)     // Underline off
        val CUT_PAPER = byteArrayOf(0x1D, 0x56, 0x00)         // Full cut
        val CUT_PARTIAL = byteArrayOf(0x1D, 0x56, 0x01)       // Partial cut
        val FEED_LINES = byteArrayOf(0x1B, 0x64, 0x04)        // Feed 4 lines
        val CASH_DRAWER = byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte())
        val LINE_SPACING_DEFAULT = byteArrayOf(0x1B, 0x32)    // Default line spacing
    }

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun isConnected(): Boolean = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    @SuppressLint("MissingPermission")
    suspend fun connectToPrinter(address: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Disconnect existing connection
            disconnect()

            val device = bluetoothAdapter?.getRemoteDevice(address)
            if (device == null) {
                Log.e(TAG, "Device not found: $address")
                return@withContext false
            }

            Log.d(TAG, "Connecting to ${device.name} ($address)")
            
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket?.connect()
            outputStream = socket?.outputStream

            if (socket?.isConnected == true) {
                Log.d(TAG, "Connected successfully")
                // Initialize printer
                outputStream?.write(ESC.INIT)
                return@withContext true
            }

            return@withContext false
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed", e)
            disconnect()
            return@withContext false
        }
    }

    fun disconnect() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error disconnecting", e)
        }
        outputStream = null
        socket = null
    }

    /**
     * Print a receipt
     */
    suspend fun printReceipt(receipt: ReceiptData) = withContext(Dispatchers.IO) {
        val stream = outputStream ?: run {
            Log.e(TAG, "Printer not connected")
            return@withContext
        }

        try {
            // Initialize
            stream.write(ESC.INIT)
            stream.write(ESC.LINE_SPACING_DEFAULT)

            // Header - Shop name (large, centered)
            stream.write(ESC.ALIGN_CENTER)
            stream.write(ESC.DOUBLE_ON)
            stream.write(ESC.BOLD_ON)
            printLine(receipt.shopName)
            stream.write(ESC.NORMAL)
            stream.write(ESC.BOLD_OFF)

            // Address & phone
            if (receipt.shopAddress.isNotEmpty()) {
                printLine(receipt.shopAddress)
            }
            if (receipt.shopPhone.isNotEmpty()) {
                printLine(receipt.shopPhone)
            }
            printLine("")

            // Receipt title
            stream.write(ESC.BOLD_ON)
            printLine("RECEIPT")
            stream.write(ESC.BOLD_OFF)
            printLine("")

            // Date/time and receipt number
            stream.write(ESC.ALIGN_LEFT)
            printLine("Date: ${receipt.dateTime}")
            printLine("Receipt: ${receipt.receiptNumber}")
            if (receipt.orderType.isNotEmpty()) {
                printLine("Order: ${receipt.orderType}")
            }
            printLine("-".repeat(32))

            // Items
            for (item in receipt.items) {
                // Item name and price
                val itemLine = formatLine(item.name, "£${"%.2f".format(item.price)}")
                printLine(itemLine)

                // Modifiers
                for (modifier in item.modifiers) {
                    val modLine = if (modifier.price > 0) {
                        formatLine("  ${modifier.name}: ${modifier.option}", "£${"%.2f".format(modifier.price)}")
                    } else {
                        "  ${modifier.name}: ${modifier.option}"
                    }
                    printLine(modLine)
                }
            }

            printLine("-".repeat(32))

            // Discount if applicable
            if (receipt.discount > 0) {
                val discountLine = formatLine("Discount:", "-£${"%.2f".format(receipt.discount)}")
                printLine(discountLine)
            }

            // Total
            stream.write(ESC.BOLD_ON)
            stream.write(ESC.DOUBLE_HEIGHT_ON)
            val totalLine = formatLine("TOTAL:", "£${"%.2f".format(receipt.total)}")
            printLine(totalLine)
            stream.write(ESC.NORMAL)
            stream.write(ESC.BOLD_OFF)

            printLine("")

            // Payment info
            if (receipt.paymentMethod.isNotEmpty()) {
                stream.write(ESC.ALIGN_CENTER)
                printLine("PAYMENT: ${receipt.paymentMethod.uppercase()}")

                // Card details if applicable
                if (receipt.cardType?.isNotEmpty() == true) {
                    printLine("${receipt.cardType} ****${receipt.cardLastFour}")
                    if (receipt.authCode?.isNotEmpty() == true) {
                        printLine("Auth: ${receipt.authCode}")
                    }
                    if (receipt.transactionId?.isNotEmpty() == true) {
                        printLine("Trans: ${receipt.transactionId}")
                    }
                    printLine("")
                    stream.write(ESC.BOLD_ON)
                    printLine("APPROVED")
                    stream.write(ESC.BOLD_OFF)
                }
            }

            printLine("")

            // Footer
            stream.write(ESC.ALIGN_CENTER)
            printLine("Thank you!")
            printLine("")

            // Feed and cut
            stream.write(ESC.FEED_LINES)
            if (config.hasPaperCutter()) {
                stream.write(ESC.CUT_PARTIAL)
            }

            stream.flush()
            Log.d(TAG, "Receipt printed successfully")

        } catch (e: IOException) {
            Log.e(TAG, "Print failed", e)
            throw e
        }
    }

    /**
     * Open cash drawer (sends pulse to drawer connected to printer)
     */
    suspend fun openCashDrawer() = withContext(Dispatchers.IO) {
        val stream = outputStream ?: run {
            Log.e(TAG, "Printer not connected - cannot open drawer")
            return@withContext
        }

        try {
            Log.d(TAG, "Opening cash drawer")
            stream.write(ESC.CASH_DRAWER)
            stream.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to open cash drawer", e)
        }
    }

    /**
     * Print a test page
     */
    suspend fun printTestPage() = withContext(Dispatchers.IO) {
        val stream = outputStream ?: return@withContext

        try {
            stream.write(ESC.INIT)
            stream.write(ESC.ALIGN_CENTER)
            stream.write(ESC.DOUBLE_ON)
            stream.write(ESC.BOLD_ON)
            printLine("PRINTER TEST")
            stream.write(ESC.NORMAL)
            stream.write(ESC.BOLD_OFF)
            printLine("")
            printLine("C2M POS")
            printLine("Bridge App v1.0")
            printLine("")
            stream.write(ESC.ALIGN_LEFT)
            printLine("Normal text")
            stream.write(ESC.BOLD_ON)
            printLine("Bold text")
            stream.write(ESC.BOLD_OFF)
            printLine("-".repeat(32))
            printLine("If you can read this,")
            printLine("printing is working!")
            printLine("")
            stream.write(ESC.FEED_LINES)
            if (config.hasPaperCutter()) {
                stream.write(ESC.CUT_PARTIAL)
            }
            stream.flush()
        } catch (e: IOException) {
            Log.e(TAG, "Test print failed", e)
        }
    }

    private fun printLine(text: String) {
        outputStream?.write("$text\n".toByteArray(Charsets.UTF_8))
    }

    /**
     * Format a line with left and right text (for item - price alignment)
     */
    private fun formatLine(left: String, right: String, width: Int = 32): String {
        val spaces = width - left.length - right.length
        return if (spaces > 0) {
            left + " ".repeat(spaces) + right
        } else {
            // Truncate left side if too long
            left.take(width - right.length - 1) + " " + right
        }
    }
}
