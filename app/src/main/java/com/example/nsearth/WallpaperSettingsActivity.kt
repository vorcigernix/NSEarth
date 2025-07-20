package com.example.nsearth

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.view.ViewGroup
import android.widget.LinearLayout
import android.util.TypedValue
import android.graphics.Color

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
            setBackgroundColor(Color.parseColor("#1a1a2e"))
        }
        
        // Title
        val title = TextView(this).apply {
            text = "Network State"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 32)
        }
        
        // Description
        val description = TextView(this).apply {
            text = """
                Enjoy a beautiful 3D spinning Earth as your Network State live wallpaper!
                
                Features:
                • High-performance OpenGL ES 2.0 rendering
                • Realistic Earth texture with clouds
                • Smooth 60 FPS animation
                • Battery optimized
                
                The Earth rotates automatically and provides a mesmerizing view of our planet.
                
                Settings can be added here in future updates.
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