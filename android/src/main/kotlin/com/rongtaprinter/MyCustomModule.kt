package com.lineclear.ce

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import com.facebook.react.bridge.Promise
import android.content.Context
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.rt.printerlibrary.bean.BitmapLimitSizeBean
import com.rt.printerlibrary.bean.LableSizeBean
import com.rt.printerlibrary.bean.Position
import com.rt.printerlibrary.cmd.Cmd
import com.rt.printerlibrary.cmd.TscFactory
import com.rt.printerlibrary.enumerate.PrintDirection
import com.rt.printerlibrary.exception.SdkException
import com.rt.printerlibrary.factory.cmd.CmdFactory
import com.rt.printerlibrary.printer.RTPrinter
import com.rt.printerlibrary.setting.BitmapSetting
import com.rt.printerlibrary.setting.CommonSetting
import com.rt.printerlibrary.utils.BitmapConvertUtil
import com.rt.printerlibrary.enumerate.BmpPrintMode
import com.rt.printerlibrary.enumerate.TscFontTypeEnum;
import java.net.URL
import android.net.Uri
import com.rt.printerlibrary.factory.printer.PrinterFactory
import com.rt.printerlibrary.factory.printer.LabelPrinterFactory
import com.rt.printerlibrary.factory.connect.WiFiFactory
import com.rt.printerlibrary.bean.WiFiConfigBean
import com.rt.printerlibrary.factory.connect.PIFactory
import kotlinx.coroutines.*
import android.util.Log

import com.rt.printerlibrary.connect.PrinterInterface
import com.rt.printerlibrary.enumerate.CommonEnum
import com.rt.printerlibrary.factory.printer.UniversalPrinterFactory
import com.rt.printerlibrary.observer.PrinterObserver
import com.rt.printerlibrary.observer.PrinterObserverManager
import com.rt.printerlibrary.enumerate.*
import com.rt.printerlibrary.setting.TextSetting
import com.rt.printerlibrary.utils.PrinterStatusPareseUtils

class MyCustomModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext),PrinterObserver {
    private var configObj: Any? = null
    private var rtPrinter: RTPrinter<Any>? = null
    private var printerFactory: PrinterFactory? = null
    private val chooseTscFont = TscFontTypeEnum.Font_TSS24_BF2_For_Simple_Chinese
    private val mChartsetName = "UTF-8"
    

    override fun getName(): String {
        return "MyCustomModule"
    }

    @ReactMethod
    fun showToast(message: String) {
        Toast.makeText(reactApplicationContext, message, Toast.LENGTH_LONG).show()
    }

    private fun downloadAndResizeImage(url: String): Bitmap? {
        try {
            val bitmap = BitmapFactory.decodeStream(URL(url).openStream())
            return bitmap?.let { resizeBitmapIfNeeded(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }


    fun connectPrinter(ip:String,port:Int) {
        configObj = WiFiConfigBean(ip, port)
        val wiFiConfigBean = configObj as WiFiConfigBean
        val piFactory: PIFactory = WiFiFactory()
        val printerInterface: PrinterInterface<Any> = piFactory.create()
        printerInterface.configObject = wiFiConfigBean
        rtPrinter?.printerInterface = printerInterface
        try {
            rtPrinter?.connect(wiFiConfigBean)
            println("Printer connected")
            Log.d("MyCustomModule","Connected Printer from wifi");
        } catch (e: Exception) {
            println("Exception in connect: ${'$'}{e.message}")
            e.printStackTrace()
        }
    }

    override fun printerObserverCallback(arg0: PrinterInterface<Any>?, arg1: Int) {
        when (arg1) {
            CommonEnum.CONNECT_STATE_SUCCESS -> {
                rtPrinter?.printerInterface = arg0
                println("Connected Printer")
                setPrinterStatusListener()
                Log.d("MyCustomModule","Connected Printer")
            }
            CommonEnum.CONNECT_STATE_INTERRUPTED -> {
                println("Disconnected Printer")
                Log.d("MyCustomModule","Disconnected Printer")
            }
        }
    }
 
    override fun printerReadMsgCallback(arg0: PrinterInterface<Any>?, arg1: ByteArray?) {}

    private fun resizeBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val maxWidth = 210 * 8
        val maxHeight = 4000

        return when {
            bitmap.width > maxWidth -> BitmapConvertUtil.decodeSampledBitmapFromBitmap(bitmap, maxWidth, maxHeight)
            bitmap.width > 99 * 8 -> BitmapConvertUtil.decodeSampledBitmapFromBitmap(bitmap, 100 * 8, maxHeight)
            bitmap.width > 72 * 8 -> BitmapConvertUtil.decodeSampledBitmapFromBitmap(bitmap, 72 * 8, maxHeight)
            bitmap.width > 48 * 8 -> BitmapConvertUtil.decodeSampledBitmapFromBitmap(bitmap, 48 * 8, maxHeight)
            else -> bitmap
        }
    }
    @ReactMethod
    fun initPrinter(IP: String, PORT: Int, promise: Promise) {
        try {
            printerFactory = UniversalPrinterFactory()
            rtPrinter = printerFactory?.create()
            PrinterObserverManager.getInstance().add(this)
            connectPrinter(IP,PORT);
            Log.d("MyCustomModule","Init printer success");
            promise.resolve("connected")
        }
        catch(e: Exception) {
            Log.d("MyCustomModule","Init printer failed");
            promise.reject("ERROR", e.message)
        }
  
    }

    @ReactMethod
    public fun doDisConnect(promise: Promise) {
        if (rtPrinter?.printerInterface != null) {
            try {
                rtPrinter?.disConnect()
                println("rongta printer disconnected successfully");
                Log.d("MyCustomModule","rongta printer disconnected successfully");
                promise.resolve("disconnected")
            } catch (e: Exception) {
                println("Error during disconnect: \${e.message}")
                promise.reject("ERROR", e.message)
            }
        }
    }
  

    @ReactMethod
    fun tscPrint(imageUrl: String, promise: Promise) {
        Thread {
            try {
                // Default Values
                val width = 95 // mm
                val height = 250 // mm
                val maxPrintWidth = width * 8 
                val maxPrintHeight = height * 8 
    
                // Download Image
                val bitmap = downloadAndResizeImage(imageUrl) ?: throw Exception("Image Download Failed")
    
                // Get Original Dimensions
                val originalWidth = bitmap.width
                val originalHeight = bitmap.height
    
                // Calculate Scaling Factor (Maintain Aspect Ratio)
                val scaleFactor = maxPrintWidth.toFloat() / originalWidth.toFloat()
                var scaledHeight = (originalHeight * scaleFactor).toInt()
    
                // Ensure Scaled Height Does Not Exceed Print Height
                if (scaledHeight > maxPrintHeight) {
                    scaledHeight = maxPrintHeight
                }
    
                // Resize Image
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, maxPrintWidth, scaledHeight, false)
    
                // TSC Command Setup
                val tscFac = TscFactory()
                val tscCmd = tscFac.create()
                tscCmd.append(tscCmd.headerCmd)
    
                // Common Settings
                val commonSetting = CommonSetting()
                commonSetting.lableSizeBean = LableSizeBean(width, height)
                commonSetting.labelGap = 3
                commonSetting.printDirection = PrintDirection.NORMAL
                tscCmd.append(tscCmd.getCommonSettingCmd(commonSetting))
    
                // Bitmap Settings (FIXED)
                val bitmapSetting = BitmapSetting()
                bitmapSetting.printPostion = Position(5, 12) // Reset padding
                bitmapSetting.bimtapLimitWidth = maxPrintWidth // Fix width
                bitmapSetting.bmpPrintMode = BmpPrintMode.MODE_MULTI_COLOR 
    
                // Add Bitmap Command
                tscCmd.append(tscCmd.getBitmapCmd(bitmapSetting, resizedBitmap))
                tscCmd.append(tscCmd.getPrintCopies(1)) // 1 Copy
    
                // Send to Printer
                rtPrinter?.let { printer ->
                    Log.d("MyCustomModule", printer.toString())
                    printer.writeMsg(tscCmd.getAppendCmds())
                } ?: throw Exception("Connection issue occurred")
    
                // Return Success
                promise.resolve("success")
    
            } catch (e: Exception) {
                e.printStackTrace()
                promise.reject("PRINT_ERROR", e.message)
            }
        }.start()
    }
    @ReactMethod
    fun print() {
        try {
            val text = "##### HELLO WORLD AC ####"
            if (rtPrinter == null) return
 
            val tscFac: CmdFactory = TscFactory()
            val tscCmd: Cmd = tscFac.create()
 
            val commonSetting = CommonSetting().apply {
                lableSizeBean = LableSizeBean(80, 40)
                labelGap = 3
                printDirection = PrintDirection.NORMAL
                speedEnum = SpeedEnum.getEnumByString("2")
            }
 
            tscCmd.append(tscCmd.headerCmd)
            tscCmd.append(tscCmd.getCommonSettingCmd(commonSetting))
 
            val textSetting = TextSetting().apply {
                tscFontTypeEnum = chooseTscFont
                txtPrintPosition = Position(80, 80)
                printRotation = PrintRotation.Rotate0
            }
 
            tscCmd.append(tscCmd.getTextCmd(textSetting, text, mChartsetName))
            tscCmd.append(tscCmd.getPrintCopies(1))
            rtPrinter?.writeMsgAsync(tscCmd.appendCmds)
            println("Printer data written successfully")
            Log.d("MyCustomModule","Printer data written successfully")
        } catch (e: Exception) {
            println("Printer data Exception: ${'$'}{e.message}")
        }
    }

   

    private fun setPrinterStatusListener() {
        rtPrinter?.setPrintListener { statusBean ->
            val msg = PrinterStatusPareseUtils.getPrinterStatusStr(statusBean)
            println("rongta onPrinterStatus: $msg")
        }
    }

   

   
}
