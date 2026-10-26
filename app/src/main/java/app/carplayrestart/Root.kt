package app.carplayrestart

import java.util.concurrent.TimeUnit

/**
 * Runs shell commands as root on radios that have it (many Android head units come with "su").
 * With root, a restart is instant and invisible: no settings screens open, no helper needed.
 *
 * Never call these on the main thread: the first check can wait a few seconds for a root prompt.
 */
object Root {

    class Output(val ok: Boolean, val text: String)

    @Volatile
    private var available: Boolean? = null

    /** Whether this radio gives the app root. Checked once per launch of the app. */
    fun isAvailable(): Boolean {
        available?.let { return it }
        val out = run("id", timeoutMs = 15_000)
        val ok = out.ok && out.text.contains("uid=0")
        // A timed-out check (e.g. the su prompt answered late) isn't a "no": ask again next time.
        if (ok || out.text != "timed out") available = ok
        return ok
    }

    /** The answer from the check, or null while it's still running. Safe on the main thread. */
    fun known(): Boolean? = available

    fun run(command: String, timeoutMs: Long = 8_000): Output {
        return try {
            val process = ProcessBuilder("su").redirectErrorStream(true).start()
            val text = StringBuilder()
            val reader = Thread {
                try {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        synchronized(text) { text.append(line).append('\n') }
                    }
                } catch (_: Exception) {
                }
            }
            reader.isDaemon = true
            reader.start()
            // Commands go in on su's input rather than "su -c", which some head units' su doesn't understand.
            process.outputStream.bufferedWriter().use { input ->
                input.write(command)
                input.write("\nexit\n")
                input.flush()
            }
            if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                process.destroy()
                return Output(false, "timed out")
            }
            reader.join(500)
            Output(process.exitValue() == 0, synchronized(text) { text.toString() })
        } catch (e: Exception) {
            // No su on this radio (the usual case), or it said no.
            Output(false, e.message ?: "no root")
        }
    }
}
