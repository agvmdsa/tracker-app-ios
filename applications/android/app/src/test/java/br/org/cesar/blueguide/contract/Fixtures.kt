package br.org.cesar.blueguide.contract

/** The expected files under `src/test/resources/contracts/`. */
internal object Fixtures {
    fun text(name: String): String =
        requireNotNull(Fixtures::class.java.getResource("/contracts/$name")) { "missing contracts/$name" }.readText()
}
