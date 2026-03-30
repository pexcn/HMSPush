package one.yufz.hmspush.app.util

import android.annotation.SuppressLint
import java.lang.reflect.Method

object SystemPropertiesUtil {
    @SuppressLint("PrivateApi")
    private val classSystemProperties: Class<*>? = try {
        Class.forName("android.os.SystemProperties")
    } catch (e: Exception) {
        null
    }

    private val methodGetDefault: Method? by lazy {
        classSystemProperties?.getMethod("get", String::class.java, String::class.java)
    }

    fun get(key: String, def: String = ""): String {
        return try {
            methodGetDefault?.invoke(null, key, def) as? String ?: def
        } catch (e: Exception) {
            def
        }
    }
}
