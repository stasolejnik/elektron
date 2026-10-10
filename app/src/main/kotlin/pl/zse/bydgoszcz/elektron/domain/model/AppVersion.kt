package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Porównywanie wersji aplikacji: "0.5.0-alpha" < "0.5.0-beta" < "0.5.0-rc1" < "0.5.0" < "0.5.1".
 * Akceptuje tagi z GitHuba ("v0.5.0-alpha") i wersje debug ("0.5.0-alpha-debug").
 */
object AppVersion {

    private data class Parsed(val core: List<Int>, val preWord: String?, val preNumbers: List<Int>, val development: Boolean)

    private val PRE_WORD = Regex("^([a-z]+)")
    private val PRE_NUMBER = Regex("(\\d+)")

    private fun parse(version: String): Parsed {
        var s = version.trim().removePrefix("v").removePrefix("V").removeSuffix("-debug")
        val development = s.endsWith("-dev")
        s = s.removeSuffix("-dev")
        val dash = s.indexOf('-')
        val coreText = if (dash < 0) s else s.substring(0, dash)
        val core = coreText.split('.').map { it.toIntOrNull() ?: 0 }
        val pre = if (dash < 0) null else s.substring(dash + 1).lowercase().ifBlank { null }
        s = pre.orEmpty()
        return Parsed(
            core = core,
            preWord = pre?.let { PRE_WORD.find(it)?.groupValues?.get(1) ?: it },
            preNumbers = PRE_NUMBER.findAll(s).map { it.value.toIntOrNull() ?: 0 }.toList(),
            development = development
        )
    }

    private fun preRank(word: String): Int = when (word) {
        "alpha" -> 0
        "beta" -> 1
        "rc" -> 2
        else -> -1
    }

    /** < 0: a starsza, 0: równe, > 0: a nowsza. */
    fun compare(a: String, b: String): Int {
        val pa = parse(a)
        val pb = parse(b)
        for (i in 0 until maxOf(pa.core.size, pb.core.size, 3)) {
            val c = pa.core.getOrElse(i) { 0 }.compareTo(pb.core.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        // Ta sama wersja bazowa: wydanie stabilne jest nowsze od każdej przedpremierowej.
        if (pa.preWord == null && pb.preWord == null) return pb.development.compareTo(pa.development)
        if (pa.preWord == null) return 1
        if (pb.preWord == null) return -1
        val rank = preRank(pa.preWord).compareTo(preRank(pb.preWord))
        if (rank != 0) return rank
        for (i in 0 until maxOf(pa.preNumbers.size, pb.preNumbers.size)) {
            val c = pa.preNumbers.getOrElse(i) { 0 }.compareTo(pb.preNumbers.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return pb.development.compareTo(pa.development)
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    /** Wydanie stabilne: bez dopisku alpha/beta/rc i bez "-dev" ("1.0.1", nie "1.0.1-beta1"). */
    fun isStable(version: String): Boolean = parse(version).let { it.preWord == null && !it.development }
}
