package com.jarvis.app

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel

class AiBrain(private val context: Context) {

    private val generativeModel = GenerativeModel(
        modelName = "gemini-1.5-flash",
        apiKey = BuildConfig.GEMINI_API_KEY
    )

    // User query aur Contacts list ko analyze karke AI se JSON response lega
    suspend fun processCommand(userQuery: String, contactsList: String): String {
        return try {
            val prompt = """
            You are Jarvis, a smart phone voice assistant.
            Here is the user's contact list:
            [$contactsList]
            
            User said: "$userQuery"
            
            Analyze the request. If the user wants to make a call, match the name with the contact list.
            You MUST return your response strictly in the following JSON format (do not use markdown backticks like ```json):
            {
              "action": "CALL",
              "target_name": "Exact Name from list",
              "phone_number": "number if available or empty",
              "status": "EXACT_MATCH", 
              "message": "Message to speak if multiple matches or general response"
            }
            
            Rules:
            1. If exact single match found for call, set status to "EXACT_MATCH".
            2. If multiple matches found (e.g. 3 Krish), set status to "MULTIPLE_MATCHES" and list the options in "message" field so Jarvis can speak it.
            3. If it's a normal chat/question, set action to "TALK" and put the answer in "message".
            """.trimIndent()

            val response = generativeModel.generateContent(prompt)
            response.text?.trim() ?: "{\"action\":\"TALK\",\"message\":\"कोई जवाब नहीं मिला\"}"
        } catch (e: Exception) {
            "{\"action\":\"TALK\",\"message\":\"त्रुटि: ${e.localizedMessage}\"}"
        }
    }
}
