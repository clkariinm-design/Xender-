package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.network.NetworkUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("AirShare", appName)
  }

  @Test
  fun `formatFileSize formats correctly`() {
    assertEquals("0 B", NetworkUtils.formatFileSize(0))
    assertEquals("500 B", NetworkUtils.formatFileSize(500))
    assertEquals("1.0 KB", NetworkUtils.formatFileSize(1024))
    assertEquals("1.0 MB", NetworkUtils.formatFileSize(1024 * 1024))
  }

  @Test
  fun `formatSpeed formats correctly`() {
    assertEquals("0 B/s", NetworkUtils.formatSpeed(0))
    assertEquals("5.0 MB/s", NetworkUtils.formatSpeed(5 * 1024 * 1024))
  }
}
