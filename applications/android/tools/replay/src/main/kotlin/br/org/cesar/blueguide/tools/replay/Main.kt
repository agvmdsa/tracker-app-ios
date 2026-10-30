package br.org.cesar.blueguide.tools.replay

import br.org.cesar.blueguide.core.positioning.PositioningEngine
import br.org.cesar.blueguide.core.positioning.PositioningSetup
import java.io.File
import java.util.Locale
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val status = System.out.bufferedWriter().use { out -> ReplayCommand.run(args.toList(), out, System.err) }
    exitProcess(status)
}

/** `replay [opções] <gravação.csv | pasta>...` — see [USAGE]. */
object ReplayCommand {

    const val OK = 0

    /** A recording couldn't be read; the others were still replayed. */
    const val FAILED = 1

    /** Wrong arguments, or nothing to replay. */
    const val USAGE_ERROR = 2

    val USAGE = """
        |uso: replay [opções] <gravação.csv | pasta>...
        |
        |Recalcula, segundo a segundo, onde o app teria colocado o celular durante cada gravação de
        |fingerprint, com a calibração guardada nela, e escreve um CSV com a posição, a verdadeira, o
        |erro e o que cada âncora ouviu. Uma pasta conta como as gravações dentro dela. Um .csv sem o
        |.json ao lado foi interrompido e fica de fora, como no app.
        |
        |  --center-rssi <dBm>     usa este "RSSI no centro" em vez do da gravação
        |  --touch-rssi <dBm>      usa este "RSSI encostado" em vez do da gravação
        |  --max-speed-kmh <km/h>  usa este limite de velocidade em vez do da gravação
        |  --no-speed-limit        sem limite de velocidade
        |  --engine <nome>         recalcula com este motor de posicionamento; repetido, compara os
        |                          motores nas mesmas gravações (padrão: ${Engines.APP}, o do app)
        |  --out <arquivo>         escreve o CSV neste arquivo (senão, na saída padrão)
        |
        |O resumo do erro de cada gravação sai na saída de erro.
    """.trimMargin()

    private class Arguments(val paths: List<File>, val override: CalibrationOverride, val out: File?, val engines: List<String>)

    /** [engines]: the ones `--engine` can name; a test passes its own. */
    fun run(
        args: List<String>,
        stdout: Appendable,
        stderr: Appendable,
        engines: Map<String, () -> PositioningEngine> = Engines.BY_NAME
    ): Int {
        val arguments = try {
            parse(args, engines)
        } catch (e: IllegalArgumentException) {
            stderr.appendLine(e.message).appendLine().appendLine(USAGE)
            return USAGE_ERROR
        } ?: run {
            stdout.appendLine(USAGE)
            return OK
        }

        var status = OK
        val csvs = try {
            arguments.paths.flatMap(::csvFiles).distinct()
        } catch (e: IllegalArgumentException) {
            stderr.appendLine(e.message)
            return USAGE_ERROR
        }
        val recordings = csvs.mapNotNull { csv ->
            try {
                val recording = Recording.open(csv)
                if (recording == null) stderr.appendLine("${csv.name}: sem o .json, foi interrompida; fica de fora.")
                recording
            } catch (e: IllegalArgumentException) {
                stderr.appendLine(e.message)
                status = FAILED
                null
            }
        }
        if (recordings.isEmpty()) {
            stderr.appendLine("Nenhuma gravação para recalcular.")
            return USAGE_ERROR
        }

        val beacons = recordings.flatMap { it.metadata.anchors.map { anchor -> anchor.id } }.distinct().sortedBy { it.value }
        val comparing = arguments.engines.size > 1
        fun label(name: String, engine: String) = if (comparing) "$name [$engine]" else name
        val all = mutableListOf<ReplayedSecond>()
        val write: (Appendable) -> Unit = { out ->
            out.appendLine(ReplayTable.header(beacons))
            for (recording in recordings) {
                val setup = setupFor(recording, arguments.override, stderr)
                for (engine in arguments.engines) {
                    val seconds = try {
                        replay(recording, setup, engine, engines.getValue(engine)())
                    } catch (e: IllegalArgumentException) {
                        stderr.appendLine(e.message)
                        status = FAILED
                        break
                    }
                    seconds.forEach { out.appendLine(ReplayTable.line(it, beacons)) }
                    stderr.appendLine("${label(recording.name, engine)}: ${describe(ErrorSummary.of(seconds))}")
                    all += seconds
                }
            }
        }
        val out = arguments.out
        if (out == null) write(stdout) else out.bufferedWriter().use(write)
        if (recordings.size > 1 || comparing) {
            for (engine in arguments.engines) {
                stderr.appendLine("${label("Todas", engine)}: ${describe(ErrorSummary.of(all.filter { it.engine == engine }))}")
            }
        }
        return status
    }

    /** The recording's own setup, changed by the options. A recording from before version 2 kept
     *  no calibration: it is replayed with the app's defaults, unless the options give one. */
    private fun setupFor(recording: Recording, override: CalibrationOverride, stderr: Appendable): PositioningSetup {
        val recorded = recording.metadata.positioningSetup
        val setup = override.applied(recorded ?: PositioningSetup(recording.metadata.anchors))
        if (recorded == null && !override.setsCalibration) {
            stderr.appendLine(
                "${recording.name}: gravação da versão ${recording.metadata.schemaVersion}, sem a calibração; usando " +
                    "${number(setup.centerRssi)} dBm no centro e ${number(setup.touchRssi)} dBm encostado " +
                    "(escolha com --center-rssi e --touch-rssi)."
            )
        }
        return setup
    }

    /** Null for `--help`. */
    private fun parse(args: List<String>, known: Map<String, () -> PositioningEngine>): Arguments? {
        val paths = mutableListOf<File>()
        var override = CalibrationOverride()
        var out: File? = null
        val engines = mutableListOf<String>()
        val remaining = ArrayDeque(args)
        fun value(option: String): String = remaining.removeFirstOrNull() ?: throw IllegalArgumentException("$option precisa de um valor.")
        fun number(option: String): Double = value(option).let { text ->
            text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
                ?: throw IllegalArgumentException("$option: '$text' não é um número.")
        }
        while (remaining.isNotEmpty()) {
            when (val arg = remaining.removeFirst()) {
                "-h", "--help" -> return null
                "--center-rssi" -> override = override.copy(centerRssi = number(arg))
                "--touch-rssi" -> override = override.copy(touchRssi = number(arg))
                "--max-speed-kmh" ->
                    override =
                        override.copy(maxSpeedKmh = number(arg).also { require(it > 0) { "$arg precisa ser maior que zero." } })
                "--no-speed-limit" -> override = override.copy(noSpeedLimit = true)
                "--out" -> out = File(value(arg))
                "--engine" -> engines += value(arg).also { name ->
                    require(name in known) { "Motor desconhecido: $name (há: ${known.keys.joinToString()})." }
                }
                else -> if (arg.startsWith("--")) throw IllegalArgumentException("Opção desconhecida: $arg") else paths += File(arg)
            }
        }
        require(paths.isNotEmpty()) { "Diga quais gravações recalcular." }
        return Arguments(paths, override, out, engines.distinct().ifEmpty { listOf(Engines.APP) })
    }

    /** A folder's recordings, by name (so by date); a file as given. */
    private fun csvFiles(path: File): List<File> = when {
        path.isDirectory -> path.listFiles { file -> file.isFile && file.extension == "csv" }.orEmpty().sortedBy { it.name }
        path.exists() -> listOf(path)
        else -> throw IllegalArgumentException("${path.path}: não existe.")
    }

    private fun describe(summary: ErrorSummary): String {
        val placed = "${summary.seconds} s, posição em ${summary.fixes}"
        if (summary.fixes == 0) return "$placed."
        return "$placed; erro médio ${meters(summary.mean)}, p95 ${meters(summary.p95)}, máximo ${meters(summary.max)}."
    }

    private fun meters(value: Double?) = String.format(Locale.ROOT, "%.2f m", value).replace('.', ',')

    private fun number(value: Double) = String.format(Locale.ROOT, "%.1f", value).replace('.', ',')
}
