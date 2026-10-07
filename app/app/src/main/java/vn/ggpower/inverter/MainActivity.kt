package vn.ggpower.inverter

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import vn.ggpower.inverter.navigation.AppNavGraph
import vn.ggpower.inverter.ui.theme.GgPowerTheme

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GgPowerTheme {
                AppNavGraph()
            }
        }
    }
}
