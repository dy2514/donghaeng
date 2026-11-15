package ai.pleos.coach.core
enum class ReplayScenario(val label:String) {
 FIRST("첫 주행 · 기록만"), PRACTICE("반복 연습"), SIGNAL_READY("방향지시등 미리 켜짐"), MISSING_DATA("데이터 누락"), ROUTE_CANCELLED("경로 취소"), WRONG_APPROACH("다른 접근 방향")
}
object ReplayFactory {
 fun frames(scenario:ReplayScenario,startMs:Long=1_000_000L): List<Snapshot> = (0..62).map { index ->
  val timestamp=startMs+index*500L
  val segment=when(index) { in 5..27->SampleRoute.segments[0]; in 34..57->SampleRoute.segments[1]; else->null }
  val position=when(index) { in 5..27->index-5; in 34..57->index-34; else->0 }
  val selectedSpeed=when { segment==null->28.0; position<8->40.0-position*2.1; position<14->23.2-(position-8)*1.0; else->17.2+(position-14)*1.4 }
  val missing=scenario==ReplayScenario.MISSING_DATA && segment?.order==0
  val signal=when {
   missing->TurnSignal.UNKNOWN
   segment==null->TurnSignal.OFF
   scenario==ReplayScenario.SIGNAL_READY->segment.direction
   scenario==ReplayScenario.PRACTICE && segment.order==1->segment.direction
   position>=8 && position<=19->segment.direction
   else->TurnSignal.OFF
  }
  Snapshot(timestampMs=timestamp,source=Source.REPLAY,routeActive=!(scenario==ReplayScenario.ROUTE_CANCELLED && index>=16),segmentId=segment?.id,approachId=if(scenario==ReplayScenario.WRONG_APPROACH && segment!=null) "opposite-approach" else segment?.approachId,approachVerified=scenario!=ReplayScenario.WRONG_APPROACH,locationReliable=!(missing && position<4),speedKmh=if(missing && position in 6..12) null else selectedSpeed,brake=if(missing && position in 6..12) null else segment!=null && position in 3..12,signal=signal)
 }
}