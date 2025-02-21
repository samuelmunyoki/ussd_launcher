package com.kavina.ussd_launcher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import android.os.Handler
import android.os.Looper

class UssdLauncherPlugin: FlutterPlugin, MethodCallHandler {
    private lateinit var channel : MethodChannel
    private lateinit var context: Context
    private lateinit var ussdSessionUnique: UssdSessionUnique
    private lateinit var ussdMultiSession: UssdMultiSession

    companion object {
        private var channel: MethodChannel? = null
        private val handler = Handler(Looper.getMainLooper())

        fun onUssdResult(result: String) {
            handler.post {
                Log.d("UssdLauncherPlugin", "Received USSD Response: $result")
                channel?.invokeMethod("onUssdMessageReceived", result)
            }
        }

        fun setMethodChannel(methodChannel: MethodChannel) {
            channel = methodChannel
        }
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "ussd_launcher")
        channel.setMethodCallHandler(this)
        setMethodChannel(channel)
        context = flutterPluginBinding.applicationContext
        ussdSessionUnique = UssdSessionUnique(context)
        ussdMultiSession = UssdMultiSession(context)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        try {
            when (call.method) {
                "sendUssdRequest" -> {
                    if (!isAccessibilityServiceEnabled()) {
                        openAccessibilitySettings()
                        return
                    }

                    val ussdCode = call.argument<String>("ussdCode")
                    val subscriptionId = call.argument<Int>("subscriptionId") ?: -1
                    if (ussdCode != null) {
                        ussdSessionUnique.sendUssdRequest(ussdCode, subscriptionId, result)
                    } else {
                        Log.e("UssdLauncherPlugin", "Invalid argument: USSD code is required")
                        result.error("INVALID_ARGUMENT", "USSD code is required", null)
                    }
                }
                "multisessionUssd" -> {
                    if (!isAccessibilityServiceEnabled()) {
                        openAccessibilitySettings()
                        return
                    }

                    val ussdCode = call.argument<String>("ussdCode")
                    val slotIndex = call.argument<Int>("slotIndex") ?: 0
                    val options = call.argument<List<String>>("options") ?: emptyList()
                    if (ussdCode != null) {
                        ussdMultiSession.callUSSDWithMenu(ussdCode, slotIndex, options, createHashMap(), object : UssdMultiSession.CallbackInvoke {
                            override fun responseInvoke(message: String) {
                                onUssdResult(message)
                            }
                            override fun over(message: String) {
                                onUssdResult(message)
                                result.success(null)
                            }
                        })
                    } else {
                        Log.e("UssdLauncherPlugin", "Invalid argument: USSD code is required")
                        result.error("INVALID_ARGUMENT", "USSD code is required", null)
                    }
                }
                "sendMessage" -> {
                    val message = call.argument<String>("message")
                    if (message != null) {
                        ussdMultiSession.sendMessage(message, result)
                    } else {
                        Log.e("UssdLauncherPlugin", "Invalid argument: Message is required")
                        result.error("INVALID_ARGUMENT", "Message is required", null)
                    }
                }
                "cancelSession" -> {
                    ussdMultiSession.cancelSession(result)
                }
                "isAccessibilityPermissionEnabled" -> {
                    result.success(isAccessibilityServiceEnabled())
                }
                "openAccessibilitySettings" -> {
                    openAccessibilitySettings()
                    result.success(null)
                }
                "getSimCards" -> {
                    ussdSessionUnique.getSimCards(result)
                }
                else -> {
                    Log.e("UssdLauncherPlugin", "Method not implemented: ${call.method}")
                    result.notImplemented()
                }
            }
        } catch (e: Exception) {
            Log.e("UssdLauncherPlugin", "Error in method ${call.method}", e)
            result.error("UNEXPECTED_ERROR", "An unexpected error occurred: ${e.localizedMessage}", null)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return try {
            val accessibilityEnabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED, 0
            )
            if (accessibilityEnabled == 1) {
                val service = "${context.packageName}/${UssdAccessibilityService::class.java.canonicalName}"
                val settingValue = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                return settingValue?.contains(service) == true
            }
            false
        } catch (e: Exception) {
            Log.e("UssdLauncherPlugin", "Error checking accessibility service", e)
            false
        }
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("UssdLauncherPlugin", "Error opening accessibility settings", e)
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    private fun createHashMap(): HashMap<String, HashSet<String>> {
        val map = HashMap<String, HashSet<String>>()
        map["KEY_ERROR"] = hashSetOf("Error message")
        map["KEY_LOGIN"] = hashSetOf("Login message")
        return map
    }
}
