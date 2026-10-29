package com.cripta.app.media.intro

/**
 * Streaming downmix and downsample of interleaved PCM to mono [OUT_RATE] Hz. Each output sample is
 * the mean of the input samples in its interval: a crude low-pass, but the same for every video,
 * which is all the fingerprint comparison needs.
 */
class MonoResampler(srcRate: Int, private val channels: Int) {
    private val step = srcRate.toDouble() / OUT_RATE
    private var next = step
    private var pos = 0L
    private var acc = 0.0
    private var cnt = 0
    private var ch = 0
    private var frame = 0.0
    private var out = FloatArray(1 shl 16)

    /** Output samples so far. */
    var size = 0
        private set

    /** [samples] in -1..1, interleaved by channel; only the first [count] are read. */
    fun push(samples: FloatArray, count: Int = samples.size) {
        for (i in 0 until count) {
            frame += samples[i]
            if (++ch < channels) continue
            ch = 0
            acc += frame / channels; frame = 0.0
            cnt++; pos++
            if (pos >= next) {
                if (size == out.size) out = out.copyOf(size * 2)
                out[size++] = (acc / cnt).toFloat()
                acc = 0.0; cnt = 0
                next += step
            }
        }
    }

    fun result(): FloatArray = out.copyOf(size)

    companion object {
        const val OUT_RATE = 5512
    }
}
