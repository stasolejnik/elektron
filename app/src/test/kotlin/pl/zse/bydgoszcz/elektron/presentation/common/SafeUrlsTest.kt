package pl.zse.bydgoszcz.elektron.presentation.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Linki: tylko http(s); z zewnętrznych intentów tylko domeny szkoły (bez podróbek). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafeUrlsTest {

    @Test
    fun onlyWebSchemes() {
        assertTrue(SafeUrls.isWebUrl("https://zse.bydgoszcz.pl/a-w1,10,1.html"))
        assertTrue(SafeUrls.isWebUrl("http://zse.edu.bydgoszcz.pl/x.jpg"))
        assertFalse(SafeUrls.isWebUrl("javascript:alert(1)"))
        assertFalse(SafeUrls.isWebUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(SafeUrls.isWebUrl("file:///sdcard/x.html"))
        assertFalse(SafeUrls.isWebUrl(""))
        assertFalse(SafeUrls.isWebUrl(null))
    }

    @Test
    fun schoolDomainsOnly() {
        assertTrue(SafeUrls.isSchoolUrl("https://zse.bydgoszcz.pl/"))
        assertTrue(SafeUrls.isSchoolUrl("https://plan.zse.bydgoszcz.pl/lista.html"))
        assertTrue(SafeUrls.isSchoolUrl("https://zse.edu.bydgoszcz.pl/assets/a.jpg"))
        assertFalse(SafeUrls.isSchoolUrl("https://zse.bydgoszcz.pl.evil.example/"))
        assertFalse(SafeUrls.isSchoolUrl("https://evilzse.bydgoszcz.pl/"))
        assertFalse(SafeUrls.isSchoolUrl("https://example.com/?next=zse.bydgoszcz.pl"))
    }
}
