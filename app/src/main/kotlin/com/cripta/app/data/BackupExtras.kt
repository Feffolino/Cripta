package com.cripta.app.data

import com.cripta.app.data.db.AudioPrintEntity
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.data.db.FaceRejectionEntity
import com.cripta.app.data.db.FaceScanEntity
import com.cripta.app.data.db.FaceTrackEntity
import com.cripta.app.data.db.PersonEntity
import com.cripta.app.data.db.PersonMediaEntity
import com.cripta.app.data.db.RejectedPrintEntity
import com.cripta.app.data.db.SegmentRuleEntity
import com.cripta.app.data.db.SignatureEntity
import com.cripta.app.data.db.SignatureSoundEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * What a backup carries after the files: the people (faces found, names, notes, links, choices),
 * the face analysis of each media, and the intro/outro analysis (fingerprints, signatures, rules,
 * rejected sounds). Written as [MAGIC], a JSON index, then the binary parts (embeddings, face
 * pictures, fingerprints) in the index's order, so nothing large is held in a string.
 *
 * Media are referred to by their position in the archive (ids change on restore). An app that
 * predates this part stops reading after the files and ignores it.
 */
internal object BackupExtras {
    const val MAGIC = 0x43524558 // "CREX"

    private fun JSONObject.optLongOrNull(k: String): Long? = if (!has(k) || isNull(k)) null else optLong(k)
    private fun JSONObject.optStrOrNull(k: String): String? = if (!has(k) || isNull(k)) null else optString(k)
    private fun DataOutputStream.blob(b: ByteArray) { writeInt(b.size); write(b) }
    private fun DataInputStream.blob(): ByteArray {
        val n = readInt()
        require(n in 0..64_000_000) { "File corrotto" }
        return ByteArray(n).also { readFully(it) }
    }

    suspend fun write(db: CriptaDatabase, fileIndex: Map<String, Int>, out: DataOutputStream) {
        val faces = db.faceDao()
        val intro = db.introDao()
        val persons = faces.persons()
        val tracks = ArrayList<FaceTrackEntity>()
        for (id in fileIndex.keys) tracks += faces.tracksOfFile(id)
        val trackIds = tracks.map { it.id }.toSet()
        val prints = intro.backupPrints().filter { it.fileId in fileIndex }
        val signatures = intro.backupSignatures()
        val sounds = intro.backupSignatureSounds()
        val rules = intro.backupRules()
        val rejected = intro.backupRejected()
        fun origin(id: String): Any = fileIndex[id] ?: id

        val index = JSONObject()
            .put("persons", JSONArray().apply {
                persons.forEach {
                    put(JSONObject().put("id", it.id).put("name", it.name).put("ignored", it.ignored).put("favorite", it.favorite)
                        .put("createdAt", it.createdAt).put("notes", it.notes).put("links", it.links)
                        .put("cover", it.coverTrackId?.takeIf { c -> c in trackIds } ?: JSONObject.NULL))
                }
            })
            .put("personMedia", JSONArray().apply {
                faces.allPersonMedia().forEach { m -> fileIndex[m.fileId]?.let { put(JSONObject().put("p", m.personId).put("f", it)) } }
            })
            .put("scans", JSONArray().apply {
                faces.allScans().forEach { s ->
                    val f = fileIndex[s.fileId] ?: return@forEach
                    put(JSONObject().put("f", f).put("status", s.status).put("read", s.framesRead).put("failed", s.framesFailed)
                        .put("faces", s.faces).put("rejects", s.rejects).put("version", s.version).put("at", s.scannedAt)
                        .put("clustered", s.clustered).put("ms", s.ms).put("stats", s.stats))
                }
            })
            .put("tracks", JSONArray().apply {
                tracks.forEach { t ->
                    put(JSONObject().put("id", t.id).put("f", fileIndex[t.fileId]).put("frames", t.frames).put("time", t.timeMs)
                        .put("count", t.faceCount).put("width", t.width).put("quality", t.quality.toDouble())
                        .put("person", t.personId ?: JSONObject.NULL).put("state", t.state).put("sim", t.similarity.toDouble())
                        .put("times", t.times).put("box", t.box))
                }
            })
            .put("rejections", JSONArray().apply {
                faces.rejections().filter { it.trackId in trackIds }.forEach {
                    put(JSONObject().put("t", it.trackId).put("p", it.personId).put("sim", it.similarity.toDouble()))
                }
            })
            .put("prints", JSONArray().apply {
                prints.forEach { put(JSONObject().put("f", fileIndex[it.fileId]).put("part", it.part).put("start", it.startMs)
                    .put("status", it.status).put("matched", it.matched)) }
            })
            .put("signatures", JSONArray().apply {
                signatures.forEach { put(JSONObject().put("id", it.id).put("kind", it.kind).put("name", it.name)
                    .put("sd", it.startDeltaMs).put("ed", it.endDeltaMs).put("at", it.createdAt)) }
            })
            .put("sounds", JSONArray().apply { sounds.forEach { put(JSONObject().put("s", it.signatureId)) } })
            .put("rules", JSONArray().apply {
                rules.forEach { put(JSONObject().put("kind", it.kind).put("origin", origin(it.originFileId)).put("originName", it.originName)
                    .put("sd", it.startDeltaMs).put("ed", it.endDeltaMs).put("at", it.createdAt)) }
            })
            .put("rejected", JSONArray().apply {
                rejected.forEach { put(JSONObject().put("kind", it.kind).put("origin", origin(it.originFileId)).put("originName", it.originName)
                    .put("start", it.startMs).put("end", it.endMs).put("at", it.createdAt)) }
            })
            .toString().toByteArray()

        out.writeInt(MAGIC)
        out.blob(index)
        tracks.forEach { out.blob(it.embeddings); out.blob(it.crop) }
        prints.forEach { out.blob(it.hashes) }
        sounds.forEach { out.blob(it.hashes) }
        rules.forEach { out.blob(it.hashes) }
        rejected.forEach { out.blob(it.hashes) }
    }

    /**
     * Read what [write] stored, after the files: [newIds] are the restored files by archive
     * position. Returns false when the archive has no such part (older backups).
     */
    suspend fun read(db: CriptaDatabase, newIds: Array<String?>, inp: DataInputStream): Boolean {
        val magic = try { inp.readInt() } catch (e: java.io.EOFException) { return false }
        if (magic != MAGIC) return false
        val o = JSONObject(String(inp.blob()))
        val faces = db.faceDao()
        val intro = db.introDao()
        fun file(i: Any?): String? = (i as? Number)?.toInt()?.let { newIds.getOrNull(it) }
        fun originOf(v: Any?): String = file(v) ?: (v as? String).orEmpty()

        val personIds = HashMap<Long, Long>()
        val covers = HashMap<Long, Long>()
        o.optJSONArray("persons")?.let { a ->
            for (i in 0 until a.length()) {
                val p = a.getJSONObject(i)
                val id = faces.addPerson(PersonEntity(name = p.optString("name"), ignored = p.optBoolean("ignored"), favorite = p.optBoolean("favorite"),
                    createdAt = p.optLong("createdAt", System.currentTimeMillis()), notes = p.optString("notes"), links = p.optString("links")))
                personIds[p.getLong("id")] = id
                p.optLongOrNull("cover")?.let { covers[id] = it }
            }
        }
        o.optJSONArray("personMedia")?.let { a ->
            for (i in 0 until a.length()) {
                val m = a.getJSONObject(i)
                val pid = personIds[m.getLong("p")] ?: continue
                val f = file(m.get("f")) ?: continue
                faces.addPersonMedia(PersonMediaEntity(pid, f))
            }
        }
        o.optJSONArray("scans")?.let { a ->
            for (i in 0 until a.length()) {
                val s = a.getJSONObject(i)
                val f = file(s.get("f")) ?: continue
                faces.putScan(FaceScanEntity(f, s.optString("status"), s.optInt("read"), s.optInt("failed"), s.optInt("faces"),
                    s.optString("rejects"), s.optInt("version"), s.optLong("at"), s.optBoolean("clustered"), s.optLong("ms"), s.optString("stats")))
            }
        }
        val trackIds = HashMap<Long, Long>()
        o.optJSONArray("tracks")?.let { a ->
            for (i in 0 until a.length()) {
                val t = a.getJSONObject(i)
                val emb = inp.blob(); val crop = inp.blob()
                val f = file(t.opt("f")) ?: continue
                val id = faces.addTrack(FaceTrackEntity(fileId = f, frames = t.optLong("frames"), timeMs = t.optLong("time"),
                    faceCount = t.optInt("count"), width = t.optInt("width"), quality = t.optDouble("quality").toFloat(),
                    embeddings = emb, crop = crop, personId = t.optLongOrNull("person")?.let { personIds[it] },
                    state = t.optString("state", "auto"), similarity = t.optDouble("sim", 0.0).toFloat(),
                    times = t.optString("times"), box = t.optString("box")))
                trackIds[t.getLong("id")] = id
            }
        }
        for ((pid, old) in covers) trackIds[old]?.let { faces.setCover(pid, it) }
        o.optJSONArray("rejections")?.let { a ->
            for (i in 0 until a.length()) {
                val r = a.getJSONObject(i)
                val t = trackIds[r.getLong("t")] ?: continue
                val p = personIds[r.getLong("p")] ?: continue
                faces.addRejection(FaceRejectionEntity(t, p, r.optDouble("sim", 0.0).toFloat()))
            }
        }
        o.optJSONArray("prints")?.let { a ->
            for (i in 0 until a.length()) {
                val p = a.getJSONObject(i)
                val hashes = inp.blob()
                val f = file(p.opt("f")) ?: continue
                intro.putPrint(AudioPrintEntity(f, p.optString("part"), p.optLong("start"), hashes, p.optString("status"), p.optBoolean("matched")))
            }
        }
        val signatureIds = HashMap<Long, Long>()
        o.optJSONArray("signatures")?.let { a ->
            for (i in 0 until a.length()) {
                val s = a.getJSONObject(i)
                signatureIds[s.getLong("id")] = intro.addSignature(SignatureEntity(kind = s.optString("kind"), name = s.optString("name"),
                    startDeltaMs = s.optLong("sd"), endDeltaMs = s.optLong("ed"), createdAt = s.optLong("at")))
            }
        }
        o.optJSONArray("sounds")?.let { a ->
            for (i in 0 until a.length()) {
                val hashes = inp.blob()
                val sid = signatureIds[a.getJSONObject(i).getLong("s")] ?: continue
                intro.addSignatureSound(SignatureSoundEntity(signatureId = sid, hashes = hashes))
            }
        }
        o.optJSONArray("rules")?.let { a ->
            for (i in 0 until a.length()) {
                val r = a.getJSONObject(i)
                intro.addRule(SegmentRuleEntity(kind = r.optString("kind"), originFileId = originOf(r.opt("origin")), originName = r.optString("originName"),
                    hashes = inp.blob(), startDeltaMs = r.optLong("sd"), endDeltaMs = r.optLong("ed"), createdAt = r.optLong("at")))
            }
        }
        o.optJSONArray("rejected")?.let { a ->
            for (i in 0 until a.length()) {
                val r = a.getJSONObject(i)
                intro.addRejected(RejectedPrintEntity(kind = r.optString("kind"), originFileId = originOf(r.opt("origin")),
                    originName = r.optString("originName"), startMs = r.optLong("start"), endMs = r.optLong("end"),
                    hashes = inp.blob(), createdAt = r.optLong("at")))
            }
        }
        return true
    }
}
