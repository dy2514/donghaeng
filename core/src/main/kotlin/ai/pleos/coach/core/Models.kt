package ai.pleos.coach.core
enum class Source(val label:String) { REPLAY("시나리오 재생"), LIVE("차량 실시간") }
enum class TurnSignal(val label:String) { LEFT("좌측"), RIGHT("우측"), OFF("꺼짐"), UNKNOWN("확인 불가") }
enum class Goal(val label:String) { ROUTE_PREVIEW("회전 구간 예습"), SIGNAL_CHECK("방향지시등 확인"), SPEED_REVIEW("속도·브레이크 복습") }
enum class Assistance(val label:String) { TOGETHER("함께 준비"), BRIEF("짧게 확인"), REVIEW_ONLY("주행 후 복습") }
enum class Difficulty(val label:String) { EASY("편했어요"), MEDIUM("조금 어려웠어요"), HARD("많이 어려웠어요") }
enum class Reason(val label:String,val suggestedGoal:Goal?) {
 LATE_TURN("회전 지점을 늦게 알아봤어요",Goal.ROUTE_PREVIEW), FORGOT_SIGNAL("방향지시등 확인을 잊었어요",Goal.SIGNAL_CHECK), SPEED_CONTROL("속도 조절이 어려웠어요",Goal.SPEED_REVIEW), TRAFFIC("교통 상황의 영향이 있었어요",null), UNSURE("잘 모르겠어요",null)
}
data class Segment(val id:String,val name:String,val direction:TurnSignal,val approachId:String,val order:Int)
object SampleRoute {
 const val ID="campus-practice"
 const val NAME="처음 혼자 가는 학교 길"
 val segments=listOf(Segment("school-right","학교 앞 우회전",TurnSignal.RIGHT,"school-eastbound",0),Segment("gate-left","정문 앞 좌회전",TurnSignal.LEFT,"gate-northbound",1))
 fun segment(id:String?)=segments.firstOrNull { it.id==id }
}
data class Preference(val segmentId:String,val selected:Boolean=false,val reason:Reason=Reason.UNSURE,val goal:Goal=Goal.ROUTE_PREVIEW,val assistance:Assistance=Assistance.TOGETHER,val difficulty:Difficulty=Difficulty.MEDIUM,val note:String="") {
 fun withReason(value:Reason)=copy(reason=value,goal=value.suggestedGoal ?: goal)
 val previewText:String get()=when(goal) {
  Goal.ROUTE_PREVIEW->"출발 전에 진행 방향을 확인해요. 선택한 구간에 접근하면 짧게 알려드릴게요."
  Goal.SIGNAL_CHECK->"선택한 구간에서 방향지시등을 확인해요. 해당 방향 신호가 확인되면 추가 안내를 생략해요."
  Goal.SPEED_REVIEW->"속도와 브레이크 페달 기록을 남기고, 주차 후 느낌과 함께 돌아봐요."
 }
}
data class Snapshot(val timestampMs:Long,val source:Source,val routeId:String=SampleRoute.ID,val routeActive:Boolean=true,val segmentId:String?=null,val approachId:String?=null,val approachVerified:Boolean=true,val locationReliable:Boolean=true,val locationTimestampMs:Long=timestampMs,val speedKmh:Double?=null,val speedTimestampMs:Long=timestampMs,val brake:Boolean?=null,val brakeTimestampMs:Long=timestampMs,val signal:TurnSignal=TurnSignal.UNKNOWN,val signalTimestampMs:Long=timestampMs)
enum class DecisionReason(val label:String) {
 PROMPTED("교육 안내 제공"),NOT_SELECTED("기록만 하는 구간"),SIGNAL_ALREADY_ON("해당 방향 신호 확인으로 생략"),REVIEW_ONLY("주행 후 복습으로 설정"),SPEED_REVIEW_ONLY("속도·브레이크는 주행 후 복습"),DATA_UNAVAILABLE("데이터 확인 불가로 안내 보류"),APPROACH_UNVERIFIED("접근 방향·순서 확인 불가"),ROUTE_CHANGED("경로 변경·취소로 안내 중단"),NO_VALID_APPROACH("유효한 구간 접근 기록 없음")
}
data class Decision(val segmentId:String,val timestampMs:Long,val reason:DecisionReason,val prompt:String?=null,val observedSignal:TurnSignal=TurnSignal.UNKNOWN) { val shouldSpeak:Boolean get()=reason==DecisionReason.PROMPTED && prompt!=null }
data class Sample(val timestampMs:Long,val elapsedMs:Long,val source:Source,val speedKmh:Double?,val brake:Boolean?,val signal:TurnSignal,val locationReliable:Boolean,val speedTimestampMs:Long=timestampMs,val brakeTimestampMs:Long=timestampMs,val signalTimestampMs:Long=timestampMs,val locationTimestampMs:Long=timestampMs)
data class SegmentRecord(val id:String,val sessionId:String,val segmentId:String,val approachId:String,val source:Source,val startedAtMs:Long,val endedAtMs:Long,val samples:List<Sample>,val decision:Decision,val preference:Preference,val difficulty:Difficulty?=null,val reason:Reason?=null,val note:String="") {
 val durationMs:Long get()=(endedAtMs-startedAtMs).coerceAtLeast(0)
 val missingSamples:Int get()=samples.count { it.speedKmh==null || it.brake==null || it.signal==TurnSignal.UNKNOWN || !it.locationReliable }
}
data class EngineUpdate(val activeSegment:Segment?=null,val decision:Decision?=null,val sample:Sample?=null,val completedRecords:List<SegmentRecord> = emptyList())
object RecordHistory {
 fun latestTwo(records:List<SegmentRecord>,segmentId:String,approachId:String,source:Source):List<SegmentRecord> = records.filter { it.segmentId==segmentId && it.approachId==approachId && it.source==source }.distinctBy { it.id }.sortedByDescending { it.startedAtMs }.take(2)
 fun retainLatestTwo(records:List<SegmentRecord>):List<SegmentRecord> = records.distinctBy { it.id }.groupBy { Triple(it.segmentId,it.approachId,it.source) }.values.flatMap { group->group.sortedByDescending { it.startedAtMs }.take(2) }.sortedByDescending { it.startedAtMs }
}