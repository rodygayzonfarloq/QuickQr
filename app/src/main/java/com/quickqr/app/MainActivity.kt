package com.quickqr.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import com.quickqr.app.databinding.ActivityMainBinding
import com.quickqr.app.ui.generator.GeneratorFragment
import com.quickqr.app.ui.history.HistoryFragment
import com.quickqr.app.ui.scanner.ScannerFragment

/**
 * Hosts the bottom navigation and swaps between the three main screens.
 * We use replace() (not hide/show) on purpose: replace() runs the fragment
 * through onPause/onDestroyView when you leave a tab, which is what lets
 * ScannerFragment reliably release the camera when you're not on the Scan tab.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentTabId = R.id.nav_scan

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragmentContainer, ScannerFragment())
            }
        }

        binding.bottomNav.setOnItemSelectedListener { item ->
            // Skip the transaction entirely when the user re-taps the tab they're
            // already on - otherwise re-tapping "Scan" would tear down and rebuild
            // the fragment (and restart the camera) for no reason.
            if (item.itemId != currentTabId) {
                currentTabId = item.itemId
                val fragment = when (item.itemId) {
                    R.id.nav_scan -> ScannerFragment()
                    R.id.nav_history -> HistoryFragment()
                    R.id.nav_create -> GeneratorFragment()
                    else -> ScannerFragment()
                }
                supportFragmentManager.commit {
                    setReorderingAllowed(true)
                    replace(R.id.fragmentContainer, fragment)
                }
            }
            true
        }
    }
}
