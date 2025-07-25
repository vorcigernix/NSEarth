package com.example.nsearth

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.view.ViewGroup
import android.widget.LinearLayout
import android.util.TypedValue
import android.graphics.Color
import androidx.core.graphics.toColorInt

/**
 * Simple settings activity for the 3D Earth live wallpaper
 */
class WallpaperSettingsActivity : Activity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create a simple layout programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor("#1a1a2e".toColorInt())
        }
        
        // Title
        val title = TextView(this).apply {
            text = getString(R.string.settings_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 32)
        }
        
        // Description
        val description = TextView(this).apply {
            text = """
                ${getString(R.string.settings_description)}
                
                ${getString(R.string.features_title)}
                • ${getString(R.string.feature_opengl)}
                • ${getString(R.string.feature_texture)}
                • ${getString(R.string.feature_animation)}
                • ${getString(R.string.feature_battery)}
                
                ${getString(R.string.description_footer)}
                
                ${getString(R.string.settings_footer)}
            """.trimIndent()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.LTGRAY)
            setLineSpacing(8f, 1f)
        }
        
        layout.addView(title)
        layout.addView(description)
        
        setContentView(layout)
    }
} 