package com.deckpuller.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UniformTypeIdentifiers.UTTypeCommaSeparatedText
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberCsvPicker(onText: (String?) -> Unit): () -> Unit {
    val currentOnText = rememberUpdatedState(onText)
    // The delegate must outlive the call, so it's remembered for the composition's lifetime.
    val delegate = remember {
        object : NSObject(), UIDocumentPickerDelegateProtocol {
            override fun documentPicker(
                controller: UIDocumentPickerViewController,
                didPickDocumentsAtURLs: List<*>,
            ) {
                val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
                if (url == null) {
                    currentOnText.value(null)
                    return
                }
                val scoped = url.startAccessingSecurityScopedResource()
                val text = NSString.stringWithContentsOfURL(url, NSUTF8StringEncoding, null)
                if (scoped) url.stopAccessingSecurityScopedResource()
                currentOnText.value(text)
            }

            override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
                currentOnText.value(null)
            }
        }
    }
    return {
        val picker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypeCommaSeparatedText, UTTypePlainText),
        )
        picker.delegate = delegate
        topViewController()?.presentViewController(picker, animated = true, completion = null)
    }
}
