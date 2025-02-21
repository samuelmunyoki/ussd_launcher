package com.kavina.ussd_launcher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Path


class UssdAccessibilityService : AccessibilityService() {
    companion object {
        private var instance: UssdAccessibilityService? = null
        private var pendingMessages: ArrayDeque<String> = ArrayDeque()
        var hideDialogs = false // Nouvelle variable pour contrôler le comportement

        // Envoie une réponse dans la session USSD
        fun sendReply(messages: List<String>) {
            println("Setting pending messages: $messages")
            pendingMessages.clear()
            pendingMessages.addAll(messages)
            instance?.performReply()
        }

        // Annule la session USSD
        fun cancelSession() {
            instance?.let { service ->
                val rootInActiveWindow = service.rootInActiveWindow
                val cancelButton = rootInActiveWindow?.findAccessibilityNodeInfosByViewId("android:id/button2")
                cancelButton?.firstOrNull()?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                println("Bouton d'annulation USSD cliqué")
            } ?: run {
                println("Instance du service d'accessibilité non disponible pour annuler la session")
            }
        }
    }

    private fun performReply(retryCount: Int = 0) {
        if (retryCount > 3) {
            println("Max retries reached. Aborting.")
            return
        }

        try {
            if (pendingMessages.isEmpty()) return

            val message = pendingMessages.removeFirstOrNull()
            println("Performing reply with message: $message")
            
            Handler(Looper.getMainLooper()).postDelayed({
                val rootInActiveWindow = this.rootInActiveWindow ?: return@postDelayed
                println("Root in active window: $rootInActiveWindow")
        
                // Find the input field
                val editText = findInputField(rootInActiveWindow)
                if (editText == null) {
                    println("Input field not found, retrying...")
                    performReply(retryCount + 1)
                    return@postDelayed
                }
        
                // Insert the text into the input field
                val bundle = Bundle()
                editText.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
                val setTextSuccess = editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                println("Set text action performed: $setTextSuccess")
        
                // Find and click the confirm button
                val button = findConfirmButton(rootInActiveWindow)
                if (button != null) {
                    val clickSuccess = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    println("Click action performed: $clickSuccess")
                    // Wait before sending the next message
                    Handler(Looper.getMainLooper()).postDelayed({
                        performReply()
                    }, 3000) // 3 second delay; adjust if needed
                } else {
                    println("Confirm button not found, trying alternative methods")
                    tryAlternativeConfirmMethods(rootInActiveWindow)
                }
            }, 800) // Delay of 800ms before executing the above block
            
        } catch(e: Exception) {
            println("performReply ::: Error in performReply ::: $e")
        }
    }


        // Trouve le champ de saisie dans l'interface USSD
    private fun findInputField(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Common USSD input view classes
        val targetClassNames = listOf(
            "android.widget.EditText",
            "android.widget.TextView",
            // Samsung often uses this
            "android.widget.CustomEditText",
            // Some devices use these
            "android.widget.NumberPicker",
            "android.widget.ImeEditText"
        )
        
        // Try to find nodes by each possible class name
        for (className in targetClassNames) {
            val nodes = findNodesByClassName(root, className)
            val inputNode = nodes.firstOrNull { node ->
                // Additional checks to verify it's likely a USSD input
                node.isEditable || 
                node.isFocused || 
                node.isClickable
            }
            if (inputNode != null) return inputNode
        }
        
        return null
    }

    // Trouve le bouton de confirmation dans l'interface USSD
    private fun findConfirmButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val buttons = findNodesByClassName(root, "android.widget.Button")
        return buttons.firstOrNull {
            it.text?.toString()?.toLowerCase() in listOf("send", "ok", "submit", "confirmer", "envoyer")
        }
    }

    // Essaie des méthodes alternatives pour confirmer l'action USSD
    private fun tryAlternativeConfirmMethods(root: AccessibilityNodeInfo) {
        // Méthode 1: Essayer de cliquer sur tous les boutons
        val allButtons = findNodesByClassName(root, "android.widget.Button")
        for (button in allButtons) {
            println("Attempting to click button: ${button.text}")
            val clickSuccess = button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clickSuccess) {
                println("Successfully clicked button: ${button.text}")
                // Attendre un court instant avant d'envoyer le message suivant
                Handler(Looper.getMainLooper()).postDelayed({
                    performReply()
                }, 3000) // 3 secondes de délai, ajustez si nécessaire
                return
            }
        }

        // Méthode 2: Essayer de cliquer sur tous les éléments cliquables
        val clickableNodes = findClickableNodes(root)
        for (node in clickableNodes) {
            println("Attempting to click node: ${node.className}")
            val clickSuccess = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clickSuccess) {
                println("Successfully clicked node: ${node.className}")
                // Attendre un court instant avant d'envoyer le message suivant
                Handler(Looper.getMainLooper()).postDelayed({
                    performReply()
                }, 3000) // 3 secondes de délai, ajustez si nécessaire
                return
            }
        }

        // Méthode 3: Simuler un appui sur la touche "Entrée"
        val enterKeyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
        val dispatchSuccess = dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(Path(), 0, 1))
                .build(),
            null,
            null
        )
        println("Dispatched Enter key event: $dispatchSuccess")
    }

    // Trouve tous les nœuds cliquables dans l'interface
    private fun findClickableNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.isClickable) {
                result.add(node)
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return result
    }

    // Trouve tous les nœuds d'une classe spécifique dans l'interface
    private fun findNodesByClassName(root: AccessibilityNodeInfo?, className: String): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        if (root == null) return result

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node.className?.toString() == className) {
                result.add(node)
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }
        }
        return result
    }

    // Gère les événements d'accessibilité
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (hideDialogs) {
                // Fermer automatiquement la fenêtre de dialogue USSD
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }

        try {
            println("Accessibility event received: ${event?.eventType}")
            println("Event source: ${event?.source}")
            println("Event class name: ${event?.className}")
            println("Event package name: ${event?.packageName}")
            println("Event text: ${event?.text}")

            if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event?.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

                 Handler(Looper.getMainLooper()).postDelayed({
                val nodeInfo = event.source
                if (nodeInfo != null) {
                    val ussdMessage = findUssdMessage(nodeInfo)
                    if (ussdMessage != null && ussdMessage.isNotEmpty()) {
                        UssdLauncherPlugin.onUssdResult(ussdMessage)
                    }

                    // Tenter d'insérer le message en attente, s'il y en a un
                    if (pendingMessages.isNotEmpty()) {
                        performReply()
                    }

                    nodeInfo.recycle()
                }
                 },1000)
            }
        } catch (e: Exception) {
            println("UssdAccessibilityService ::: Error in onAccessibilityEvent ::: $e")
        }
    }

    // Trouve le message USSD dans l'interface
    private fun findUssdMessage(node: AccessibilityNodeInfo): String? {
        // Vérifier si le nœud est un champ de saisie
        if (node.className?.toString() == "android.widget.EditText") {
            return null
        }

        // Si c'est une boîte de dialogue USSD, retourner son texte
        if (node.className?.toString() == "android.widget.TextView" && node.text != null) {
            return node.text.toString()
        }

        // Parcourir les nœuds enfants
        for (i in 0 until node.childCount) {
            val childNode = node.getChild(i)
            val message = findUssdMessage(childNode)
            if (message != null) {
                return message
            }
        }

        return null
    }

    override fun onInterrupt() {}

    // Appelé lorsque le service d'accessibilité est connecté
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        println("UssdAccessibilityService connected")
    }
}
