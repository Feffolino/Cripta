package com.cripta.app.data

import com.cripta.app.R
import com.cripta.app.str

import com.cripta.app.data.db.AudioPrintEntity
import com.cripta.app.data.db.CriptaDatabase
import com.cripta.app.data.db.DupApartEntity
import com.cripta.app.data.db.DupPrintEntity
import com.cripta.app.data.db.FaceRejectionEntity
import com.cripta.app.data.db.FaceScanEntity
import com.cripta.app.data.db.FaceTrackEntity
import com.cripta.app.data.db.IgnoredFaceEntity
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
 * the face analysis of each media, the intro/outro analysis (fingerprints, signatures, rules,
 * rejected sounds) and the duplicate search's fingerprints (a restored vault needs no new deep search). Written as [MAGIC], a JSON index, then the binary parts (embeddings, face
 * pictures, fingerprints) in the index's order, so nothing large is held in a string.
 *
 * Media are referred to by their position in the archive (ids change on restore). An app that
 * predates this part stops reading after the files and ignores it.
 */
internal object BackupExtras {
    const val MAGIC = 0x43524558 // "CREX"

    /** What [read] found: people restored, and whether any analysis came with them. */
    class Contents {
        var people = 0; var analysis = false
        /** Old person id -> restored id, for what refers to people (saved filters). */
        val personIds = HashMap<Long, Long>()
    }

    private fun JSONObject.optLongOrNull(k: String): Long? = if (!has(k) || isNull(k)) null else optLong(k)
    private fun JSONObject.optStrOrNull(k: String): String? = if (!has(k) || isNull(k)) null else optString(k)
    /** Largest record [read] accepts, so the largest [write] may produce. */
    private const val MAX_RECORD = 64_000_000
    private fun DataOutputStream.blob(b: ByteArray) {
        // Over what a restore reads, the archive would be made, said done, and never restored.
        if (b.size > MAX_RECORD) throw java.io.IOException(str(R.string.err_backup_too_large))
        writeInt(b.size); write(b)
    }
    private fun DataInputStream.blob(): ByteArray {
        val n = readInt()
        require(n in 0..MAX_RECORD) { str(R.string.file_corrupt) }
        return ByteArray(n).also { readFully(it) }
    }

    /**
     * [people]: persons, faces and their choices; [analysis]: intro/outro and similar-media
     * fingerprints. A group left out is written as empty lists (same layout, older apps read it).
     */
    suspend fun write(db: CriptaDatabase, fileIndex: Map<String, Int>, out: DataOutputStream, people: Boolean = true, analysis: Boolean = true) {
        val faces = db.faceDao()
        val intro = db.introDao()
        val persons = if (people) faces.persons() else emptyList()
        val tracks = ArrayList<FaceTrackEntity>()
        if (people) for (id in fileIndex.keys) tracks += faces.tracksOfFile(id)
        val trackIds = tracks.map { it.id }.toSet()
        val prints = if (analysis) intro.backupPrints().filter { it.fileId in fileIndex } else emptyList()
        val signatures = if (analysis) intro.backupSignatures() else emptyList()
        val sounds = if (analysis) intro.backupSignatureSounds() else emptyList()
        val rules = if (analysis) intro.backupRules() else emptyList()
        val rejected = if (analysis) intro.backupRejected() else emptyList()
        val dupPrints = if (analysis) db.dupPrintDao().all().filter { it.fileId in fileIndex } else emptyList()
        val dupApart = if (analysis) db.dupPrintDao().apartPairs().filter { it.a in fileIndex && it.b in fileIndex } else emptyList()
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
                if (people) faces.allPersonMedia().forEach { m -> fileIndex[m.fileId]?.let { put(JSONObject().put("p", m.personId).put("f", it)) } }
            })
            .put("scans", JSONArray().apply {
                if (people) faces.allScans().forEach { s ->
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
                        .put("times", t.times).put("box", t.box)
                        .put("sight", java.util.Base64.getEncoder().encodeToString(t.sightings)))
                }
            })
            .put("ignored", JSONArray().apply {
                if (people) faces.allIgnored().forEach { g ->
                    val f = g.fileId?.let { fileIndex[it] ?: return@forEach }
                    put(JSONObject().put("f", f ?: JSONObject.NULL).put("at", g.createdAt)
                        .put("emb", java.util.Base64.getEncoder().encodeToString(g.embeddings)))
                }
            })
            .put("rejections", JSONArray().apply {
                if (people) faces.rejections().filter { it.trackId in trackIds }.forEach {
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
            // A few bytes each: inline, so the binary parts (and older apps reading them) are unchanged.
            .put("dupPrints", JSONArray().apply {
                dupPrints.forEach { put(JSONObject().put("f", fileIndex[it.fileId]).put("size", it.sizeBytes).put("v", it.version)
                    .put("h", java.util.Base64.getEncoder().encodeToString(it.hashes))) }
            })
            .put("dupApart", JSONArray().apply {
                dupApart.forEach { put(JSONObject().put("a", fileIndex[it.a]).put("b", fileIndex[it.b])) }
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
     * position; [known] the files of the vault the restore recognised as already there (same
     * content in the same folder), by the same position. A recognised file keeps its own analysis
     * (nothing of it is replaced): it only gains the archive's people. Returns false when the
     * archive has no such part (older backups).
     */
    suspend fun read(db: CriptaDatabase, newIds: Array<String?>, inp: DataInputStream, contents: Contents? = null,
                     known: Array<String?> = arrayOfNulls(0)): Boolean {
        val magic = try { inp.readInt() } catch (e: java.io.EOFException) { return false }
        if (magic != MAGIC) return false
        val o = JSONObject(String(inp.blob()))
        val faces = db.faceDao()
        val intro = db.introDao()
        fun file(i: Any?): String? = (i as? Number)?.toInt()?.let { newIds.getOrNull(it) }
        fun originOf(v: Any?): String = file(v) ?: (v as? String).orEmpty()
        /** A restored file, or one already in the vault: for links that only add. */
        fun anyFile(i: Any?): String? = (i as? Number)?.toInt()?.let { newIds.getOrNull(it) ?: known.getOrNull(it) }

        contents?.analysis = listOf("prints", "signatures", "rules", "rejected", "dupPrints", "dupApart")
            .any { (o.optJSONArray(it)?.length() ?: 0) > 0 }
        val personIds = HashMap<Long, Long>()
        val covers = HashMap<Long, Long>()
        // Named people already in the vault: the archive's person of that name is that one (only
        // gaining media), as tags and folders are matched by name. Restored twice, an archive no
        // longer made a second profile of everyone.
        val byName = faces.persons().filter { it.name.isNotBlank() }.associateBy { it.name }
        /** Profiles made by this restore, and those that got something of their own (a face, a media). */
        val created = HashSet<Long>(); val filled = HashSet<Long>()
        o.optJSONArray("persons")?.let { a ->
            for (i in 0 until a.length()) {
                val p = a.getJSONObject(i)
                val name = p.optString("name")
                val same = byName[name]
                if (same != null) { personIds[p.getLong("id")] = same.id; continue }
                val id = faces.addPerson(PersonEntity(name = p.optString("name"), ignored = p.optBoolean("ignored"), favorite = p.optBoolean("favorite"),
                    createdAt = p.optLong("createdAt", System.currentTimeMillis()), notes = p.optString("notes"), links = p.optString("links")))
                personIds[p.getLong("id")] = id
                created += id
                p.optLongOrNull("cover")?.let { covers[id] = it }
            }
        }
        o.optJSONArray("personMedia")?.let { a ->
            for (i in 0 until a.length()) {
                val m = a.getJSONObject(i)
                val pid = personIds[m.getLong("p")] ?: continue
                val f = anyFile(m.get("f")) ?: continue
                faces.addPersonMedia(PersonMediaEntity(pid, f))
                if (file(m.get("f")) != null) filled += pid
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
                    times = t.optString("times"), box = t.optString("box"),
                    sightings = runCatching { java.util.Base64.getDecoder().decode(t.optString("sight")) }.getOrDefault(ByteArray(0))))
                trackIds[t.getLong("id")] = id
                t.optLongOrNull("person")?.let { personIds[it] }?.let { filled += it }
            }
        }
        for ((pid, old) in covers) trackIds[old]?.let { faces.setCover(pid, it) }
        // A new unnamed profile with nothing new (its media were all already here, with the
        // profile they had): not made again. Its notes or links, or a star, keep it.
        val now = faces.persons().associateBy { it.id }
        for (id in created.toList()) if (id !in filled) {
            val p = now[id] ?: continue
            if (p.name.isBlank() && p.notes.isBlank() && p.links.isBlank() && !p.favorite && !p.ignored) {
                faces.deletePersonMedia(id); faces.deletePerson(id)
                personIds.entries.removeAll { it.value == id }
                created -= id
            }
        }
        contents?.personIds?.putAll(personIds)
        contents?.people = created.size
        o.optJSONArray("ignored")?.let { a ->
            for (i in 0 until a.length()) {
                val g = a.getJSONObject(i)
                val f = if (g.isNull("f")) null else file(g.get("f")) ?: continue
                val emb = runCatching { java.util.Base64.getDecoder().decode(g.optString("emb")) }.getOrNull() ?: continue
                faces.addIgnored(IgnoredFaceEntity(fileId = f, embeddings = emb, createdAt = g.optLong("at")))
            }
        }
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
        // Checked like any stored fingerprint when used (size and version), so a stale one is made again.
        o.optJSONArray("dupPrints")?.let { a ->
            val dup = db.dupPrintDao()
            for (i in 0 until a.length()) {
                val d = a.getJSONObject(i)
                val f = file(d.opt("f")) ?: continue
                val hashes = runCatching { java.util.Base64.getDecoder().decode(d.optString("h")) }.getOrNull() ?: continue
                dup.put(DupPrintEntity(f, d.optLong("size"), d.optInt("v"), hashes))
            }
        }
        o.optJSONArray("dupApart")?.let { a ->
            val pairs = (0 until a.length()).mapNotNull { i ->
                val d = a.getJSONObject(i)
                val x = file(d.opt("a")) ?: return@mapNotNull null
                val y = file(d.opt("b")) ?: return@mapNotNull null
                DupApartEntity(minOf(x, y), maxOf(x, y))
            }
            if (pairs.isNotEmpty()) db.dupPrintDao().putApart(pairs)
        }
        return true
    }
}
