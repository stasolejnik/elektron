package pl.zse.bydgoszcz.elektron.presentation.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Aktywność za kontekstem Compose (LocalContext bywa opakowany w ContextWrapper). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
