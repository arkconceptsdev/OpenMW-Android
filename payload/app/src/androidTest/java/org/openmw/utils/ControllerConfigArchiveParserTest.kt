package org.openmw.utils

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ControllerConfigArchiveParserTest {
    @Test
    fun acceptsOpenMwMappingWithCustomBinders() {
        withMapping(
            """
                <?xml version="1.0" encoding="utf-8"?>
                <Controller>
                  <Control name="move-forward">
                    <KeyBinder key="W" />
                    <JoystickButtonBinder button="0" />
                  </Control>
                </Controller>
            """.trimIndent()
        ) { file ->
            ControllerConfigArchive.validateInputMapping(file)
        }
    }

    @Test
    fun rejectsMalformedXmlAndUnexpectedRoot() {
        val malformed = validationFailure("<Controller>")
        val wrongRoot = validationFailure("<NotController />")

        assertTrue(malformed is InvalidControllerConfigException)
        assertTrue(wrongRoot is InvalidControllerConfigException)
    }

    @Test
    fun rejectsDoctypeWithoutResolvingExternalEntities() {
        val failure = validationFailure(
            """<!DOCTYPE Controller [<!ENTITY secret SYSTEM "file:///etc/passwd">]><Controller>&secret;</Controller>"""
        )

        assertTrue(failure is InvalidControllerConfigException)
        assertTrue(failure.message.orEmpty().contains("DOCTYPE"))
    }

    private fun validationFailure(xml: String): Throwable? {
        var failure: Throwable? = null
        withMapping(xml) { file ->
            failure = runCatching {
                ControllerConfigArchive.validateInputMapping(file)
            }.exceptionOrNull()
        }
        return failure
    }

    private fun withMapping(xml: String, test: (File) -> Unit) {
        val cacheDir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val mapping = File.createTempFile("controller-mapping-", ".xml", cacheDir)
        try {
            mapping.writeText(xml)
            test(mapping)
        } finally {
            mapping.delete()
        }
    }
}