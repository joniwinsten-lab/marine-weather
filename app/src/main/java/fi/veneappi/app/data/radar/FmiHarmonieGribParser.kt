package fi.veneappi.app.data.radar

import java.nio.ByteBuffer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.nio.ByteOrder
import java.time.format.DateTimeFormatter

/**
 * Minimal GRIB2 reader for FMI HARMONIE/MEPS precipitation (rain_con, grid_simple).
 * One download replaces hundreds of WFS point queries.
 */
object FmiHarmonieGribParser {
    private const val MISSING = 9999.0
    private const val HARMONIE_BINARY_SCALE = -20

    data class PrecipField(
        val validity: Instant,
        /** [row][col] mm in timestep (kg/m²); row 0 = north. */
        val amountMm: Array<DoubleArray>,
        val bounds: RadarGeoBounds,
    )

    fun parseAll(gribBytes: ByteArray): List<PrecipField> {
        val out = mutableListOf<PrecipField>()
        var offset = 0
        while (offset + 16 <= gribBytes.size) {
            if (gribBytes[offset].toInt() != 'G'.code ||
                gribBytes[offset + 1].toInt() != 'R'.code ||
                gribBytes[offset + 2].toInt() != 'I'.code ||
                gribBytes[offset + 3].toInt() != 'B'.code
            ) {
                break
            }
            val totalLen = readUInt64(gribBytes, offset + 8)
            if (totalLen <= 0 || offset + totalLen > gribBytes.size) break
            parseMessage(gribBytes, offset, totalLen.toInt())?.let { out += it }
            offset += totalLen.toInt()
        }
        return out.sortedBy { it.validity }
    }

    private fun parseMessage(
        bytes: ByteArray,
        base: Int,
        totalLen: Int,
    ): PrecipField? {
        var secOff = base + 16
        val end = base + totalLen
        var section1: ByteArray? = null
        var section3: ByteArray? = null
        var section4: ByteArray? = null
        var section5: ByteArray? = null
        var section6: ByteArray? = null
        var section7: ByteArray? = null
        while (secOff + 5 <= end) {
            if (bytes[secOff].toInt() == '7'.code &&
                bytes[secOff + 1].toInt() == '7'.code &&
                bytes[secOff + 2].toInt() == '7'.code &&
                bytes[secOff + 3].toInt() == '7'.code
            ) {
                break
            }
            val secLen = readUInt32(bytes, secOff)
            if (secLen < 5) break
            val secNum = bytes[secOff + 4].toInt()
            val bodyStart = secOff + 5
            val bodyEnd = secOff + secLen
            if (bodyEnd > end) break
            val body = bytes.copyOfRange(bodyStart, bodyEnd)
            when (secNum) {
                1 -> section1 = body
                3 -> section3 = body
                4 -> section4 = body
                5 -> section5 = body
                6 -> section6 = body
                7 -> section7 = body
            }
            secOff += secLen
        }
        val s1 = section1 ?: return null
        val s3 = section3 ?: return null
        val s4 = section4 ?: return null
        val s5 = section5 ?: return null
        val s6 = section6 ?: return null
        val s7 = section7 ?: return null
        if (s3.size < 36 || s5.size < 16) return null

        val validity = parseValidity(s1, s4) ?: return null
        val stepMinutes = readUInt16(s4, 15)
        // FMI bundles alternate accumulation (e.g. 570 min) and hourly grids; skip non-hourly.
        if (stepMinutes <= 0 || stepMinutes % 60 != 0) return null
        val ni = readUInt16(s3, 27)
        val nj = readUInt16(s3, 31)
        if (ni <= 0 || nj <= 0 || ni > 2000 || nj > 2000) return null

        val nPacked = readUInt16(s5, 2)
        val reference = readFloat32(s5, 6)
        val bitsPerValue = s5[14].toInt() and 0xFF
        if (bitsPerValue <= 0 || bitsPerValue > 32) return null

        val scale = Math.pow(2.0, HARMONIE_BINARY_SCALE.toDouble())
        val packed = unpackBits(s7, nPacked, bitsPerValue)
        val values = DoubleArray(packed.size) { i ->
            val raw = reference + packed[i] * scale
            if (raw >= MISSING - 1 || raw.isNaN() || raw < 0) 0.0 else raw
        }

        val grid = DoubleArray(ni * nj) { Double.NaN }
        val bitmap = s6.copyOfRange(2, s6.size)
        var valueIndex = 0
        var bitIndex = 0
        for (point in 0 until ni * nj) {
            val on = readBitmapBit(bitmap, bitIndex)
            bitIndex++
            if (on) {
                if (valueIndex < values.size) {
                    grid[point] = values[valueIndex]
                    valueIndex++
                }
            }
        }

        val rows = Array(nj) { row ->
            DoubleArray(ni) { col ->
                val v = grid[row * ni + col]
                if (v.isNaN()) 0.0 else v
            }
        }

        val maxMm = rows.maxOfOrNull { r -> r.maxOrNull() ?: 0.0 } ?: 0.0
        if (maxMm <= 0.01) return null

        return PrecipField(
            validity = validity,
            amountMm = rows,
            bounds = RadarGeoBounds(0.0, 0.0, 0.0, 0.0), // filled by repository from request bbox
        )
    }

    private fun parseValidity(
        section1: ByteArray?,
        section4: ByteArray?,
    ): Instant? {
        if (section1 == null || section1.size < 13 || section4 == null || section4.size < 17) return null
        val year = readUInt16(section1, 7)
        val month = section1[9].toInt() and 0xFF
        val day = section1[10].toInt() and 0xFF
        val hour = section1[11].toInt() and 0xFF
        val minute = section1[12].toInt() and 0xFF
        val stepMinutes = readUInt16(section4, 15)
        return runCatching {
            LocalDateTime.of(year, month, day, hour, minute)
                .plusMinutes(stepMinutes.toLong())
                .toInstant(ZoneOffset.UTC)
        }.getOrNull()
    }

    private fun readUInt16(
        bytes: ByteArray,
        offset: Int,
    ): Int {
        if (offset + 2 > bytes.size) return 0
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }

    private fun unpackBits(
        data: ByteArray,
        count: Int,
        bitsPerValue: Int,
    ): IntArray {
        val out = IntArray(count)
        var bitPos = 0
        for (i in 0 until count) {
            var v = 0
            repeat(bitsPerValue) {
                val byteIndex = bitPos / 8
                val bitInByte = 7 - (bitPos % 8)
                val bit =
                    if (byteIndex < data.size) {
                        (data[byteIndex].toInt() shr bitInByte) and 1
                    } else {
                        0
                    }
                v = (v shl 1) or bit
                bitPos++
            }
            out[i] = v
        }
        return out
    }

    private fun readBitmapBit(
        bitmap: ByteArray,
        index: Int,
    ): Boolean {
        val byteIndex = index / 8
        val bitInByte = 7 - (index % 8)
        if (byteIndex >= bitmap.size) return false
        return ((bitmap[byteIndex].toInt() shr bitInByte) and 1) == 1
    }

    private fun readUInt32(
        bytes: ByteArray,
        offset: Int,
    ): Int {
        if (offset + 4 > bytes.size) return 0
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int
    }

    private fun readUInt64(
        bytes: ByteArray,
        offset: Int,
    ): Long {
        if (offset + 8 > bytes.size) return 0L
        return ByteBuffer.wrap(bytes, offset, 8).order(ByteOrder.BIG_ENDIAN).long
    }

    private fun readFloat32(
        bytes: ByteArray,
        offset: Int,
    ): Double {
        if (offset + 4 > bytes.size) return 0.0
        return ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).float.toDouble()
    }

    fun buildDownloadUrl(
        west: Double,
        south: Double,
        east: Double,
        north: Double,
        start: Instant,
        end: Instant,
        originTime: Instant,
    ): String {
        val origin =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(originTime)
        val startParam =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(start)
        val endParam =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .withZone(ZoneOffset.UTC)
                .format(end)
        return (
            "https://opendata.fmi.fi/download?producer=harmonie_scandinavia_surface" +
                "&param=PrecipitationAmount" +
                "&bbox=${west.formatCoord()},${south.formatCoord()},${east.formatCoord()},${north.formatCoord()}" +
                "&origintime=$origin" +
                "&starttime=$startParam" +
                "&endtime=$endParam" +
                "&format=grib2&projection=EPSG:4326&levels=0&timestep=30"
        )
    }

    private fun Double.formatCoord(): String = "%.4f".format(this)
}
