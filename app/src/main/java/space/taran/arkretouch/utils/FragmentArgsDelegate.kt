package space.taran.arkretouch.utils

import android.os.Bundle
import androidx.fragment.app.Fragment
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal fun <T> args() = FragmentArgDelegate<T>()

class FragmentArgDelegate<T : Any?> : ReadWriteProperty<Fragment, T?> {

    override fun setValue(thisRef: Fragment, property: KProperty<*>, value: T?) {
        val arguments = thisRef.arguments ?: Bundle().also(thisRef::setArguments)
        val key = property.name
        value?.let { arguments.put(key, it) } ?: arguments.remove(key)
    }

    @Suppress("UNCHECKED_CAST")
    override fun getValue(thisRef: Fragment, property: KProperty<*>): T? =
        thisRef.arguments?.get(property.name) as? T
}

fun <T> Bundle.put(key: String, value: T) {
    when (value) {
        is Boolean -> putBoolean(key, value)
        is String -> putString(key, value)
        is Int -> putInt(key, value)
        is Short -> putShort(key, value)
        is Long -> putLong(key, value)
        is Byte -> putByte(key, value)
        is ByteArray -> putByteArray(key, value)
        is Char -> putChar(key, value)
        is CharArray -> putCharArray(key, value)
        is CharSequence -> putCharSequence(key, value)
        is Float -> putFloat(key, value)
        is Bundle -> putBundle(key, value)
        else -> throw IllegalStateException("Type of $key not supported")
    }
}
