package com.aliothmoon.maameow

import androidx.appcompat.app.AppCompatActivity
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLocaleContractTest {

    @Test
    fun mainActivity_extendsAppCompatActivity_forRuntimeLocaleUpdates() {
        assertTrue(
            "MainActivity must extend AppCompatActivity so AppCompatDelegate locale changes can recreate the host activity",
            AppCompatActivity::class.java.isAssignableFrom(MainActivity::class.java)
        )
    }
}
