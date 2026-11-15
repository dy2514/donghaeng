package ai.pleos.coach.data

import ai.pleos.coach.core.*
import org.json.JSONArray
import org.json.JSONObject

data class SavedState(
    val preferences: List<Preference> = SampleRoute.segments.map { Preference(it.id) },
    val records: List<SegmentRecord> = emptyList(),
    val audioOutcomes: Map<String, String> = emptyMap()
)

object StateCodec {
    private fun JSONObject.nullableDouble(key: String): Double? =
        if (isNull(key) || !has(key)) null else getDouble(key).takeIf { it.isFinite() }
    private fun JSONObject.nullableBoolean(key: String): Boolean? =
        if (isNull(key) || !has(key)) null else getBoolean(key)
    private inline fun <reified T : Enum<T>> JSONObject.enum(key: String): T = enumValueOf(getString(key))
    private fun array(items: Iterable<JSONObject>) = JSONArray().apply { items.forEach { put(it) } }
    private fun preference(p: Preference) = JSONObject()
        .put("segmentId", p.segmentId).put("selected", p.selected).put("reason", p.reason.name)
        .put("goal", p.goal.name).put("assistance", p.assistance.name).put("difficulty", p.difficulty.name).put("note", p.note)
    private fun preference(o: JSONObject) = Preference(
        segmentId=o.getString("segmentId"), selected=o.getBoolean("selected"), reason=o.enum("reason"),
        goal=o.enum("goal"), assistance=o.enum("assistance"), difficulty=o.enum("difficulty"), note=o.optString("note", "")
    )
    private fun sample(s: Sample) = JSONObject()
        .put("timestampMs",s.timestampMs).put("elapsedMs",s.elapsedMs).put("source",s.source.name)
        .put("speedKmh",s.speedKmh ?: JSONObject.NULL).put("brake",s.brake ?: JSONObject.NULL)
        .put("signal",s.signal.name).put("locationReliable",s.locationReliable)
        .put("speedTimestampMs",s.speedTimestampMs).put("brakeTimestampMs",s.brakeTimestampMs)
        .put("signalTimestampMs",s.signalTimestampMs).put("locationTimestampMs",s.locationTimestampMs)
    private fun sample(o: JSONObject) = Sample(
        timestampMs=o.getLong("timestampMs"),elapsedMs=o.getLong("elapsedMs"),source=o.enum("source"),
        speedKmh=o.nullableDouble("speedKmh"),brake=o.nullableBoolean("brake"),signal=o.enum("signal"),
        locationReliable=o.getBoolean("locationReliable"),
        speedTimestampMs=o.optLong("speedTimestampMs",o.getLong("timestampMs")),brakeTimestampMs=o.optLong("brakeTimestampMs",o.getLong("timestampMs")),
        signalTimestampMs=o.optLong("signalTimestampMs",o.getLong("timestampMs")),locationTimestampMs=o.optLong("locationTimestampMs",o.getLong("timestampMs"))
    )
    private fun decision(d: Decision) = JSONObject()
        .put("segmentId",d.segmentId).put("timestampMs",d.timestampMs).put("reason",d.reason.name)
        .put("prompt",d.prompt ?: JSONObject.NULL).put("observedSignal",d.observedSignal.name)
    private fun decision(o: JSONObject) = Decision(
        segmentId=o.getString("segmentId"),timestampMs=o.getLong("timestampMs"),reason=o.enum("reason"),
        prompt=if(o.isNull("prompt")) null else o.getString("prompt"),observedSignal=o.enum("observedSignal")
    )
    private fun record(r: SegmentRecord) = JSONObject()
        .put("id",r.id).put("sessionId",r.sessionId).put("segmentId",r.segmentId).put("approachId",r.approachId)
        .put("source",r.source.name).put("startedAtMs",r.startedAtMs).put("endedAtMs",r.endedAtMs)
        .put("samples",array(r.samples.map(::sample))).put("decision",decision(r.decision))
        .put("preference",preference(r.preference)).put("difficulty",r.difficulty?.name ?: JSONObject.NULL)
        .put("reason",r.reason?.name ?: JSONObject.NULL).put("note",r.note)
    private fun record(o: JSONObject): SegmentRecord {
        val samples = o.getJSONArray("samples")
        return SegmentRecord(
            id=o.getString("id"),sessionId=o.getString("sessionId"),segmentId=o.getString("segmentId"),
            approachId=o.getString("approachId"),source=o.enum("source"),startedAtMs=o.getLong("startedAtMs"),
            endedAtMs=o.getLong("endedAtMs"),samples=List(samples.length()) { sample(samples.getJSONObject(it)) },
            decision=decision(o.getJSONObject("decision")),preference=preference(o.getJSONObject("preference")),
            difficulty=if(o.isNull("difficulty")) null else o.enum<Difficulty>("difficulty"),
            reason=if(o.isNull("reason")) null else o.enum<Reason>("reason"),note=o.optString("note","")
        ).also { r ->
            require(r.endedAtMs >= r.startedAtMs)
            require(r.samples.all { it.source == r.source && it.elapsedMs >= 0 })
        }
    }

    fun retainLatest(records: List<SegmentRecord>): List<SegmentRecord> = records
        .distinctBy { it.id }
        .groupBy { Triple(it.source,it.segmentId,it.approachId) }
        .values.flatMap { it.sortedByDescending(SegmentRecord::startedAtMs).take(2) }
        .sortedByDescending(SegmentRecord::startedAtMs)

    fun encode(state: SavedState): JSONObject {
        val records = retainLatest(state.records)
        return JSONObject().put("version",1)
            .put("preferences",array(state.preferences.map(::preference)))
            .put("records",array(records.map(::record)))
            .put("audioOutcomes",JSONObject(state.audioOutcomes.filterKeys { id -> records.any { it.id == id } }))
    }
    fun decode(json: JSONObject): SavedState {
        require(json.getInt("version") == 1)
        val prefs = json.getJSONArray("preferences")
        val records = json.getJSONArray("records")
        val audio = json.optJSONObject("audioOutcomes") ?: JSONObject()
        val knownSegments = SampleRoute.segments.map { it.id }.toSet()
        return SavedState(
            List(prefs.length()) { preference(prefs.getJSONObject(it)) }.filter { it.segmentId in knownSegments }
                .let { loaded -> SampleRoute.segments.map { segment -> loaded.find { it.segmentId == segment.id } ?: Preference(segment.id) } },
            retainLatest(List(records.length()) { record(records.getJSONObject(it)) }.filter { it.segmentId in knownSegments }),
            audio.keys().asSequence().associateWith { audio.getString(it) }
        )
    }
}
