package com.cutejarvis.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/**
 * Tiny translation layer keyed by the English text. To add a language, add an entry to [TABLES]
 * (and to [LANGUAGES]); any string missing from a table falls back to English.
 */
val LocalLang = compositionLocalOf { "en" }
val LANGUAGES = listOf("en" to "English", "ne" to "नेपाली")

private val NE = mapOf(
    "Home" to "गृहपृष्ठ", "Chat" to "च्याट", "Memories" to "सम्झनाहरू", "Skills" to "सीपहरू", "Settings" to "सेटिङ",
    "Good Morning" to "शुभ प्रभात", "Good Afternoon" to "शुभ दिउँसो", "Good Evening" to "शुभ साँझ", "Hello" to "नमस्ते",
    "How can I help you today?" to "आज म तपाईंलाई कसरी मद्दत गर्न सक्छु?",
    "Get Started" to "सुरु गर्नुहोस्", "Welcome to" to "स्वागत छ", "Your Personal AI Assistant" to "तपाईंको व्यक्तिगत एआई सहायक",
    "Loading..." to "लोड हुँदै...", "Type a message…" to "सन्देश लेख्नुहोस्…", "Online" to "अनलाइन",
    "Listening…" to "सुन्दै छु…", "Say something" to "केही भन्नुहोस्", "Thinking…" to "सोच्दै छु…", "Speaking…" to "बोल्दै छु…",
    "Standby" to "स्ट्यान्डबाइ", "Tap the mic to talk" to "बोल्न माइकमा थिच्नुहोस्", "Talk to Jarvis" to "जार्भिससँग बोल्नुहोस्",
    "Installed" to "स्थापित", "Install" to "स्थापना", "All" to "सबै", "Available" to "उपलब्ध",
    "Memory" to "सम्झना", "Add" to "थप्नुहोस्", "Add a memory" to "सम्झना थप्नुहोस्", "No memories yet." to "अहिलेसम्म कुनै सम्झना छैन।",
    "Chat History" to "च्याट इतिहास", "Permissions Center" to "अनुमति केन्द्र", "Granted" to "दिइएको", "Not granted" to "दिइएको छैन",
    "Grant" to "अनुमति दिनुहोस्", "Scan Permissions" to "अनुमति जाँच्नुहोस्", "Phone Control" to "फोन नियन्त्रण",
    "Assistant" to "सहायक", "Save Changes" to "परिवर्तन सुरक्षित गर्नुहोस्", "Assistant name" to "सहायकको नाम",
    "Wake phrase" to "जगाउने वाक्यांश", "Sleep phrase" to "सुत्ने वाक्यांश", "Voice Settings" to "आवाज सेटिङ",
    "Speech speed" to "बोल्ने गति", "Language" to "भाषा", "API & Integrations" to "एपीआई र एकीकरण",
    "Privacy & Security" to "गोपनीयता र सुरक्षा", "Developer Tools" to "विकासकर्ता उपकरण", "About" to "बारेमा",
    "Add New Provider" to "नयाँ प्रदायक थप्नुहोस्", "Test Connection" to "जडान जाँच्नुहोस्",
    "Set as default" to "पूर्वनिर्धारित बनाउनुहोस्", "Remove" to "हटाउनुहोस्", "Save Provider" to "प्रदायक सुरक्षित गर्नुहोस्",
    "Connected" to "जडान भयो", "Not tested" to "जाँच गरिएको छैन", "Default" to "पूर्वनिर्धारित", "Cancel" to "रद्द गर्नुहोस्",
    "Allow" to "अनुमति दिनुहोस्", "Deny" to "अस्वीकार", "Ask anything" to "जे पनि सोध्नुहोस्", "Search this session…" to "यो सत्रमा खोज्नुहोस्…",
    "Allow this action?" to "यो कार्य गर्न दिने?", "Appearance & Language" to "रूप र भाषा", "Voice Assistant" to "आवाज सहायक",
)

val TABLES: Map<String, Map<String, String>> = mapOf("ne" to NE)

@Composable fun t(en: String): String = TABLES[LocalLang.current]?.get(en) ?: en
