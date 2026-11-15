package ai.pleos.coach.core
class CoachEngine(preferences:List<Preference>,val source:Source,val sessionId:String,val startedAtMs:Long) {
 companion object { const val MAX_DATA_AGE_MS=2500L }
 private val settings=preferences.associateBy { it.segmentId }
 private val completed=mutableListOf<SegmentRecord>()
 val records:List<SegmentRecord> get()=completed.toList()
 private var lastTimestamp=Long.MIN_VALUE
 private var nextOrder=0
 private var invalidated=false
 private var finished=false
 private data class Active(val segment:Segment,val start:Long,val preference:Preference,val samples:MutableList<Sample> = mutableListOf(),var decision:Decision?=null,var terminal:Boolean=false)
 private var active:Active?=null
 private fun fresh(now:Long,observed:Long)=observed<=now && now-observed<=MAX_DATA_AGE_MS
 private fun close(at:Long,fallback:DecisionReason=DecisionReason.DATA_UNAVAILABLE):SegmentRecord? {
  val a=active ?: return null
  val record=SegmentRecord("$sessionId-${a.segment.id}",sessionId,a.segment.id,a.segment.approachId,source,a.start,at.coerceAtLeast(a.start),a.samples.toList(),a.decision ?: Decision(a.segment.id,at,fallback),a.preference)
  completed.add(record); active=null; nextOrder=a.segment.order+1
  return record
 }
 fun ingest(snapshot:Snapshot):EngineUpdate {
  if(finished || invalidated || snapshot.source!=source || snapshot.timestampMs<startedAtMs || snapshot.timestampMs<=lastTimestamp) return EngineUpdate(activeSegment=active?.segment)
  lastTimestamp=snapshot.timestampMs
  val done=mutableListOf<SegmentRecord>()
  if(!snapshot.routeActive || snapshot.routeId!=SampleRoute.ID) {
   val segmentId=active?.segment?.id
   val stopped=segmentId?.let { Decision(it,snapshot.timestampMs,DecisionReason.ROUTE_CHANGED) }
   active?.let { if(!it.terminal) it.decision=stopped }
   close(snapshot.timestampMs,DecisionReason.ROUTE_CHANGED)?.let(done::add)
   invalidated=true
   return EngineUpdate(decision=stopped,completedRecords=done)
  }
  val candidate=SampleRoute.segment(snapshot.segmentId)
  if(active!=null && candidate?.id!=active?.segment?.id) close(snapshot.timestampMs)?.let(done::add)
  if(candidate==null) return EngineUpdate(completedRecords=done)
  if(candidate.order!=nextOrder || !snapshot.approachVerified || snapshot.approachId!=candidate.approachId) {
   return EngineUpdate(activeSegment=active?.segment,decision=Decision(candidate.id,snapshot.timestampMs,DecisionReason.APPROACH_UNVERIFIED),completedRecords=done)
  }
  val a=active ?: Active(candidate,snapshot.timestampMs,settings[candidate.id] ?: Preference(candidate.id)).also { active=it }
  val locationOk=snapshot.locationReliable && fresh(snapshot.timestampMs,snapshot.locationTimestampMs)
  val signal=if(fresh(snapshot.timestampMs,snapshot.signalTimestampMs)) snapshot.signal else TurnSignal.UNKNOWN
  val speed=if(fresh(snapshot.timestampMs,snapshot.speedTimestampMs)) snapshot.speedKmh?.takeIf { it.isFinite() && it>=0.0 } else null
  val brake=if(fresh(snapshot.timestampMs,snapshot.brakeTimestampMs)) snapshot.brake else null
  val sample=Sample(snapshot.timestampMs,snapshot.timestampMs-a.start,source,speed,brake,signal,locationOk,snapshot.speedTimestampMs,snapshot.brakeTimestampMs,snapshot.signalTimestampMs,snapshot.locationTimestampMs)
  a.samples.add(sample)
  var emitted:Decision?=null
  if(!a.terminal) {
   val p=a.preference
   val reason=when {
    !locationOk->DecisionReason.DATA_UNAVAILABLE
    !p.selected->DecisionReason.NOT_SELECTED
    p.assistance==Assistance.REVIEW_ONLY->DecisionReason.REVIEW_ONLY
    p.goal==Goal.SPEED_REVIEW->DecisionReason.SPEED_REVIEW_ONLY
    p.goal==Goal.SIGNAL_CHECK && signal==TurnSignal.UNKNOWN->DecisionReason.DATA_UNAVAILABLE
    p.goal==Goal.SIGNAL_CHECK && signal==candidate.direction->DecisionReason.SIGNAL_ALREADY_ON
    else->DecisionReason.PROMPTED
   }
   val prompt=if(reason==DecisionReason.PROMPTED) when(p.goal) {
    Goal.SIGNAL_CHECK->if(p.assistance==Assistance.BRIEF) "방향지시등을 확인하세요." else "연습 구간이에요. 방향지시등을 확인하세요."
    Goal.ROUTE_PREVIEW->if(p.assistance==Assistance.BRIEF) "연습 구간이에요." else "지난번 선택한 연습 구간에 접근하고 있어요."
    Goal.SPEED_REVIEW->null
   } else null
   val decision=Decision(candidate.id,snapshot.timestampMs,reason,prompt,signal)
   if(a.decision?.reason!=reason) emitted=decision
   a.decision=decision
   a.terminal=reason!=DecisionReason.DATA_UNAVAILABLE
  }
  return EngineUpdate(candidate,emitted,sample,done)
 }
 fun finish(endedAtMs:Long):List<SegmentRecord> {
  if(!finished) { close(maxOf(endedAtMs,lastTimestamp,startedAtMs)); finished=true }
  return records
 }
}