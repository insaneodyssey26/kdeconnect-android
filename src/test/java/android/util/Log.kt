package android.util

// Based on https://stackoverflow.com/questions/36787449/how-to-mock-method-e-in-log
object Log {
    @JvmStatic
    fun d(tag: String?, msg: String?): Int {
        println("DEBUG: $tag: $msg")
        return 0
    }

    @JvmStatic
    fun i(tag: String?, msg: String?): Int {
        println("INFO: $tag: $msg")
        return 0
    }

    @JvmStatic
    fun w(tag: String?, msg: String?): Int {
        println("WARN: $tag: $msg")
        return 0
    }

    @JvmStatic
    fun e(tag: String?, msg: String?): Int {
        println("ERROR: $tag: $msg")
        return 0
    }

    @JvmStatic
    fun e(tag: String?, msg: String?, e: Throwable?): Int {
        println("ERROR: $tag: $msg: $e")
        return 0
    }
}
